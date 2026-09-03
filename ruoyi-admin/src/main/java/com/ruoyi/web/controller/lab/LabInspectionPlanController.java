package com.ruoyi.web.controller.lab;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.lab.domain.InspectionPlanStatus;
import com.ruoyi.lab.dto.InspectionPlanCommand;
import com.ruoyi.lab.service.InspectionPlanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/lab/inspection-plans")
public class LabInspectionPlanController extends BaseController
{
    private final InspectionPlanService service;
    public LabInspectionPlanController(InspectionPlanService service) { this.service = service; }

    @PreAuthorize("@ss.hasPermi('lab:inspection:plan:list')")
    @GetMapping public TableDataInfo list(InspectionPlanStatus status, String keyword)
    { startPage(); try { return getDataTable(service.list(status, keyword)); } finally { clearPage(); } }

    @PreAuthorize("@ss.hasPermi('lab:inspection:plan:list')")
    @GetMapping("/{id}") public AjaxResult get(@PathVariable @Positive Long id)
    { return success(service.get(id)); }

    @PreAuthorize("@ss.hasPermi('lab:inspection:plan:add')")
    @Log(title="巡检计划", businessType=BusinessType.INSERT)
    @PostMapping public ResponseEntity<AjaxResult> create(@Valid @RequestBody InspectionPlanCommand command)
    { return ResponseEntity.status(HttpStatus.CREATED).body(success(service.create(command, getUserId(), getUsername()))); }

    @PreAuthorize("@ss.hasPermi('lab:inspection:plan:edit')")
    @Log(title="巡检计划", businessType=BusinessType.UPDATE)
    @PutMapping("/{id}") public AjaxResult update(@PathVariable @Positive Long id,
            @RequestParam @Min(0) Integer expectedVersion, @Valid @RequestBody InspectionPlanCommand command)
    { service.update(id, expectedVersion, command, getUserId(), getUsername()); return success(); }

    @PreAuthorize("@ss.hasPermi('lab:inspection:plan:enable')")
    @PostMapping("/{id}/enable") public AjaxResult enable(@PathVariable @Positive Long id)
    { service.enable(id, getUserId(), getUsername()); return success(); }

    @PreAuthorize("@ss.hasPermi('lab:inspection:plan:enable')")
    @PostMapping("/{id}/disable") public AjaxResult disable(@PathVariable @Positive Long id)
    { service.disable(id, getUserId(), getUsername()); return success(); }
}
