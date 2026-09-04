package com.ruoyi.lab.service.impl;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.service.LabNotificationPersistenceService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.TransientDataAccessResourceException;

class LabNotificationDeliveryServiceImplTest
{
    @Test
    void recordsASafeFailedAttemptWithoutPropagatingToTheCommittedBusinessFlow()
    {
        LabNotificationPersistenceService persistence =
                mock(LabNotificationPersistenceService.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-03T04:00:00Z"),
                ZoneId.of("Asia/Shanghai"));
        LabNotificationDeliveryServiceImpl service =
                new LabNotificationDeliveryServiceImpl(persistence, clock);
        NotificationCommand command = new NotificationCommand(
                "history:801:RESERVATION_APPROVED:18", 18L,
                "RESERVATION_APPROVED", "预约已批准", "预约申请已批准",
                "RESERVATION", 91L);
        doThrow(new TransientDataAccessResourceException("database unavailable"))
                .when(persistence).insertSent(command);

        assertThatCode(() -> service.deliverSafely(command)).doesNotThrowAnyException();

        verify(persistence).recordFailed(command, "DATA_ACCESS_ERROR",
                LocalDateTime.of(2026, 9, 3, 12, 1));
    }
}
