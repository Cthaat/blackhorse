package com.ruoyi.web.controller.lab;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.lab.restriction.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/lab/restrictions")
public class LabRestrictionController extends LabBaseController
{
    private final RestrictionService service;
    public LabRestrictionController(RestrictionService service) { this.service=service; }

    @GetMapping
    @PreAuthorize("@ss.hasAnyPermi('lab:restriction:mine,lab:restriction:list')")
    public TableDataInfo list(@RequestParam(defaultValue="true") boolean mine,
            @RequestParam(required=false) @Positive Long laboratoryId,
            @RequestParam(required=false) @Positive Long userId, @RequestParam(required=false) String status)
    {
        startPage();
        try { return getDataTable(service.list(mine,laboratoryId,userId,status)); }
        finally { clearPage(); }
    }

    @GetMapping("/{id}")
    @PreAuthorize("@ss.hasAnyPermi('lab:restriction:mine,lab:restriction:list,lab:restriction:review')")
    public AjaxResult detail(@PathVariable @Positive Long id) { return success(service.detail(id)); }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('lab:restriction:manual')")
    @Log(title="手动预约限制",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult manual(@Valid @RequestBody RestrictionCommands.Manual command) { return success(service.manual(command)); }

    @PostMapping("/{id}/commands/revoke")
    @PreAuthorize("@ss.hasPermi('lab:restriction:revoke')")
    @Log(title="解除预约限制",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult revoke(@PathVariable @Positive Long id, @Valid @RequestBody RestrictionCommands.Reason command)
    { return success(service.revoke(id,command.reason())); }

    @PostMapping("/{id}/appeal")
    @PreAuthorize("@ss.hasPermi('lab:restriction:appeal')")
    @Log(title="预约限制申诉",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult appeal(@PathVariable @Positive Long id, @Valid @RequestBody RestrictionCommands.Appeal command)
    { return success(service.appeal(id,command)); }

    @PostMapping("/{id}/appeal/decision")
    @PreAuthorize("@ss.hasPermi('lab:restriction:review')")
    @Log(title="审核预约限制申诉",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult review(@PathVariable @Positive Long id, @Valid @RequestBody RestrictionCommands.Decision command)
    { return success(service.review(id,command)); }

    @GetMapping("/rules")
    @PreAuthorize("@ss.hasPermi('lab:restriction:rule')")
    public AjaxResult rules(@RequestParam @Positive Long laboratoryId) { return success(service.rules(laboratoryId)); }

    @PostMapping("/rules")
    @PreAuthorize("@ss.hasPermi('lab:restriction:rule')")
    @Log(title="发布爽约限制规则",businessType=BusinessType.INSERT)
    public AjaxResult publish(@Valid @RequestBody RestrictionCommands.Rule command) { return success(service.publish(command)); }
}
