package com.ruoyi.web.controller.lab;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.lab.dto.CheckOutCommand;
import com.ruoyi.lab.dto.ReturnUsageCommand;
import com.ruoyi.lab.dto.UsageQueryDto;
import com.ruoyi.lab.service.UsageCommandService;
import com.ruoyi.lab.service.UsageQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Secured checkout, return and scoped usage query API. */
@Validated
@RestController
@RequestMapping("/lab/usage-records")
public class LabUsageRecordController extends BaseController
{
    private final UsageCommandService commandService;
    private final UsageQueryService queryService;

    public LabUsageRecordController(UsageCommandService commandService,
            UsageQueryService queryService)
    {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PreAuthorize("@ss.hasPermi('lab:usage:list')")
    @GetMapping
    public TableDataInfo list(@Valid UsageQueryDto query)
    {
        startPage();
        try
        {
            return getDataTable(queryService.list(query, getUserId()));
        }
        finally
        {
            clearPage();
        }
    }

    @PreAuthorize("@ss.hasPermi('lab:usage:query')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable @Positive Long id)
    {
        return success(queryService.detail(id, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:usage:checkout')")
    @Log(title = "设备领用", businessType = BusinessType.INSERT)
    @PostMapping("/check-out")
    public AjaxResult checkOut(@Valid @RequestBody CheckOutCommand command)
    {
        return success(commandService.checkOut(command, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:usage:return')")
    @Log(title = "设备归还", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/return")
    public AjaxResult returnUsage(@PathVariable @Positive Long id,
            @Valid @RequestBody ReturnUsageCommand command)
    {
        return success(commandService.returnUsage(id, command, getUserId()));
    }
}
