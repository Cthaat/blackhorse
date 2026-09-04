package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import com.ruoyi.lab.domain.LabInspectionPlan;
import com.ruoyi.lab.domain.LabInspectionPlanItem;
import com.ruoyi.lab.mapper.LabInspectionPlanItemMapper;
import com.ruoyi.lab.mapper.LabInspectionPlanMapper;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.service.impl.InspectionPlanServiceImpl;
import com.ruoyi.lab.vo.InspectionPlanDetailVo;
import org.junit.jupiter.api.Test;

class InspectionPlanDetailServiceTest
{
    @Test
    void returnsActiveItemsAfterCheckingThePlansLaboratoryScope()
    {
        LabInspectionPlanMapper planMapper = mock(LabInspectionPlanMapper.class);
        LabInspectionPlanItemMapper itemMapper = mock(LabInspectionPlanItemMapper.class);
        LabObjectPermissionService permissions = mock(LabObjectPermissionService.class);
        LabInspectionPlan plan = new LabInspectionPlan();
        plan.setId(10L);
        plan.setLaboratoryId(99L);
        LabInspectionPlanItem first = item(101L, "POWER", 1);
        LabInspectionPlanItem second = item(102L, "FIRE", 2);
        when(planMapper.selectActiveById(10L)).thenReturn(plan);
        when(itemMapper.selectByPlan(10L)).thenReturn(List.of(first, second));
        InspectionPlanService service = new InspectionPlanServiceImpl(planMapper, itemMapper,
                mock(LabDataScopeService.class), permissions,
                mock(LabStatusHistoryService.class), Clock.fixed(
                        Instant.parse("2026-09-03T04:00:00Z"), ZoneId.of("Asia/Shanghai")));

        InspectionPlanDetailVo detail = service.get(10L);

        assertThat(detail.plan()).isSameAs(plan);
        assertThat(detail.items()).containsExactly(first, second);
        verify(permissions).assertLaboratoryReadable(99L);
        verify(itemMapper).selectByPlan(10L);
    }

    private static LabInspectionPlanItem item(Long id, String code, int order)
    {
        LabInspectionPlanItem item = new LabInspectionPlanItem();
        item.setId(id);
        item.setPlanId(10L);
        item.setItemCode(code);
        item.setContent(code + " check");
        item.setSortOrder(order);
        item.setEnabled("1");
        return item;
    }
}
