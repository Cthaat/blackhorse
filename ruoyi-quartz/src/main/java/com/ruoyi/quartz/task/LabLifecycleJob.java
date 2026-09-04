package com.ruoyi.quartz.task;

import java.time.Clock;
import java.time.LocalDateTime;
import com.ruoyi.lab.config.LabJobProperties;
import com.ruoyi.lab.service.HazardLifecycleService;
import com.ruoyi.lab.service.InspectionLifecycleService;
import com.ruoyi.lab.service.InspectionScheduleService;
import com.ruoyi.lab.service.LabNotificationCompensationService;
import com.ruoyi.lab.service.ReservationLifecycleService;
import org.springframework.stereotype.Component;

/**
 * Small, idempotent entry points invoked by the persisted Quartz jobs.
 */
@Component("labLifecycleJob")
public class LabLifecycleJob
{
    private final ReservationLifecycleService reservationLifecycleService;
    private final InspectionScheduleService inspectionScheduleService;
    private final InspectionLifecycleService inspectionLifecycleService;
    private final HazardLifecycleService hazardLifecycleService;
    private final LabNotificationCompensationService notificationCompensationService;
    private final LabJobProperties jobProperties;
    private final Clock clock;

    public LabLifecycleJob(ReservationLifecycleService reservationLifecycleService,
            InspectionScheduleService inspectionScheduleService,
            InspectionLifecycleService inspectionLifecycleService,
            HazardLifecycleService hazardLifecycleService,
            LabNotificationCompensationService notificationCompensationService,
            LabJobProperties jobProperties, Clock clock)
    {
        this.reservationLifecycleService = reservationLifecycleService;
        this.inspectionScheduleService = inspectionScheduleService;
        this.inspectionLifecycleService = inspectionLifecycleService;
        this.hazardLifecycleService = hazardLifecycleService;
        this.notificationCompensationService = notificationCompensationService;
        this.jobProperties = jobProperties;
        this.clock = clock;
    }

    public int expirePendingReservations()
    {
        return reservationLifecycleService.expirePending(now(), batchSize());
    }

    public int markNoShowReservations()
    {
        return reservationLifecycleService.markNoShow(now(), batchSize());
    }

    public int generateInspectionTasks()
    {
        return inspectionScheduleService.generateDueTasks(now(), batchSize());
    }

    public int markInspectionOverdue()
    {
        return inspectionLifecycleService.markOverdue(now(), batchSize());
    }

    public int markHazardOverdue()
    {
        return hazardLifecycleService.markOverdue(now(), batchSize());
    }

    public int compensateNotifications()
    {
        LocalDateTime now = now();
        return notificationCompensationService.retryFailed(now, batchSize())
                + notificationCompensationService.reconcileStatusHistory(now, batchSize());
    }

    private LocalDateTime now()
    {
        return LocalDateTime.now(clock);
    }

    private int batchSize()
    {
        return jobProperties.getBatchSize();
    }
}
