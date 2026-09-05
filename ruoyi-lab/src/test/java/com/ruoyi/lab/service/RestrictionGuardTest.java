package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.time.*;
import java.util.List;
import com.ruoyi.lab.mapper.LabRestrictionMapper;
import com.ruoyi.lab.restriction.RestrictionGuard;
import com.ruoyi.lab.exception.LabBusinessException;
import org.junit.jupiter.api.Test;

class RestrictionGuardTest
{
    @Test void locksAllQueueApplicantsInAscendingOrderBeforeTheCallerTakesDeviceLock()
    {
        var mapper = mock(LabRestrictionMapper.class);
        when(mapper.lockGate()).thenReturn(LocalDateTime.of(2026,9,5,0,0));
        when(mapper.deviceUsers(2L)).thenReturn(List.of(8L,3L,8L));
        var guard = new RestrictionGuard(mapper, Clock.systemUTC());
        guard.lockDeviceUsers(2L,5L);
        var order = inOrder(mapper);
        order.verify(mapper).lockGate();
        order.verify(mapper).lockUser(3L);
        order.verify(mapper).lockUser(5L);
        order.verify(mapper).lockUser(8L);
    }

    @Test void anyOverlappingActiveFactBlocksAndExpiredUnionDoesNot()
    {
        var mapper = mock(LabRestrictionMapper.class);
        Clock clock = Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);
        var guard = new RestrictionGuard(mapper, clock);
        when(mapper.activeCount(1L,2L,LocalDateTime.now(clock))).thenReturn(2,0);
        assertThatThrownBy(() -> guard.assertAllowed(1L,2L)).isInstanceOf(LabBusinessException.class);
        assertThatCode(() -> guard.assertAllowed(1L,2L)).doesNotThrowAnyException();
    }
}
