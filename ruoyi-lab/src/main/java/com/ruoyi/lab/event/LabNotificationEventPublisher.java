package com.ruoyi.lab.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** Publishes fact references without projecting notifications in the core transaction. */
@Component
public class LabNotificationEventPublisher
{
    private static final Logger LOG =
            LoggerFactory.getLogger(LabNotificationEventPublisher.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    public LabNotificationEventPublisher(ApplicationEventPublisher applicationEventPublisher)
    {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    public void publishHistory(long historyId)
    {
        publish(LabNotificationRequested.history(historyId));
    }

    public void publishInspectionOverdue(long taskId, long overdueEventVersion)
    {
        publish(LabNotificationRequested.inspectionOverdue(taskId, overdueEventVersion));
    }

    public void publishHazardOverdue(long hazardId, long overdueEventVersion)
    {
        publish(LabNotificationRequested.hazardOverdue(hazardId, overdueEventVersion));
    }

    private void publish(LabNotificationRequested event)
    {
        try
        {
            applicationEventPublisher.publishEvent(event);
        }
        catch (RuntimeException failure)
        {
            LOG.error("Unable to publish laboratory notification fact; type={}; referenceId={}; version={}",
                    event.type(), event.referenceId(), event.version(), failure);
        }
    }
}
