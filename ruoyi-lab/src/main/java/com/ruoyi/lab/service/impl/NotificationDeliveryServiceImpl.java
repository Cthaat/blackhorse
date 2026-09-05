package com.ruoyi.lab.service.impl;
import java.time.Clock;
import java.time.LocalDateTime;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.mapper.LabNotificationMapper;
import com.ruoyi.lab.service.MessageDeliveryEngine;
import com.ruoyi.lab.service.NotificationDeliveryService;
import org.springframework.stereotype.Service;

/** Compatibility facade. All callers share the same execution register. */
@Service
public class NotificationDeliveryServiceImpl implements NotificationDeliveryService
{
    private final LabNotificationMapper mapper;
    private final MessageDeliveryEngine engine;
    private final Clock clock;
    public NotificationDeliveryServiceImpl(LabNotificationMapper mapper, MessageDeliveryEngine engine,Clock clock)
    { this.mapper=mapper;this.engine=engine;this.clock=clock; }
    public long deliver(NotificationEvent event)
    {
        engine.registerAndDeliver(new NotificationCommand(event.dedupeKey(),event.receiverId(),event.notificationType(),event.title(),event.content(),event.businessType(),event.businessId()));
        var sent=mapper.selectByDedupeKey(event.dedupeKey());
        return sent==null?0L:sent.getId();
    }
    public int compensateDue(int batchSize) { return engine.retryDue(LocalDateTime.now(clock),batchSize); }
}
