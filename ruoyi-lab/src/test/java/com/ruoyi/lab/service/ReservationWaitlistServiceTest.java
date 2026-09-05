package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.time.LocalDateTime;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.domain.LabReservationWaitlist;
import com.ruoyi.lab.dto.ReservationApplyDto;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabReservationWaitlistMapper;
import com.ruoyi.lab.security.LabObjectPermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;

class ReservationWaitlistServiceTest
{
    private final LabReservationWaitlistMapper queue = mock(LabReservationWaitlistMapper.class);
    private final LabDeviceMapper devices = mock(LabDeviceMapper.class);
    private final LabObjectPermissionService permissions = mock(LabObjectPermissionService.class);
    private final ReservationPolicy policy = mock(ReservationPolicy.class);
    private final ReservationRequestHasher hasher = mock(ReservationRequestHasher.class);
    private final ReservationWaitlistCoordinator coordinator = mock(ReservationWaitlistCoordinator.class);
    private final ReservationApplyDto request = new ReservationApplyDto();
    private final DuplicateKeyException collision = new DuplicateKeyException("duplicate key");

    private ReservationWaitlistService service(LabReservationWaitlist concurrent)
    {
        var range = new ReservationPolicy.ValidatedReservation(2L, LocalDateTime.of(2026, 9, 6, 9, 0),
                LocalDateTime.of(2026, 9, 6, 10, 0), "实验", null);
        when(policy.validate(request)).thenReturn(range);
        when(permissions.currentUserId()).thenReturn(9L);
        when(hasher.hash(range, null)).thenReturn("new-hash");
        LabDevice device = new LabDevice();
        device.setId(2L);
        when(devices.selectByIdForUpdate(2L)).thenReturn(device);
        when(queue.byKey(9L, "same-key")).thenReturn(null, concurrent);
        when(queue.insert(any(LabReservationWaitlist.class))).thenThrow(collision);
        return new ReservationWaitlistService(queue, devices, permissions, policy, hasher, coordinator,
                mock(ReservationCommandService.class), Clock.systemUTC(), mock(com.ruoyi.lab.restriction.RestrictionGuard.class));
    }

    private LabReservationWaitlist concurrent(String hash)
    {
        LabReservationWaitlist row = new LabReservationWaitlist();
        row.setId(7L);
        row.setDeviceId(1L);
        row.setApplicantId(9L);
        row.setRequestHash(hash);
        row.setStatus("WAITING");
        row.setVersion(0);
        return row;
    }

    @Test void concurrentKeyForAnotherDeviceIsAContentConflict()
    {
        var service = service(concurrent("other-device-hash"));
        assertThatThrownBy(() -> service.join("same-key", request))
                .isInstanceOfSatisfying(LabBusinessException.class,
                        error -> assertThat(error.getErrorCode()).isEqualTo(LabErrorCode.LAB_DUPLICATE_OPERATION));
    }

    @Test void concurrentMatchingRequestReplaysThePersistedEntry()
    {
        var row = concurrent("new-hash");
        row.setDeviceId(2L);
        var service = service(row);
        assertThat(service.join("same-key", request).id()).isEqualTo(7L);
        verify(queue, times(2)).byKey(9L, "same-key");
    }

    @Test void unrelatedDuplicateKeyIsNotMasked()
    {
        var service = service(null);
        assertThatThrownBy(() -> service.join("same-key", request)).isSameAs(collision);
    }
}
