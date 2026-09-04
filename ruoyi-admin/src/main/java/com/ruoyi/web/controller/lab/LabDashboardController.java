package com.ruoyi.web.controller.lab;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.lab.dto.DashboardQueryDto;
import com.ruoyi.lab.service.DashboardService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Role-aware laboratory workbench endpoint. */
@RestController
@RequestMapping("/lab/dashboard")
public class LabDashboardController extends BaseController
{
    private final DashboardService dashboardService;

    public LabDashboardController(DashboardService dashboardService)
    {
        this.dashboardService = dashboardService;
    }

    @PreAuthorize("@ss.hasPermi('lab:dashboard:view')")
    @GetMapping("/summary")
    public AjaxResult summary(DashboardQueryDto query)
    {
        return success(dashboardService.snapshot(getUserId(), query));
    }

    /** Compatibility path retained for clients built before the approved summary contract. */
    @PreAuthorize("@ss.hasPermi('lab:dashboard:view')")
    @GetMapping
    public AjaxResult snapshot(DashboardQueryDto query)
    {
        return success(dashboardService.snapshot(getUserId(), query));
    }
}
