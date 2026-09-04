package com.ruoyi.lab.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.domain.LabHazard;
import com.ruoyi.lab.domain.LabReservation;
import com.ruoyi.lab.domain.LabStatusHistory;
import com.ruoyi.lab.domain.HazardTargetType;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabHazardMapper;
import com.ruoyi.lab.mapper.LabInspectionTaskMapper;
import com.ruoyi.lab.mapper.LabNotificationRecipientMapper;
import com.ruoyi.lab.mapper.LabRepairOrderMapper;
import com.ruoyi.lab.mapper.LabReservationMapper;
import com.ruoyi.lab.mapper.LabStatusHistoryMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationExpectationResolverImplTest
{
    private LabStatusHistoryMapper historyMapper;
    private LabReservationMapper reservationMapper;
    private LabHazardMapper hazardMapper;
    private LabNotificationRecipientMapper recipientMapper;
    private NotificationExpectationResolverImpl resolver;

    @BeforeEach
    void setUp()
    {
        historyMapper = mock(LabStatusHistoryMapper.class);
        reservationMapper = mock(LabReservationMapper.class);
        hazardMapper = mock(LabHazardMapper.class);
        recipientMapper = mock(LabNotificationRecipientMapper.class);
        resolver = new NotificationExpectationResolverImpl(historyMapper, reservationMapper,
                mock(LabRepairOrderMapper.class), mock(LabInspectionTaskMapper.class),
                hazardMapper, mock(LabDeviceMapper.class), recipientMapper);
    }

    @Test
    void historyKeyUsesThePersistedHistoryId()
    {
        LabStatusHistory history = history(802L, "RESERVATION", "APPROVED", 91L);
        LabReservation reservation = new LabReservation();
        reservation.setId(91L);
        reservation.setApplicantId(18L);
        when(historyMapper.selectActiveById(802L)).thenReturn(history);
        when(reservationMapper.selectActiveById(91L)).thenReturn(reservation);

        assertThat(resolver.resolveHistory(802L)).singleElement().satisfies(command -> {
            assertThat(command.dedupeKey())
                    .isEqualTo("history:802:RESERVATION_APPROVED:18");
            assertThat(command.receiverId()).isEqualTo(18L);
        });
    }

    @Test
    void hazardOverdueUsesPersistedVersionAndIncludesOwnerAndSafetyOfficers()
    {
        LabHazard hazard = new LabHazard();
        hazard.setId(99L);
        hazard.setTargetType(HazardTargetType.LABORATORY);
        hazard.setTargetId(10L);
        hazard.setOwnerId(22L);
        hazard.setOverdueEventVersion(2L);
        when(hazardMapper.selectActiveById(99L)).thenReturn(hazard);
        when(recipientMapper.selectScopedRoleUserIds(10L, "lab_safety_officer"))
                .thenReturn(List.of(33L));

        assertThat(resolver.resolveHazardOverdue(99L, 2L))
                .extracting(command -> command.dedupeKey())
                .containsExactlyInAnyOrder(
                        "overdue:hazard:99:2:22",
                        "overdue:hazard:99:2:33");
    }

    private static LabStatusHistory history(Long id, String type, String toStatus,
            Long objectId)
    {
        LabStatusHistory history = new LabStatusHistory();
        history.setId(id);
        history.setObjectType(type);
        history.setObjectId(objectId);
        history.setToStatus(toStatus);
        return history;
    }
}
