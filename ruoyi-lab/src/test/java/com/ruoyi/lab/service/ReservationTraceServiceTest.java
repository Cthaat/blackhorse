package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.time.Clock;
import java.util.List;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.domain.LabReservation;
import com.ruoyi.lab.domain.LabUsageRecord;
import com.ruoyi.lab.domain.LabRepairOrder;
import com.ruoyi.lab.domain.RepairStatus;
import com.ruoyi.lab.exception.*;
import com.ruoyi.lab.mapper.*;
import com.ruoyi.lab.security.*;
import com.ruoyi.lab.vo.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class ReservationTraceServiceTest
{
    private final ReservationQueryService reservations = mock(ReservationQueryService.class);
    private final UsageQueryService usages = mock(UsageQueryService.class);
    private final RepairQueryService repairs = mock(RepairQueryService.class);
    private final LabRepairOrderMapper repairMapper = mock(LabRepairOrderMapper.class);
    private final LabUsageRecordMapper usageMapper = mock(LabUsageRecordMapper.class);
    private final LabReservationTraceMapper mapper = mock(LabReservationTraceMapper.class);
    private final LabDataScopeService scopes = mock(LabDataScopeService.class);
    private final LabStatusHistoryObjectAuthorizer authorizer = mock(LabStatusHistoryObjectAuthorizer.class);

    private ReservationTraceService service()
    {
        return new ReservationTraceService(reservations, usages, repairMapper, usageMapper,
                mapper, scopes, authorizer, Clock.systemUTC());
    }

    private void root()
    {
        LabReservation row = new LabReservation();
        row.setId(7L); row.setDeviceId(8L); row.setApplicantId(9L);
        when(reservations.getById(7L, 9L, false)).thenReturn(ReservationVo.from(row));
    }

    @Test void deniesRootBeforeAnyChildRead()
    {
        when(reservations.getById(7L, 9L, false)).thenThrow(new LabBusinessException(
                LabErrorCode.LAB_OUT_OF_DATA_SCOPE, "无权查看预约"));
        assertThatThrownBy(() -> service().trace(7L, 9L, false)).isInstanceOf(LabBusinessException.class);
        verifyNoInteractions(usageMapper, mapper, usages, repairs, repairMapper, scopes, authorizer);
    }

    @Test void rootPermissionDoesNotGrantChildren()
    {
        root();
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            ReservationTraceVo result = service().trace(7L, 9L, false);
            assertThat(result.usage()).isNull();
            assertThat(result.repair()).isNull();
            assertThat(result.qualification()).isNull();
            assertThat(result.hazards().items()).isEmpty();
            assertThat(result.notifications().items()).isEmpty();
            verifyNoInteractions(usageMapper, usages, repairs, repairMapper, scopes);
        }
    }

    @Test void hidesOutOfScopeUsageAndDoesNotFollowItsRepair()
    {
        root();
        LabUsageRecord row = new LabUsageRecord(); row.setId(12L); row.setRepairOrderId(20L);
        when(usageMapper.selectByReservationId(7L)).thenReturn(row);
        when(usages.detail(12L, 9L)).thenThrow(new LabBusinessException(
                LabErrorCode.LAB_OUT_OF_DATA_SCOPE, "无权查看使用记录"));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(() -> SecurityUtils.hasPermi("lab:usage:query")).thenReturn(true);
            assertThat(service().trace(7L, 9L, false).usage()).isNull();
            verifyNoInteractions(repairs, repairMapper);
        }
    }

    @Test void propagatesUnexpectedChildFailures()
    {
        root();
        when(usageMapper.selectByReservationId(7L)).thenThrow(new IllegalStateException("storage"));
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(() -> SecurityUtils.hasPermi("lab:usage:query")).thenReturn(true);
            assertThatThrownBy(() -> service().trace(7L, 9L, false)).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test void followsActualUsageRepairLinkAndBoundsVisibleEvidence()
    {
        root();
        LabUsageRecord row = new LabUsageRecord(); row.setId(12L);
        when(usageMapper.selectByReservationId(7L)).thenReturn(row);
        when(usages.detail(12L, 9L)).thenReturn(new UsageRecordDetailVo(12L, 7L, 8L,
                9L, null, null, 0, 20L, "预约", "设备", "设备", null, null, null, null, null));
        LabRepairOrder order = new LabRepairOrder();
        order.setId(20L); order.setSourceId(999L); order.setStatus(RepairStatus.CLOSED);
        LabDataScope scope = new LabDataScope(9L, false, java.util.Set.of(8L));
        when(scopes.resolveCurrentScope()).thenReturn(scope);
        when(repairMapper.selectScopedDetail(20L, 9L, scope)).thenReturn(RepairOrderVo.from(order));
        List<StatusHistoryVo> rows = java.util.stream.LongStream.range(1, 22)
                .mapToObj(id -> new StatusHistoryVo(id, "RESERVATION", 7L, null, "PENDING",
                        null, null, null, null, null)).toList();
        when(mapper.history(7L, 20L, 21)).thenReturn(rows);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(() -> SecurityUtils.hasPermi("lab:usage:query")).thenReturn(true);
            security.when(() -> SecurityUtils.hasPermi("lab:repair:query")).thenReturn(true);
            security.when(() -> SecurityUtils.hasPermi("lab:notification:list")).thenReturn(true);
            ReservationTraceVo result = service().trace(7L, 9L, false);
            assertThat(result.repair().id()).isEqualTo(20L);
            assertThat(result.history().items()).hasSize(20);
            assertThat(result.history().hasMore()).isTrue();
            verify(mapper).notifications(7L, 12L, 20L, 9L, 21);
            verifyNoInteractions(repairs);
            verify(repairMapper).selectScopedDetail(20L, 9L, scope);
            verify(authorizer).assertReadable("REPAIR_ORDER", 20L, 9L);
            verify(repairMapper, never()).selectScopedDetail(eq(999L), anyLong(), any());
        }
    }

    @Test void deniedRepairHasNoHistoryOrNotificationIdentifier()
    {
        root();
        LabUsageRecord row = new LabUsageRecord(); row.setId(12L);
        when(usageMapper.selectByReservationId(7L)).thenReturn(row);
        when(usages.detail(12L, 9L)).thenReturn(new UsageRecordDetailVo(12L, 7L, 8L,
                9L, null, null, 0, 20L, "预约", "设备", "设备", null, null, null, null, null));
        when(repairMapper.selectScopedDetail(20L, 9L, null)).thenReturn(null);
        try (MockedStatic<SecurityUtils> security = mockStatic(SecurityUtils.class))
        {
            security.when(() -> SecurityUtils.hasPermi("lab:usage:query")).thenReturn(true);
            security.when(() -> SecurityUtils.hasPermi("lab:repair:query")).thenReturn(true);
            security.when(() -> SecurityUtils.hasPermi("lab:notification:list")).thenReturn(true);
            ReservationTraceVo result = service().trace(7L, 9L, false);
            assertThat(result.repair()).isNull();
            verify(mapper).history(7L, null, 21);
            verify(mapper).notifications(7L, 12L, null, 9L, 21);
        }
    }
}
