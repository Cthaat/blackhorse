package com.ruoyi.web.controller.lab;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.lab.maintenance.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/lab/maintenance")
public class LabMaintenanceController extends LabBaseController
{
    private final MaintenanceService service;
    private final MaintenanceExecutionService execution;
    public LabMaintenanceController(MaintenanceService service,MaintenanceExecutionService execution) { this.service=service;this.execution=execution; }
    @GetMapping("/plans") @PreAuthorize("@ss.hasPermi('lab:maintenance:list')")
    public TableDataInfo plans(@RequestParam(required=false) @Positive Long deviceId,@RequestParam(required=false) Boolean enabled,@RequestParam(required=false) String due)
    { startPage();try{return getDataTable(service.plans(deviceId,enabled,due));}finally{clearPage();} }
    @GetMapping("/plans/{id}") @PreAuthorize("@ss.hasPermi('lab:maintenance:list')")
    public AjaxResult detail(@PathVariable @Positive Long id) { return success(service.detail(id)); }
    @PostMapping("/plans") @PreAuthorize("@ss.hasPermi('lab:maintenance:edit')")
    @Log(title="维护计划创建",businessType=BusinessType.INSERT)
    public AjaxResult create(@Valid @RequestBody MaintenanceCommands.Plan command) { return success(service.create(command)); }
    @PutMapping("/plans/{id}") @PreAuthorize("@ss.hasPermi('lab:maintenance:edit')")
    @Log(title="维护计划版本发布",businessType=BusinessType.UPDATE)
    public AjaxResult edit(@PathVariable @Positive Long id,@Valid @RequestBody MaintenanceCommands.Plan command) { return success(service.edit(id,command)); }
    @PostMapping("/plans/{id}/commands/toggle") @PreAuthorize("@ss.hasPermi('lab:maintenance:edit')")
    @Log(title="维护计划启停",businessType=BusinessType.UPDATE)
    public AjaxResult toggle(@PathVariable @Positive Long id,@Valid @RequestBody MaintenanceCommands.Toggle command) { return success(service.toggle(id,command)); }
    @GetMapping("/cycles") @PreAuthorize("@ss.hasPermi('lab:maintenance:list')")
    public TableDataInfo cycles(@RequestParam(required=false) @Positive Long deviceId,@RequestParam(required=false) String status)
    {startPage();try{return getDataTable(service.cycles(deviceId,status));}finally{clearPage();}}
    @PostMapping("/cycles/{id}/commands/window") @PreAuthorize("@ss.hasPermi('lab:maintenance:schedule')")
    @Log(title="维护窗口安排",businessType=BusinessType.UPDATE)
    public AjaxResult window(@PathVariable @Positive Long id,@Valid @RequestBody MaintenanceCommands.Window command) {return success(service.schedule(id,command));}
    @PostMapping("/cycles/{id}/commands/start") @PreAuthorize("@ss.hasPermi('lab:maintenance:start')")
    @Log(title="维护周期启动",businessType=BusinessType.UPDATE)
    public AjaxResult start(@PathVariable @Positive Long id,@Valid @RequestBody MaintenanceCommands.Start command) {return success(execution.start(id,command));}
}
