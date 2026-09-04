package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Set;
import com.ruoyi.lab.domain.HazardSeverity;
import com.ruoyi.lab.domain.HazardStatus;
import com.ruoyi.lab.domain.HazardTargetType;
import com.ruoyi.lab.domain.LabHazard;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabHazardMapper;
import com.ruoyi.lab.mapper.LabInspectionTaskMapper;
import com.ruoyi.lab.mapper.LabRectificationMapper;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.service.impl.HazardAffectedDeviceResolver;
import com.ruoyi.lab.service.impl.HazardServiceImpl;
import org.junit.jupiter.api.Test;

class HazardOwnerVisibilityTest
{
    @Test
    void ownerCanReadOwnHazardOutsideLaboratoryScopeAndListUsesViewerId()
    {
        LabHazardMapper mapper = mock(LabHazardMapper.class);
        LabObjectPermissionService permissions = mock(LabObjectPermissionService.class);
        LabDataScopeService scopes = mock(LabDataScopeService.class);
        LabDataScope emptyScope = new LabDataScope(41L, false, Set.of());
        LabHazard hazard = new LabHazard();
        hazard.setId(7L);
        hazard.setTargetType(HazardTargetType.DEVICE);
        hazard.setTargetId(13L);
        hazard.setOwnerId(41L);
        hazard.setDelFlag("0");
        when(permissions.currentUserId()).thenReturn(41L);
        when(scopes.resolveCurrentScope()).thenReturn(emptyScope);
        when(mapper.selectActiveById(7L)).thenReturn(hazard);
        when(mapper.selectListByScope(emptyScope, 41L, null,
                HazardStatus.RECTIFYING, HazardSeverity.MAJOR)).thenReturn(List.of(hazard));
        HazardService service = new HazardServiceImpl(mapper, mock(LabRectificationMapper.class),
                mock(LabInspectionTaskMapper.class), mock(LabDeviceMapper.class),
                mock(HazardAffectedDeviceResolver.class), permissions, scopes,
                mock(LabStatusHistoryService.class), Clock.systemUTC());

        assertThat(service.get(7L)).isSameAs(hazard);
        assertThat(service.list(HazardStatus.RECTIFYING, HazardSeverity.MAJOR, null))
                .containsExactly(hazard);
        verify(permissions, never()).assertDeviceReadable(13L);
        verify(mapper).selectListByScope(emptyScope, 41L, null,
                HazardStatus.RECTIFYING, HazardSeverity.MAJOR);
    }
}
