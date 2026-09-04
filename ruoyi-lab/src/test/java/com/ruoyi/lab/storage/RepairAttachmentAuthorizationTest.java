package com.ruoyi.lab.storage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ruoyi.lab.domain.LabRepairOrder;
import com.ruoyi.lab.domain.LabHazard;
import com.ruoyi.lab.domain.LabRectification;
import com.ruoyi.lab.domain.RepairStatus;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabHazardMapper;
import com.ruoyi.lab.mapper.LabLaboratoryMapper;
import com.ruoyi.lab.mapper.LabQualificationMapper;
import com.ruoyi.lab.mapper.LabRectificationMapper;
import com.ruoyi.lab.mapper.LabRepairOrderMapper;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.vo.RepairOrderVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RepairAttachmentAuthorizationTest
{
    @Mock private LabObjectPermissionService objectPermissionService;
    @Mock private LabDataScopeService dataScopeService;
    @Mock private LabLaboratoryMapper laboratoryMapper;
    @Mock private LabDeviceMapper deviceMapper;
    @Mock private LabQualificationMapper qualificationMapper;
    @Mock private LabHazardMapper hazardMapper;
    @Mock private LabRectificationMapper rectificationMapper;
    @Mock private LabRepairOrderMapper repairOrderMapper;

    private LabAttachmentObjectAuthorizer authorizer;

    @BeforeEach
    void setUp()
    {
        authorizer = new LabAttachmentObjectAuthorizer(objectPermissionService,
                dataScopeService, laboratoryMapper, deviceMapper, qualificationMapper,
                hazardMapper, rectificationMapper, repairOrderMapper);
    }

    @Test
    void readsOnlyRepairsVisibleThroughTheRepairObjectScope()
    {
        LabDataScope scope = new LabDataScope(41L, false, java.util.Set.of());
        when(objectPermissionService.currentUserId()).thenReturn(41L);
        when(dataScopeService.resolveCurrentScope()).thenReturn(scope);
        when(repairOrderMapper.selectActiveById(7L)).thenReturn(order(7L, 13L, 41L, 55L,
                RepairStatus.WAIT_REPAIR));
        when(repairOrderMapper.selectScopedDetail(7L, 41L, scope))
                .thenReturn(org.mockito.Mockito.mock(RepairOrderVo.class));

        authorizer.assertReadable("REPAIR_ORDER", 7L);

        verify(repairOrderMapper).selectScopedDetail(7L, 41L, scope);
    }

    @Test
    void assignedWorkerCanManageOnlyDuringTheProcessingStage()
    {
        when(objectPermissionService.currentUserId()).thenReturn(55L);
        when(repairOrderMapper.selectByIdForUpdate(7L)).thenReturn(order(7L, 13L, 41L, 55L,
                RepairStatus.IN_PROGRESS));

        authorizer.lockAndAssertManageable("REPAIR_ORDER", 7L);

        when(repairOrderMapper.selectByIdForUpdate(8L)).thenReturn(order(8L, 13L, 41L, 55L,
                RepairStatus.WAIT_ACCEPTANCE));
        assertThatThrownBy(() -> authorizer.lockAndAssertManageable("REPAIR_ORDER", 8L))
                .isInstanceOf(LabBusinessException.class);
    }

    @Test
    void managerCanManageInScopeButClosedOrdersRemainReadOnly()
    {
        when(objectPermissionService.currentUserId()).thenReturn(61L);
        when(repairOrderMapper.selectByIdForUpdate(7L)).thenReturn(order(7L, 13L, 41L, 55L,
                RepairStatus.WAIT_ACCEPTANCE));
        when(repairOrderMapper.countActiveUserRole(61L, "lab_manager")).thenReturn(1);

        authorizer.lockAndAssertManageable("REPAIR_ORDER", 7L);
        verify(objectPermissionService).assertDeviceManageable(13L);

        when(repairOrderMapper.selectByIdForUpdate(8L)).thenReturn(order(8L, 13L, 41L, 55L,
                RepairStatus.CLOSED));
        assertThatThrownBy(() -> authorizer.lockAndAssertManageable("REPAIR_ORDER", 8L))
                .isInstanceOf(LabBusinessException.class);
    }

    @Test
    void rectificationSubmitterCanReadAndManageOwnPendingAttachments()
    {
        LabRectification round = new LabRectification();
        round.setId(9L);
        round.setHazardId(7L);
        round.setSubmitterId(41L);
        round.setDelFlag("0");
        LabHazard hazard = new LabHazard();
        hazard.setId(7L);
        hazard.setOwnerId(41L);
        hazard.setDelFlag("0");
        when(objectPermissionService.currentUserId()).thenReturn(41L);
        when(rectificationMapper.selectById(9L)).thenReturn(round);
        when(hazardMapper.selectActiveById(7L)).thenReturn(hazard);

        authorizer.assertReadable("RECTIFICATION", 9L);
        authorizer.lockAndAssertManageable("RECTIFICATION", 9L);
    }

    private static LabRepairOrder order(Long id, Long deviceId, Long reporterId,
            Long assigneeId, RepairStatus status)
    {
        LabRepairOrder order = new LabRepairOrder();
        order.setId(id);
        order.setDeviceId(deviceId);
        order.setReporterId(reporterId);
        order.setAssigneeId(assigneeId);
        order.setStatus(status);
        order.setDelFlag("0");
        return order;
    }
}
