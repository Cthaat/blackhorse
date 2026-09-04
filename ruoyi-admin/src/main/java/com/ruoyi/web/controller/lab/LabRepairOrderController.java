package com.ruoyi.web.controller.lab;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.lab.dto.AcceptRepairCommand;
import com.ruoyi.lab.dto.AssignRepairCommand;
import com.ruoyi.lab.dto.RepairQueryDto;
import com.ruoyi.lab.dto.ReportFaultCommand;
import com.ruoyi.lab.dto.SubmitRepairResultCommand;
import com.ruoyi.lab.service.RepairOrderService;
import com.ruoyi.lab.service.RepairQueryService;
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

/** Repair reporting, assignment, processing and acceptance API. */
@Validated
@RestController
@RequestMapping("/lab/repair-orders")
public class LabRepairOrderController extends BaseController
{
    private final RepairOrderService orderService;
    private final RepairQueryService queryService;

    public LabRepairOrderController(RepairOrderService orderService,
            RepairQueryService queryService)
    {
        this.orderService = orderService;
        this.queryService = queryService;
    }

    @PreAuthorize("@ss.hasPermi('lab:repair:list')")
    @GetMapping
    public TableDataInfo list(@Valid RepairQueryDto query)
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

    @PreAuthorize("@ss.hasPermi('lab:repair:query')")
    @GetMapping("/{id}")
    public AjaxResult detail(@PathVariable @Positive Long id)
    {
        return success(queryService.detail(id, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:repair:report')")
    @Log(title = "设备报修", businessType = BusinessType.INSERT)
    @PostMapping("/report")
    public AjaxResult report(@Valid @RequestBody ReportFaultCommand command)
    {
        return success(orderService.reportFault(command, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:repair:assign')")
    @Log(title = "维修分派", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/assign")
    public AjaxResult assign(@PathVariable @Positive Long id,
            @Valid @RequestBody AssignRepairCommand command)
    {
        return success(orderService.assign(id, command, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:repair:process')")
    @Log(title = "开始维修", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/start")
    public AjaxResult start(@PathVariable @Positive Long id)
    {
        return success(orderService.start(id, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:repair:process')")
    @Log(title = "提交维修结果", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/submit-result")
    public AjaxResult submitResult(@PathVariable @Positive Long id,
            @Valid @RequestBody SubmitRepairResultCommand command)
    {
        return success(orderService.submitResult(id, command, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:repair:accept')")
    @Log(title = "维修验收", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/accept")
    public AjaxResult accept(@PathVariable @Positive Long id,
            @Valid @RequestBody AcceptRepairCommand command)
    {
        return success(orderService.accept(id, command, getUserId()));
    }
}
