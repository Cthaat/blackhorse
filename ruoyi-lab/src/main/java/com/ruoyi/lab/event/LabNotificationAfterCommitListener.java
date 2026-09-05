package com.ruoyi.lab.event;

import java.util.List;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.service.LabNotificationDeliveryService;
import com.ruoyi.lab.service.NotificationExpectationResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Keeps station-message persistence outside the already-committed core transaction. */
@Component
public class LabNotificationAfterCommitListener
{
    private static final Logger LOG =
            LoggerFactory.getLogger(LabNotificationAfterCommitListener.class);

    private final NotificationExpectationResolver expectationResolver;
    private final LabNotificationDeliveryService deliveryService;

    public LabNotificationAfterCommitListener(NotificationExpectationResolver expectationResolver,
            LabNotificationDeliveryService deliveryService)
    {
        this.expectationResolver = expectationResolver;
        this.deliveryService = deliveryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handle(LabNotificationRequested event)
    {
        if (event != null && event.type() != null)
        {
            try
            {
                resolve(event).forEach(deliveryService::deliverSafely);
            }
            catch (RuntimeException failure)
            {
                LOG.error("Unable to project laboratory notification fact after commit; type={}; referenceId={}; version={}",
                        event.type(), event.referenceId(), event.version());
            }
        }
    }

    private List<NotificationCommand> resolve(LabNotificationRequested event)
    {
        return switch (event.type())
        {
            case STATUS_HISTORY -> expectationResolver.resolveHistory(event.referenceId());
            case INSPECTION_OVERDUE -> expectationResolver.resolveInspectionOverdue(
                    event.referenceId(), event.version());
            case HAZARD_OVERDUE -> expectationResolver.resolveHazardOverdue(
                    event.referenceId(), event.version());
        };
    }
}
