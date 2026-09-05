package com.ruoyi.lab.service.impl;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.service.LabNotificationDeliveryService;
import com.ruoyi.lab.service.MessageDeliveryEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Registration failure is recoverable from authoritative source facts. */
@Service
public class LabNotificationDeliveryServiceImpl implements LabNotificationDeliveryService
{
    private static final Logger LOG=LoggerFactory.getLogger(LabNotificationDeliveryServiceImpl.class);
    private final MessageDeliveryEngine engine;
    public LabNotificationDeliveryServiceImpl(MessageDeliveryEngine engine) { this.engine=engine; }
    @Override public void deliverSafely(NotificationCommand command)
    {
        try { engine.registerAndDeliver(command); }
        catch(RuntimeException failure) { LOG.error("Notification registration or delivery interrupted; code=DELIVERY_INTERRUPTED"); }
    }
}
