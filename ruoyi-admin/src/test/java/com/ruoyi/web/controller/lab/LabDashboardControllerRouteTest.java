package com.ruoyi.web.controller.lab;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import com.ruoyi.lab.dto.DashboardQueryDto;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

class LabDashboardControllerRouteTest
{
    @Test
    void exposesSummaryAsTheApprovedPathAndKeepsTheLegacyPath()
            throws Exception
    {
        Method summary = LabDashboardController.class.getDeclaredMethod(
                "summary", DashboardQueryDto.class);
        Method legacy = LabDashboardController.class.getDeclaredMethod(
                "snapshot", DashboardQueryDto.class);

        assertThat(summary.getAnnotation(GetMapping.class).value())
                .containsExactly("/summary");
        assertThat(legacy.getAnnotation(GetMapping.class).value()).isEmpty();
        assertThat(summary.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@ss.hasPermi('lab:dashboard:view')");
        assertThat(legacy.getAnnotation(PreAuthorize.class).value())
                .isEqualTo("@ss.hasPermi('lab:dashboard:view')");
    }
}
