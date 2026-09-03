package com.ruoyi.web.controller.lab;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.lab.domain.InspectionTaskStatus;
import com.ruoyi.lab.dto.RecordInspectionItemCommand;
import com.ruoyi.lab.service.InspectionTaskService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/lab/inspection-tasks")
public class LabInspectionTaskController extends BaseController
{
    private final InspectionTaskService service;
    public LabInspectionTaskController(InspectionTaskService service) { this.service = service; }

    @PreAuthorize("@ss.hasPermi('lab:inspection:task:list')")
    @GetMapping public TableDataInfo list(InspectionTaskStatus status, Long assigneeId)
    { startPage(); try { return getDataTable(service.list(status, assigneeId)); } finally { clearPage(); } }

    @PreAuthorize("@ss.hasPermi('lab:inspection:task:list')")
    @GetMapping("/{id}") public AjaxResult get(@PathVariable @Positive Long id)
    { return success(service.get(id)); }

    @PreAuthorize("@ss.hasPermi('lab:inspection:task:list')")
    @GetMapping("/{id}/items") public AjaxResult items(@PathVariable @Positive Long id)
    { return success(service.items(id)); }

    @PreAuthorize("@ss.hasPermi('lab:inspection:task:execute')")
    @Log(title="巡检任务", businessType=BusinessType.UPDATE)
    @PostMapping("/{id}/start") public AjaxResult start(@PathVariable @Positive Long id)
    { service.start(id, getUserId(), getUsername()); return success(); }

    @PreAuthorize("@ss.hasPermi('lab:inspection:task:execute')")
    @PutMapping("/{taskId}/items/{itemId}") public AjaxResult record(
            @PathVariable @Positive Long taskId, @PathVariable @Positive Long itemId,
            @Valid @RequestBody RecordInspectionItemCommand command)
    { service.recordItem(taskId, itemId, command, getUserId()); return success(); }

    @PreAuthorize("@ss.hasPermi('lab:inspection:task:execute')")
    @PostMapping("/{id}/complete") public AjaxResult complete(@PathVariable @Positive Long id)
    { service.complete(id, getUserId(), getUsername()); return success(); }
}
