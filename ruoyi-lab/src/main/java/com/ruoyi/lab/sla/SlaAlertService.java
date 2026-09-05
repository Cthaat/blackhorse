package com.ruoyi.lab.sla;

import java.time.LocalDateTime;
import java.util.*;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.mapper.*;
import com.ruoyi.lab.service.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.support.*;

@Service
public class SlaAlertService
{
    private final LabSlaMapper mapper;
    private final LabNotificationRecipientMapper recipients;
    private final LabNotificationDeliveryService delivery;
    public SlaAlertService(LabSlaMapper mapper,LabNotificationRecipientMapper recipients,LabNotificationDeliveryService delivery)
    {this.mapper=mapper;this.recipients=recipients;this.delivery=delivery;}
    public List<Long> candidates(int limit){if(limit<1||limit>1000)throw SlaPolicy.invalid("扫描批量无效");return mapper.candidates(limit);}
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public void scan(Long id,LocalDateTime now) {
        if(now==null)throw SlaPolicy.invalid("扫描时间无效");
        SlaRecord r=mapper.locked(id);if(r==null)return;
        mapper.checked(id,now);if(r.closedAt!=null)return;
        phase(r,"RESPONSE",r.responseDueAt,r.responseHours,now,r.respondedAt!=null);
        phase(r,"PROCESSING",r.processingDueAt,r.processingHours,now,r.completedAt!=null||r.pausedAt!=null);
    }
    private void phase(SlaRecord r,String phase,LocalDateTime due,int hours,LocalDateTime now,boolean stopped) {
        String stage=SlaPolicy.stage(due,hours,now,stopped);if(stage==null)return;
        if("ESCALATED".equals(stage))register(r,phase,"DUE",due,now);
        register(r,phase,stage,due,now);
    }
    private void register(SlaRecord r,String phase,String stage,LocalDateTime due,LocalDateTime now) {
        if(mapper.hasAlert(r.id,phase,stage)>0)return;
        SlaAlert alert=new SlaAlert();alert.recordId=r.id;alert.phase=phase;alert.stage=stage;alert.createdAt=now;mapper.alert(alert);
        Set<Long> receivers=new LinkedHashSet<>();
        if("ESCALATED".equals(stage)) {
            receivers.addAll(recipients.selectScopedRoleUserIds(r.laboratoryId,"lab_manager"));
            if("HAZARD".equals(r.businessType))receivers.addAll(recipients.selectScopedRoleUserIds(r.laboratoryId,"lab_safety_officer"));
        } else if(mapper.activeBusinessUser(r.ownerId)>0)receivers.add(r.ownerId);
        for(Long receiver:receivers) {
            SlaNotice notice=new SlaNotice();notice.alertId=alert.id;notice.recordId=r.id;notice.receiverId=receiver;
            notice.title="SLA 时效提醒";
            notice.content=r.title+" 的"+("RESPONSE".equals(phase)?"响应":"处理")+"阶段："+stage+"，截止时间 "+due+"。这是 "+now+" 登记的提醒事实，请打开业务查看最新状态。";
            mapper.notice(notice);
            NotificationCommand command=command(notice);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){delivery.deliverSafely(command);}});
        }
    }
    public static NotificationCommand command(SlaNotice n) {
        return new NotificationCommand("SLA:"+n.id+":NOTICE",n.receiverId,"SLA_NOTICE",n.title,n.content,"SLA",n.recordId);
    }
}
