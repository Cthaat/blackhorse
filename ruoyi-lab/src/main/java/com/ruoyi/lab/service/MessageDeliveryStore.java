package com.ruoyi.lab.service;

import java.time.Clock;
import java.time.LocalDateTime;
import com.ruoyi.lab.domain.LabMessageDelivery;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.mapper.LabMessageDeliveryMapper;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** All execution-state writes have independent transactions, including the after-commit path. */
@Service
public class MessageDeliveryStore
{
    private final LabMessageDeliveryMapper mapper;
    private final MessageTemplateService templates;
    private final Clock clock;
    public MessageDeliveryStore(LabMessageDeliveryMapper mapper,MessageTemplateService templates,Clock clock)
    { this.mapper=mapper; this.templates=templates; this.clock=clock; }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public Long register(NotificationCommand command)
    {
        LabMessageDelivery existing=mapper.byKey(command.dedupeKey());
        if(existing!=null) return existing.id;
        LabMessageDelivery row=new LabMessageDelivery();
        row.dedupeKey=command.dedupeKey(); row.receiverId=command.receiverId(); row.eventType=command.notificationType();
        row.businessType=command.businessType(); row.businessId=command.businessId();
        source(row);
        var snapshot=templates.snapshot(command);
        row.templateVersion=snapshot.version(); row.titleSnapshot=snapshot.title(); row.contentSnapshot=snapshot.content();
        row.createTime=LocalDateTime.now(clock); row.updateTime=row.createTime; row.nextRetryAt=row.createTime;
        row.traceId=MDC.get("traceId"); row.status="PENDING";
        if(MessageDeliveryPolicy.optional(row.eventType)&&!templates.preference(row.receiverId))
        { row.status="SUPPRESSED"; row.errorCode="USER_PREFERENCE"; row.nextRetryAt=null; }
        mapper.register(row);
        return mapper.byKey(row.dedupeKey).id;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public LabMessageDelivery claim(Long id,LocalDateTime now)
    {
        if(mapper.claim(id,now,now.plusSeconds(60))!=1) return null;
        LabMessageDelivery row=mapper.byId(id);
        mapper.audit(id,"ATTEMPT",row.attemptCount,null,null,"PROCESSING",null,row.traceId,now);
        return row;
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void finish(LabMessageDelivery row,String error,LocalDateTime now)
    {
        Integer delay=MessageDeliveryPolicy.retryMinutes(row.attemptCount);
        String status=error==null?"DELIVERED":delay==null?"MANUAL_REQUIRED":"RETRY_WAIT";
        LocalDateTime next=error==null||delay==null?null:now.plusMinutes(delay);
        if(mapper.finish(row.id,row.executionVersion,status,error,next,now)==1)
            mapper.audit(row.id,"RESULT",row.attemptCount,null,null,status,error,row.traceId,now);
    }

    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void recover(LocalDateTime now,int limit)
    {
        for(LabMessageDelivery row:mapper.expired(now,limit))
            finish(row,mapper.inboxSent(row.dedupeKey)>0?null:"LEASE_EXPIRED",now);
    }

    @Transactional
    public void replay(Long id,String reason,Long operator)
    {
        if(reason==null||reason.isBlank()||reason.length()>200) throw MessageDeliveryPolicy.invalid("重放必须填写不超过200字的原因");
        LabMessageDelivery row=mapper.locked(id);
        if(row==null||!"MANUAL_REQUIRED".equals(row.status)||!factExists(row)) throw MessageDeliveryPolicy.invalid("仅可重放来源事实仍合法存在的人工处理记录");
        LocalDateTime now=LocalDateTime.now(clock);
        if(mapper.replay(id,now)!=1) throw MessageDeliveryPolicy.invalid("投递状态已变化，请刷新");
        mapper.audit(id,"REPLAY",row.attemptCount,operator,reason.trim(),"PENDING",null,MDC.get("traceId"),now);
    }
    private boolean factExists(LabMessageDelivery row)
    {
        return switch(row.sourceType) {
            case "STATUS_HISTORY" -> mapper.historyExists(row.sourceId)>0;
            case "INSPECTION_OVERDUE" -> mapper.inspectionExists(row.sourceId,row.eventVersion)>0;
            case "HAZARD_OVERDUE" -> mapper.hazardExists(row.sourceId,row.eventVersion)>0;
            case "WAITLIST_OFFERED" -> mapper.waitlistExists(row.sourceId)>0;
            default -> false;
        };
    }
    private static void source(LabMessageDelivery row)
    {
        String[] key=row.dedupeKey.split(":"); row.eventVersion=1L;
        try {
            if(key.length==4&&"history".equals(key[0])) {row.sourceType="STATUS_HISTORY";row.sourceId=Long.valueOf(key[1]);}
            else if(key.length==5&&"overdue".equals(key[0])) {row.sourceType="hazard".equals(key[1])?"HAZARD_OVERDUE":"INSPECTION_OVERDUE";row.sourceId=Long.valueOf(key[2]);row.eventVersion=Long.valueOf(key[3]);}
            else if(key.length==3&&"WAITLIST".equals(key[0])) {row.sourceType="WAITLIST_OFFERED";row.sourceId=Long.valueOf(key[1]);}
            else {row.sourceType="LEGACY";row.sourceId=row.businessId;}
        } catch(NumberFormatException invalid) {throw MessageDeliveryPolicy.invalid("事实去重键无效");}
    }
}
