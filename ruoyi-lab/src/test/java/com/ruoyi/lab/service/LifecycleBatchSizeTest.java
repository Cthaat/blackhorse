package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.event.LabNotificationEventPublisher;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabHazardMapper;
import com.ruoyi.lab.mapper.LabInspectionItemMapper;
import com.ruoyi.lab.mapper.LabInspectionPlanItemMapper;
import com.ruoyi.lab.mapper.LabInspectionPlanMapper;
import com.ruoyi.lab.mapper.LabInspectionTaskMapper;
import com.ruoyi.lab.mapper.LabNotificationMapper;
import com.ruoyi.lab.mapper.LabReservationMapper;
import com.ruoyi.lab.service.impl.HazardLifecycleServiceImpl;
import com.ruoyi.lab.service.impl.InspectionLifecycleServiceImpl;
import com.ruoyi.lab.service.impl.InspectionScheduleServiceImpl;
import com.ruoyi.lab.service.impl.NotificationDeliveryServiceImpl;
import com.ruoyi.lab.service.impl.ReservationLifecycleServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LifecycleBatchSizeTest
{
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 3, 12, 0);
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-03T04:00:00Z"),
            ZoneId.of("Asia/Shanghai"));

    private ReservationLifecycleService reservations;
    private InspectionScheduleService schedules;
    private InspectionLifecycleService inspections;
    private HazardLifecycleService hazards;
    private NotificationDeliveryService notifications;

    @BeforeEach
    void setUp()
    {
        LabSystemOperatorProvider operatorProvider = mock(LabSystemOperatorProvider.class);
        when(operatorProvider.requiredOperator()).thenReturn(new LabSystemOperator(9000L, "system"));

        LabReservationMapper reservationMapper = mock(LabReservationMapper.class);
        when(reservationMapper.selectPendingExpiryCandidates(any(), anyInt()))
                .thenReturn(List.of());
        when(reservationMapper.selectNoShowCandidates(any(), anyInt())).thenReturn(List.of());
        LabSystemParameterProvider parameters = mock(LabSystemParameterProvider.class);
        when(parameters.requiredInteger(any(), anyInt(), anyInt())).thenReturn(15);
        reservations = new ReservationLifecycleServiceImpl(reservationMapper,
                mock(LabDeviceMapper.class), operatorProvider, parameters,
                mock(LabStatusHistoryService.class), mock(com.ruoyi.lab.restriction.RestrictionGuard.class),
                mock(com.ruoyi.lab.restriction.RestrictionService.class));

        LabInspectionPlanMapper planMapper = mock(LabInspectionPlanMapper.class);
        when(planMapper.selectDuePlansForUpdate(any(), anyInt())).thenReturn(List.of());
        schedules = new InspectionScheduleServiceImpl(planMapper,
                mock(LabInspectionPlanItemMapper.class), mock(LabInspectionTaskMapper.class),
                mock(LabInspectionItemMapper.class), operatorProvider,
                mock(LabStatusHistoryService.class), CLOCK);

        inspections = new InspectionLifecycleServiceImpl(mock(LabInspectionTaskMapper.class),
                operatorProvider, mock(LabNotificationEventPublisher.class));
        hazards = new HazardLifecycleServiceImpl(mock(LabHazardMapper.class), operatorProvider,
                mock(LabNotificationEventPublisher.class));

        LabNotificationMapper notificationMapper = mock(LabNotificationMapper.class);
        when(notificationMapper.selectDueFailedIds(any(), anyInt())).thenReturn(List.of());
        notifications = new NotificationDeliveryServiceImpl(notificationMapper,
                new MessageDeliveryEngine(mock(MessageDeliveryStore.class),
                        mock(com.ruoyi.lab.mapper.LabMessageDeliveryMapper.class), mock(MessageChannel.class), CLOCK), CLOCK);
    }

    @Test
    void acceptsTheConfiguredMaximumAcrossEveryLifecycleService()
    {
        assertThatCode(() -> reservations.expirePending(NOW, 1000)).doesNotThrowAnyException();
        assertThatCode(() -> reservations.markNoShow(NOW, 1000)).doesNotThrowAnyException();
        assertThatCode(() -> schedules.generateDueTasks(NOW, 1000)).doesNotThrowAnyException();
        assertThatCode(() -> inspections.markOverdue(NOW, 1000)).doesNotThrowAnyException();
        assertThatCode(() -> hazards.markOverdue(NOW, 1000)).doesNotThrowAnyException();
        assertThatCode(() -> notifications.compensateDue(1000)).doesNotThrowAnyException();
    }

    @Test
    void rejectsBatchesAboveTheConfiguredMaximumAcrossEveryLifecycleService()
    {
        assertThatThrownBy(() -> reservations.expirePending(NOW, 1001))
                .isInstanceOf(LabBusinessException.class);
        assertThatThrownBy(() -> schedules.generateDueTasks(NOW, 1001))
                .isInstanceOf(LabBusinessException.class);
        assertThatThrownBy(() -> inspections.markOverdue(NOW, 1001))
                .isInstanceOf(LabBusinessException.class);
        assertThatThrownBy(() -> hazards.markOverdue(NOW, 1001))
                .isInstanceOf(LabBusinessException.class);
        assertThatThrownBy(() -> notifications.compensateDue(1001))
                .isInstanceOf(LabBusinessException.class);
    }
}
