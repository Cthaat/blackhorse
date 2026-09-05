package com.ruoyi.lab.sla;

import java.time.LocalDateTime;
import java.util.Set;
import com.ruoyi.lab.domain.LabStatusHistory;
import com.ruoyi.lab.mapper.LabSlaMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Critical same-transaction history hook. Never catches failures and never calls history append. */
@Service
public class SlaLifecycle
{
    private final LabSlaMapper mapper;
    public SlaLifecycle(LabSlaMapper mapper) { this.mapper=mapper; }

    @Transactional(propagation=Propagation.MANDATORY)
    public void record(LabStatusHistory h)
    {
        if (!Set.of("REPAIR_ORDER","MAINTENANCE_CYCLE","HAZARD").contains(h.getObjectType())) return;
        String type=h.getObjectType();Long id=h.getObjectId();boolean maintenanceRepair=false;
        if ("REPAIR_ORDER".equals(type)) {
            Long cycle=mapper.repairCycle(id);
            if(cycle!=null){ type="MAINTENANCE_CYCLE";id=cycle;maintenanceRepair=true; }
        }
        SlaRecord row=mapper.objectLocked(type,id);
        LocalDateTime now=h.getCreateTime();String to=h.getToStatus();
        if(row==null) {
            // New facts only. An old object moving state never starts a retroactive clock.
            if(h.getFromStatus()!=null || maintenanceRepair || !Set.of("WAIT_ASSIGN","PLANNED","PENDING_RECTIFICATION").contains(to)) return;
            row=metadata(type,id);if(row==null) throw SlaPolicy.invalid("时效来源对象缺失");
            SlaRule rule=rule(row,now,h.getOperatorId());
            row.ruleVersionId=rule.id;row.responseHours=rule.responseHours;row.processingHours=rule.processingHours;
            row.openedAt=now;row.responseDueAt=now.plusHours(rule.responseHours);row.processingDueAt=now.plusHours(rule.processingHours);
            mapper.insert(row);mapper.trace(row.id,"OPENED","新业务接入 SLA，不追溯历史",h.getOperatorId(),now);return;
        }
        if(row.closedAt!=null)return;
        SlaRecord meta=metadata(type,id);boolean ownerChanged=meta!=null&&!java.util.Objects.equals(row.ownerId,meta.ownerId);
        if(meta!=null)row.ownerId=meta.ownerId;
        boolean response=false,start=false,complete=false,close=false,reopen=false;
        if(maintenanceRepair) {
            complete="WAIT_ACCEPTANCE".equals(to);
            reopen="WAIT_ACCEPTANCE".equals(h.getFromStatus())&&"IN_PROGRESS".equals(to);
            if(!complete&&!reopen) {
                if(ownerChanged&&Set.of("WAIT_REPAIR","IN_PROGRESS").contains(to)) {
                    save(row);mapper.trace(row.id,"OWNER_CHANGED","维护维修派单同步当前责任人",h.getOperatorId(),now);
                }
                return;
            }
        } else if("MAINTENANCE_CYCLE".equals(type)) {
            response="SCHEDULED".equals(to);start="STARTED".equals(to);close="COMPLETED".equals(to);
        } else if("REPAIR_ORDER".equals(type)) {
            response="WAIT_REPAIR".equals(to);start="IN_PROGRESS".equals(to);complete="WAIT_ACCEPTANCE".equals(to);close="CLOSED".equals(to);
            reopen="WAIT_ACCEPTANCE".equals(h.getFromStatus())&&start;
        } else {
            response="RECTIFYING".equals(to);start=response;complete="PENDING_REVIEW".equals(to);close="CLOSED".equals(to);
            reopen="PENDING_REVIEW".equals(h.getFromStatus())&&start;
        }
        if(!response&&!start&&!complete&&!close&&!reopen)return;
        if(response&&row.respondedAt==null)row.respondedAt=now;
        if(start&&row.startedAt==null)row.startedAt=now;
        if(complete||close) {
            finishPause(row,now,h.getOperatorId(),"业务提交或关闭结束暂停");
            if(row.completedAt==null)row.completedAt=now;
        }
        if(reopen)row.completedAt=null;
        if(close)row.closedAt=now;
        save(row);
        mapper.trace(row.id,reopen?"REOPENED":close?"CLOSED":complete?"COMPLETED":start?"STARTED":"RESPONDED",
                "业务状态："+h.getObjectType()+" → "+to,h.getOperatorId(),now);
    }
    private SlaRecord metadata(String type,Long id) {
        return switch(type) {case "REPAIR_ORDER"->mapper.repairMeta(id);case "MAINTENANCE_CYCLE"->mapper.maintenanceMeta(id);default->mapper.hazardMeta(id);};
    }
    private SlaRule rule(SlaRecord row,LocalDateTime now,Long actor) {
        SlaRule rule=mapper.activeRule(row.laboratoryId,row.businessType,row.risk);if(rule!=null)return rule;
        rule=new SlaRule();rule.laboratoryId=row.laboratoryId;rule.businessType=row.businessType;rule.risk=row.risk;
        int[] hours=switch(row.risk){case "MAJOR"->new int[]{1,8};case "HIGH"->new int[]{2,24};case "MEDIUM"->new int[]{4,48};default->new int[]{8,72};};
        rule.responseHours=hours[0];rule.processingHours=hours[1];rule.createdBy=actor;rule.createdAt=now;rule.reason="首次新业务启用默认自然小时规则";rule.builtin=true;
        mapper.insertRule(rule);return mapper.activeRule(row.laboratoryId,row.businessType,row.risk);
    }
    public void finishPause(SlaRecord row,LocalDateTime now,Long actor,String reason) {
        if(row.pausedAt==null)return;
        long elapsed=SlaPolicy.pausedSeconds(row.pausedAt,now);
        row.processingDueAt=row.processingDueAt.plusSeconds(elapsed);row.totalPausedSeconds+=elapsed;
        mapper.trace(row.id,"PAUSE_ENDED",reason,actor,now);
        row.pausedAt=null;row.pauseReason=null;
    }
    public void save(SlaRecord row) {
        if(mapper.update(row)!=1)throw SlaPolicy.invalid("时效记录已变化，请刷新");
    }
}
