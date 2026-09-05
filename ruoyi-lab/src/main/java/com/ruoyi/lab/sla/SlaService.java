package com.ruoyi.lab.sla;

import java.time.*;
import java.util.*;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.exception.*;
import com.ruoyi.lab.mapper.LabSlaMapper;
import com.ruoyi.lab.service.LabPage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class SlaService
{
    private final LabSlaMapper mapper;private final SlaLifecycle lifecycle;private final Clock clock;
    public SlaService(LabSlaMapper mapper,SlaLifecycle lifecycle,Clock clock){this.mapper=mapper;this.lifecycle=lifecycle;this.clock=clock;}
    public record Detail(SlaRecord record,List<SlaTrace> history,List<SlaAlert> alerts) { }
    public List<SlaRecord> list(String businessType,String state,boolean mine) {
        long actor=authorize("list");String type=filter(businessType,Set.of("REPAIR","MAINTENANCE","HAZARD"));
        String status=filter(state,Set.of("OPEN","NEAR_DUE","OVERDUE","PAUSED","CLOSED"));
        return LabPage.query(()->mapper.list(actor,mine,type,status,LocalDateTime.now(clock)),r->project(r,actor));
    }
    public Detail detail(Long id) {
        long actor=authorize("list");SlaRecord r=required(id);
        if(!Objects.equals(r.ownerId,actor)&&!manageable(r,actor))throw denied();
        return new Detail(project(r,actor),mapper.traces(id),mapper.alerts(id));
    }
    public List<SlaRule> rules(Long laboratoryId) {
        long actor=authorize("rule");
        if(!mapper.canManage(actor,laboratoryId,"HAZARD"))throw denied();
        return mapper.rules(laboratoryId,actor);
    }
    @Transactional
    public SlaRule publish(SlaCommands.Rule command) {
        long actor=authorize("rule");
        if(command==null||command.laboratoryId()==null||command.responseHours()==null||command.processingHours()==null
                ||!Set.of("REPAIR","MAINTENANCE","HAZARD").contains(command.businessType()==null?"":command.businessType())
                ||!Set.of("LOW","MEDIUM","HIGH","MAJOR").contains(command.risk()==null?"":command.risk())
                ||command.responseHours()<1||command.responseHours()>720||command.processingHours()<command.responseHours()||command.processingHours()>8760)
            throw SlaPolicy.invalid("时效规则参数无效");
        if(!mapper.canManage(actor,command.laboratoryId(),command.businessType()))throw denied();
        SlaRule r=new SlaRule();r.laboratoryId=command.laboratoryId();r.businessType=command.businessType();r.risk=command.risk();
        r.responseHours=command.responseHours();r.processingHours=command.processingHours();r.reason=SlaPolicy.reason(command.reason());r.createdBy=actor;r.createdAt=LocalDateTime.now(clock);
        mapper.insertRule(r);return r;
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public SlaRecord clock(Long id,boolean pause,SlaCommands.ClockCommand command) {
        long actor=authorize("manage");SlaRecord r=mapper.locked(id);if(r==null)throw missing();
        if(!manageable(r,actor))throw denied();
        if(command==null||command.expectedVersion()==null||!command.expectedVersion().equals(r.version))throw SlaPolicy.invalid("时效记录已变化，请刷新");
        String reason=SlaPolicy.reason(command.reason());
        if(r.closedAt!=null||r.completedAt!=null||(pause==(r.pausedAt!=null)))throw SlaPolicy.invalid("当前时效记录不能执行该计时操作");
        LocalDateTime now=LocalDateTime.now(clock);
        if(pause){r.pausedAt=now;r.pauseReason=reason;}else lifecycle.finishPause(r,now,actor,reason);
        lifecycle.save(r);mapper.trace(id,pause?"PAUSED":"RESUMED",reason,actor,now);
        return project(required(id),actor);
    }
    private SlaRecord project(SlaRecord r,long actor){r.canManage=manageable(r,actor)&&SecurityUtils.hasPermi("lab:sla:manage");r.state=SlaPolicy.state(r,LocalDateTime.now(clock));return r;}
    private boolean manageable(SlaRecord r,long actor){return mapper.canManage(actor,r.laboratoryId,r.businessType);}
    private long authorize(String action) {
        if(!SecurityUtils.hasPermi("lab:sla:"+action))throw denied();
        long actor=SecurityUtils.getUserId();if(mapper.activeBusinessUser(actor)==0)throw denied();return actor;
    }
    private SlaRecord required(Long id){SlaRecord r=mapper.byId(id);if(r==null)throw missing();return r;}
    private static String filter(String v,Set<String> values){if(v==null||v.isBlank())return null;if(!values.contains(v))throw SlaPolicy.invalid("筛选参数无效");return v;}
    private static LabBusinessException denied(){return new LabBusinessException(LabErrorCode.ACCESS_DENIED,"无当前时效业务对象权限");}
    private static LabBusinessException missing(){return new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND,"时效记录不存在");}
}
