package com.ruoyi.lab.restriction;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.domain.LabAttachment;
import com.ruoyi.lab.domain.LabReservation;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.*;
import com.ruoyi.lab.security.*;
import com.ruoyi.lab.service.*;
import com.ruoyi.lab.vo.StatusHistoryVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RestrictionService
{
    private final LabRestrictionMapper mapper;
    private final RestrictionGuard guard;
    private final LabObjectPermissionService permissions;
    private final LabDataScopeService scopes;
    private final LabUserDirectory users;
    private final LabAttachmentMapper attachments;
    private final LabStatusHistoryService history;
    private final LabStatusHistoryMapper histories;
    private final Clock clock;

    public RestrictionService(LabRestrictionMapper mapper, RestrictionGuard guard,
            LabObjectPermissionService permissions, LabDataScopeService scopes, LabUserDirectory users,
            LabAttachmentMapper attachments, LabStatusHistoryService history,
            LabStatusHistoryMapper histories, Clock clock)
    {
        this.mapper=mapper; this.guard=guard; this.permissions=permissions; this.scopes=scopes;
        this.users=users; this.attachments=attachments; this.history=history; this.histories=histories; this.clock=clock;
    }

    public List<RestrictionRecord> list(boolean mine, Long laboratoryId, Long userId, String status)
    {
        String filter=status==null || status.isBlank()?null:status;
        if (filter!=null && !Set.of("ACTIVE","EXPIRED","REVOKED").contains(filter))
            throw RestrictionPolicy.invalid("限制状态无效");
        LabDataScope scope=null;
        Long owner=permissions.currentUserId();
        if (!mine)
        {
            requirePermission("lab:restriction:list");
            users.assertActiveRole(owner,"lab_manager");
            scope=scopes.resolveCurrentScope();
            if (scope.empty()) return List.of();
            owner=userId;
        }
        LabDataScope authorized=scope;
        Long selected=owner;
        return LabPage.query(() -> mapper.list(selected,laboratoryId,authorized,filter,now()));
    }

    public record Detail(RestrictionRecord restriction, RestrictionAppeal appeal, List<StatusHistoryVo> history) { }

    public Detail detail(Long id)
    {
        RestrictionRecord row=readable(id);
        RestrictionAppeal appeal=mapper.appeal(id);
        if (appeal!=null) appeal.attachmentIds=mapper.evidenceIds(appeal.id);
        return new Detail(row,appeal,histories.selectByObject("RESTRICTION",id));
    }

    public RestrictionRecord readable(Long id)
    {
        RestrictionRecord row=required(id);
        if (!Objects.equals(row.userId,permissions.currentUserId()))
        {
            if (!SecurityUtils.hasPermi("lab:restriction:list") && !SecurityUtils.hasPermi("lab:restriction:review"))
                throw denied();
            manager(row.laboratoryId);
        }
        return row;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public RestrictionRecord manual(RestrictionCommands.Manual command)
    {
        Long actor=permissions.currentUserId();
        if (Objects.equals(command.userId(),actor)) throw denied();
        requirePermission("lab:restriction:manual");
        String reason=RestrictionPolicy.reason(command.reason());
        int days=RestrictionPolicy.days(command.days(),365);
        guard.lockUsers(List.of(command.userId()));
        manager(command.laboratoryId());
        users.assertActiveRole(command.userId(),"lab_student");
        RestrictionRecord row=new RestrictionRecord();
        row.laboratoryId=command.laboratoryId();row.userId=command.userId();row.source="MANUAL";
        row.reason=reason;row.startsAt=now();row.endsAt=row.startsAt.plusDays(days);
        row.createdAt=row.startsAt;row.createdBy=actor;
        mapper.insert(row);
        history.append("RESTRICTION",row.id,null,"ACTIVE",actor,reason);
        return required(row.id);
    }

    /** Called only from a newly committed-in-this-transaction NO_SHOW transition, never from a scan of old facts. */
    public void recordNoShow(LabReservation reservation, Long laboratoryId, LocalDateTime factAt, Long operator)
    {
        LocalDateTime enabledAt=guard.gate();
        if (factAt.isBefore(enabledAt) || mapper.noShow(reservation.getId())!=null) return;
        RestrictionRule rule=mapper.activeRule(laboratoryId);
        if (rule==null)
        {
            // Laboratories created after migration also receive an immutable published version.
            rule=new RestrictionRule();rule.laboratoryId=laboratoryId;rule.days=7;
            rule.reason="首次启用：新爽约事实默认限制7天";rule.createdBy=operator;rule.createdAt=factAt;
            mapper.publish(rule);
        }
        int days=RestrictionPolicy.days(rule.days,90);
        RestrictionRecord row=new RestrictionRecord();
        row.laboratoryId=laboratoryId;row.userId=reservation.getApplicantId();row.source="NO_SHOW";
        row.sourceReservationId=reservation.getId();row.reason="预约爽约自动限制";
        row.startsAt=factAt;row.endsAt=factAt.plusDays(days);row.createdAt=factAt;row.createdBy=operator;
        row.ruleVersionId=rule.id;
        row.ruleSnapshot="{\"days\":"+days+",\"ruleVersionId\":"+row.ruleVersionId+",\"enabledAt\":\""+enabledAt+"\"}";
        mapper.insert(row);
        history.append("RESTRICTION",row.id,null,"ACTIVE",operator,row.reason);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public RestrictionRecord revoke(Long id, String reason)
    {
        RestrictionRecord row=lock(id);
        notOwner(row);
        requirePermission("lab:restriction:revoke");
        manager(row.laboratoryId);
        String normalized=RestrictionPolicy.reason(reason);
        if (mapper.revoke(id,permissions.currentUserId(),normalized,now())!=1) throw duplicate();
        history.append("RESTRICTION",id,"ACTIVE","REVOKED",permissions.currentUserId(),normalized);
        return required(id);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public RestrictionAppeal appeal(Long id, RestrictionCommands.Appeal command)
    {
        RestrictionRecord row=lock(id);
        if (!Objects.equals(row.userId,permissions.currentUserId())) throw denied();
        requirePermission("lab:restriction:appeal");
        if (mapper.appeal(id)!=null) throw duplicate();
        String reason=RestrictionPolicy.reason(command.reason());
        List<Long> ids=command.attachmentIds()==null?List.of():command.attachmentIds();
        if (ids.size()>10 || new HashSet<>(ids).size()!=ids.size()) throw RestrictionPolicy.invalid("证据附件最多10个，且不能重复");
        for (Long attachmentId : ids)
        {
            if (attachmentId==null || attachmentId<=0) throw RestrictionPolicy.invalid("证据附件编号无效");
            LabAttachment attachment=attachments.selectByIdForUpdate(attachmentId);
            if (attachment==null || !"0".equals(attachment.getDelFlag())
                    || !"RESTRICTION".equals(attachment.getBusinessType()) || !id.equals(attachment.getBusinessId())) throw denied();
        }
        RestrictionAppeal appeal=new RestrictionAppeal();
        appeal.restrictionId=id;appeal.reason=reason;appeal.status="PENDING";appeal.createdAt=now();
        mapper.insertAppeal(appeal);
        ids.forEach(attachmentId -> mapper.evidence(appeal.id,attachmentId));
        appeal.attachmentIds=List.copyOf(ids);
        history.append("RESTRICTION",id,null,"APPEAL_PENDING",row.userId,"提交限制申诉");
        return appeal;
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Detail review(Long id, RestrictionCommands.Decision command)
    {
        RestrictionRecord row=lock(id);
        notOwner(row);
        requirePermission("lab:restriction:review");
        manager(row.laboratoryId);
        String reason=RestrictionPolicy.reason(command.reason());
        if (command.approved()==null) throw RestrictionPolicy.invalid("请选择申诉结论");
        String result=command.approved()?"APPROVED":"REJECTED";
        if (mapper.review(id,result,permissions.currentUserId(),reason,now())!=1) throw duplicate();
        if (command.approved()) mapper.revoke(id,permissions.currentUserId(),reason,now());
        history.append("RESTRICTION",id,"APPEAL_PENDING","APPEAL_"+result,permissions.currentUserId(),reason);
        return detail(id);
    }

    public List<RestrictionRule> rules(Long laboratoryId)
    {
        requirePermission("lab:restriction:rule");
        manager(laboratoryId);
        return mapper.rules(laboratoryId);
    }

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public RestrictionRule publish(RestrictionCommands.Rule command)
    {
        requirePermission("lab:restriction:rule");
        guard.gate();
        manager(command.laboratoryId());
        RestrictionRule row=new RestrictionRule();
        row.laboratoryId=command.laboratoryId();row.days=RestrictionPolicy.days(command.days(),90);
        row.reason=RestrictionPolicy.reason(command.reason());row.createdBy=permissions.currentUserId();row.createdAt=now();
        mapper.publish(row);
        return row;
    }

    /** Parent lock also serializes appeal submission against evidence changes. */
    public void lockEvidenceOwner(Long id)
    {
        RestrictionRecord row=lock(id);
        if (!Objects.equals(row.userId,permissions.currentUserId()) || mapper.appealLocked(id)!=null) throw denied();
    }

    private RestrictionRecord lock(Long id)
    {
        RestrictionRecord snapshot=required(id);
        guard.lockUsers(List.of(snapshot.userId));
        RestrictionRecord row=mapper.locked(id);
        if (row==null) throw missing();
        return row;
    }
    private RestrictionRecord required(Long id)
    {
        if (id==null || id<=0) throw RestrictionPolicy.invalid("限制编号无效");
        RestrictionRecord row=mapper.byId(id,now());
        if (row==null) throw missing();
        return row;
    }
    private void manager(Long laboratoryId)
    {
        users.assertActiveRole(permissions.currentUserId(),"lab_manager");
        permissions.assertLaboratoryManageable(laboratoryId);
    }
    private void notOwner(RestrictionRecord row) { if (Objects.equals(row.userId,permissions.currentUserId())) throw denied(); }
    private static void requirePermission(String permission) { if (!SecurityUtils.hasPermi(permission)) throw denied(); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }
    private static LabBusinessException denied() { return new LabBusinessException(LabErrorCode.ACCESS_DENIED,"无权执行该限制操作，不能为本人创建、解除或审核限制"); }
    private static LabBusinessException duplicate() { return new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION,"记录已处理，请刷新后重试；每条限制仅可申诉一次"); }
    private static LabBusinessException missing() { return new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND,"限制记录不存在"); }
}
