package com.ruoyi.lab.service;
import java.time.Clock;
import java.time.LocalDateTime;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.mapper.LabMessageDeliveryMapper;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

/** Single bounded retry engine for after-commit, legacy and scheduled delivery. */
@Service
public class MessageDeliveryEngine
{
    private final MessageDeliveryStore store;
    private final LabMessageDeliveryMapper mapper;
    private final MessageChannel channel;
    private final Clock clock;
    public MessageDeliveryEngine(MessageDeliveryStore store,LabMessageDeliveryMapper mapper,MessageChannel channel,Clock clock)
    { this.store=store;this.mapper=mapper;this.channel=channel;this.clock=clock; }
    public void registerAndDeliver(NotificationCommand command) { execute(store.register(command)); }
    public int retryDue(LocalDateTime now,int limit)
    {
        if(now==null||limit<1||limit>1000) throw MessageDeliveryPolicy.invalid("补偿批量大小无效");
        store.recover(now,limit);
        var ids=mapper.due(now,limit);
        for(Long id:ids) execute(id);
        return ids.size();
    }
    public int backfillWaitlists(int limit)
    {
        var missing=mapper.missingWaitlists(limit);
        for(var row:missing) registerAndDeliver(new NotificationCommand("WAITLIST:"+row.getId()+":OFFERED",
                row.getApplicantId(),"WAITLIST_OFFERED","预约候补确认邀请",
                "您申请的时段已空出，请在"+row.getOfferedUntil()+"前打开我的预约中的开放日历与候补确认。确认后仍需审批。","WAITLIST",row.getId()));
        return missing.size();
    }
    public int backfillSla(int limit)
    {
        var missing=mapper.missingSlaNotices(limit);
        for(var notice:missing) registerAndDeliver(com.ruoyi.lab.sla.SlaAlertService.command(notice));
        return missing.size();
    }
    private void execute(Long id)
    {
        var row=store.claim(id,LocalDateTime.now(clock));
        if(row==null) return;
        String previous=MDC.get("traceId");
        try {
            if(row.traceId==null) MDC.remove("traceId"); else MDC.put("traceId",row.traceId);
            String error=null;
            try { channel.send(new NotificationCommand(row.dedupeKey,row.receiverId,row.eventType,row.titleSnapshot,row.contentSnapshot,row.businessType,row.businessId)); }
            catch(RuntimeException failure) { error=failure instanceof DataAccessException?"DATA_ACCESS_ERROR":"DELIVERY_ERROR"; }
            store.finish(row,error,LocalDateTime.now(clock));
        } finally { if(previous==null) MDC.remove("traceId"); else MDC.put("traceId",previous); }
    }
}
