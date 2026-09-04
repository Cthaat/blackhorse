package com.ruoyi.lab.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Set;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.lab.domain.LabReservation;
import com.ruoyi.lab.domain.LabRepairOrder;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabHazardMapper;
import com.ruoyi.lab.mapper.LabInspectionPlanMapper;
import com.ruoyi.lab.mapper.LabInspectionTaskMapper;
import com.ruoyi.lab.mapper.LabQualificationMapper;
import com.ruoyi.lab.mapper.LabRepairOrderMapper;
import com.ruoyi.lab.mapper.LabReservationMapper;
import com.ruoyi.lab.vo.RepairOrderVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class LabStatusHistoryObjectAuthorizerTest
{
    @Mock private LabObjectPermissionService objectPermissionService;
    @Mock private LabDataScopeService dataScopeService;
    @Mock private LabQualificationMapper qualificationMapper;
    @Mock private LabReservationMapper reservationMapper;
    @Mock private LabRepairOrderMapper repairOrderMapper;
    @Mock private LabInspectionPlanMapper inspectionPlanMapper;
    @Mock private LabInspectionTaskMapper inspectionTaskMapper;
    @Mock private LabHazardMapper hazardMapper;

    private LabStatusHistoryObjectAuthorizer authorizer;

    @BeforeEach
    void setUp()
    {
        authorizer = new LabStatusHistoryObjectAuthorizer(objectPermissionService,
                dataScopeService, qualificationMapper, reservationMapper, repairOrderMapper,
                inspectionPlanMapper, inspectionTaskMapper, hazardMapper);
    }

    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void rejectsSpoofedCurrentUserBeforeLookingUpTheObject()
    {
        when(objectPermissionService.currentUserId()).thenReturn(12L);

        assertThatThrownBy(() -> authorizer.assertReadable("REPAIR_ORDER", 7L, 13L))
                .isInstanceOfSatisfying(LabBusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                                .isEqualTo(LabErrorCode.ACCESS_DENIED));

        verify(repairOrderMapper, never()).selectActiveById(7L);
    }

    @Test
    void repairHistoryUsesTheSameScopedDetailVisibilityAsRepairQueries()
    {
        authenticate(12L, "lab:repair:query");
        LabDataScope scope = new LabDataScope(12L, false, Set.of(3L));
        when(objectPermissionService.currentUserId()).thenReturn(12L);
        when(dataScopeService.resolveCurrentScope()).thenReturn(scope);
        when(repairOrderMapper.selectActiveById(7L)).thenReturn(new LabRepairOrder());
        when(repairOrderMapper.selectScopedDetail(7L, 12L, scope))
                .thenReturn(org.mockito.Mockito.mock(RepairOrderVo.class));

        authorizer.assertReadable("repair_order", 7L, 12L);

        verify(repairOrderMapper).selectScopedDetail(7L, 12L, scope);
    }

    @Test
    void rejectsRepairHistoryWithoutRepairPermissionBeforeLookingUpTheObject()
    {
        authenticate(12L, "lab:hazard:list");
        when(objectPermissionService.currentUserId()).thenReturn(12L);

        assertThatThrownBy(() -> authorizer.assertReadable("REPAIR_ORDER", 7L, 12L))
                .isInstanceOfSatisfying(LabBusinessException.class,
                        error -> org.assertj.core.api.Assertions.assertThat(error.getErrorCode())
                                .isEqualTo(LabErrorCode.ACCESS_DENIED));

        verify(repairOrderMapper, never()).selectActiveById(7L);
    }

    @Test
    void reservationMinePermissionCanReadTheApplicantsOwnHistory()
    {
        authenticate(12L, "lab:reservation:mine");
        LabReservation reservation = new LabReservation();
        reservation.setApplicantId(12L);
        when(objectPermissionService.currentUserId()).thenReturn(12L);
        when(reservationMapper.selectActiveById(7L)).thenReturn(reservation);

        authorizer.assertReadable("RESERVATION", 7L, 12L);

        verify(reservationMapper).selectActiveById(7L);
        verify(objectPermissionService, never()).assertDeviceManageable(
                org.mockito.ArgumentMatchers.anyLong());
    }

    private static void authenticate(long userId, String... permissions)
    {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setUserName("tester");
        LoginUser loginUser = new LoginUser(userId, 1L, user, Set.of(permissions));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null,
                        loginUser.getAuthorities()));
    }
}
