package com.ruoyi.web.controller.lab;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.lab.domain.HazardSeverity;
import com.ruoyi.lab.domain.HazardStatus;
import com.ruoyi.lab.dto.CreateHazardCommand;
import com.ruoyi.lab.dto.ReviewRectificationCommand;
import com.ruoyi.lab.dto.SubmitRectificationCommand;
import com.ruoyi.lab.service.HazardService;
import com.ruoyi.lab.service.RectificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/lab/hazards")
public class LabHazardController extends BaseController
{
    private final HazardService hazardService;
    private final RectificationService rectificationService;
    public LabHazardController(HazardService hazardService, RectificationService rectificationService)
    { this.hazardService = hazardService; this.rectificationService = rectificationService; }

    @PreAuthorize("@ss.hasPermi('lab:hazard:list')")
    @GetMapping public TableDataInfo list(HazardStatus status, HazardSeverity severity, Long ownerId)
    { startPage(); try { return getDataTable(hazardService.list(status, severity, ownerId)); } finally { clearPage(); } }

    @PreAuthorize("@ss.hasPermi('lab:hazard:list')")
    @GetMapping("/{id}") public AjaxResult get(@PathVariable @Positive Long id)
    { return success(hazardService.get(id)); }

    @PreAuthorize("@ss.hasPermi('lab:hazard:list')")
    @GetMapping("/{id}/rectifications") public AjaxResult rounds(@PathVariable @Positive Long id)
    { return success(hazardService.rectifications(id)); }

    @PreAuthorize("@ss.hasPermi('lab:hazard:add')")
    @Log(title="隐患登记", businessType=BusinessType.INSERT)
    @PostMapping public ResponseEntity<AjaxResult> create(@Valid @RequestBody CreateHazardCommand command)
    { return ResponseEntity.status(HttpStatus.CREATED).body(success(
            String.valueOf(hazardService.create(command, getUserId(), getUsername())))); }

    @PreAuthorize("@ss.hasPermi('lab:hazard:rectify')")
    @PostMapping("/{id}/start-rectification") public AjaxResult start(@PathVariable @Positive Long id)
    { rectificationService.start(id, getUserId(), getUsername()); return success(); }

    @PreAuthorize("@ss.hasPermi('lab:hazard:rectify')")
    @PostMapping("/{id}/rectifications") public ResponseEntity<AjaxResult> submit(
            @PathVariable @Positive Long id, @Valid @RequestBody SubmitRectificationCommand command)
    { return ResponseEntity.status(HttpStatus.CREATED).body(success(String.valueOf(
            rectificationService.submit(id, command, getUserId(), getUsername())))); }

    @PreAuthorize("@ss.hasPermi('lab:hazard:review')")
    @PostMapping("/{hazardId}/rectifications/{roundId}/review") public AjaxResult review(
            @PathVariable @Positive Long hazardId, @PathVariable @Positive Long roundId,
            @Valid @RequestBody ReviewRectificationCommand command)
    { rectificationService.review(hazardId, roundId, command, getUserId(), getUsername()); return success(); }
}
