package com.ruoyi.lab.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.domain.LabNotification;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.mapper.LabHazardMapper;
import com.ruoyi.lab.mapper.LabInspectionTaskMapper;
import com.ruoyi.lab.mapper.LabNotificationMapper;
import com.ruoyi.lab.mapper.LabStatusHistoryMapper;
import com.ruoyi.lab.service.LabNotificationDeliveryService;
import com.ruoyi.lab.service.NotificationExpectationResolver;
import org.junit.jupiter.api.Test;

class LabNotificationCompensationServiceImplTest
{
    @Test
    void retriesFailedRowThroughTheSameIdempotentDeliveryPath()
    {
        LocalDateTime now = LocalDateTime.of(2026, 9, 3, 10, 0);
        LabNotification row = new LabNotification();
        row.setDedupeKey("history:8:RESERVATION_APPROVED:3");
        row.setReceiverId(3L);
        row.setNotificationType("RESERVATION_APPROVED");
        row.setTitle("title");
        row.setContent("content");
        row.setBusinessType("RESERVATION");
        row.setBusinessId(5L);
        LabNotificationMapper notifications = mock(LabNotificationMapper.class);
        when(notifications.selectRetryable(now, 100)).thenReturn(List.of(row));
        LabNotificationDeliveryService delivery = mock(LabNotificationDeliveryService.class);
        LabNotificationCompensationServiceImpl service = service(notifications, delivery);

        assertThat(service.retryFailed(now, 100)).isEqualTo(1);
        verify(delivery).deliverSafely(any(NotificationCommand.class));
    }

    private static LabNotificationCompensationServiceImpl service(
            LabNotificationMapper notifications, LabNotificationDeliveryService delivery)
    {
        return new LabNotificationCompensationServiceImpl(notifications,
                mock(LabStatusHistoryMapper.class), mock(LabInspectionTaskMapper.class),
                mock(LabHazardMapper.class), mock(NotificationExpectationResolver.class),
                delivery);
    }
}
