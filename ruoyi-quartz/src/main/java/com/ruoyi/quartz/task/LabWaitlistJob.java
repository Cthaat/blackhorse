package com.ruoyi.quartz.task;

import com.ruoyi.lab.service.ReservationWaitlistCoordinator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Each device is a separate transaction, so one failed queue does not block other devices. */
@Component("labWaitlistJob")
public class LabWaitlistJob
{
    private static final Logger LOG = LoggerFactory.getLogger(LabWaitlistJob.class);
    private final ReservationWaitlistCoordinator coordinator;
    public LabWaitlistJob(ReservationWaitlistCoordinator coordinator) { this.coordinator = coordinator; }

    public int advance()
    {
        int advanced = 0;
        for (Long deviceId : coordinator.dueDevices())
        {
            try { coordinator.advanceDevice(deviceId); advanced++; }
            catch (RuntimeException failure) { LOG.error("Waitlist advancement failed for device {}", deviceId, failure); }
        }
        return advanced;
    }
}
