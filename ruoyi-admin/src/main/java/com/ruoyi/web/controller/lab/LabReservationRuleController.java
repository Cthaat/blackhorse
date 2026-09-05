package com.ruoyi.web.controller.lab;

import java.time.LocalDate;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.lab.dto.*;
import com.ruoyi.lab.service.ReservationRuleService;
import com.ruoyi.lab.service.ReservationRuleQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/lab/reservation-rules")
public class LabReservationRuleController extends LabBaseController
{
    private final ReservationRuleService rules;
    private final ReservationRuleQueryService queries;
    public LabReservationRuleController(ReservationRuleService rules, ReservationRuleQueryService queries)
    {
        this.rules = rules;
        this.queries = queries;
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermi('lab:device:edit')")
    public TableDataInfo history(@RequestParam @Positive Long deviceId)
    {
        startPage();
        try { return getDataTable(rules.history(deviceId)); }
        finally { clearPage(); }
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('lab:device:edit')")
    @Log(title = "预约规则草稿", businessType = BusinessType.INSERT)
    public AjaxResult create(@Valid @RequestBody ReservationRuleDraftDto request) { return success(rules.create(request)); }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('lab:device:edit')")
    @Log(title = "预约规则草稿", businessType = BusinessType.UPDATE)
    public AjaxResult edit(@PathVariable @Positive Long id, @Valid @RequestBody ReservationRuleDraftDto request)
    {
        return success(rules.edit(id, request));
    }

    @PostMapping("/{id}/commands/publish")
    @PreAuthorize("@ss.hasPermi('lab:device:edit')")
    @Log(title = "预约规则发布", businessType = BusinessType.UPDATE)
    public AjaxResult publish(@PathVariable @Positive Long id, @Valid @RequestBody ReservationRuleCommandDto request)
    {
        return success(rules.publish(id, request.expectedVersion()));
    }

    @PostMapping("/{id}/commands/retire")
    @PreAuthorize("@ss.hasPermi('lab:device:edit')")
    @Log(title = "预约规则停用", businessType = BusinessType.UPDATE)
    public AjaxResult retire(@PathVariable @Positive Long id, @Valid @RequestBody ReservationRuleCommandDto request)
    {
        return success(rules.retire(id, request.expectedVersion()));
    }

    @GetMapping("/{id}/impact")
    @PreAuthorize("@ss.hasAnyPermi('lab:device:edit') and @ss.hasPermi('lab:reservation:list')")
    public TableDataInfo impact(@PathVariable @Positive Long id)
    {
        startPage();
        try { return getDataTable(queries.impact(id)); }
        finally { clearPage(); }
    }

    @GetMapping("/calendar")
    @PreAuthorize("@ss.hasAnyPermi('lab:reservation:apply,lab:reservation:delegate,lab:reservation:list,lab:device:edit')")
    public AjaxResult calendar(@RequestParam @Positive Long deviceId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to)
    {
        return success(queries.calendar(deviceId, from, to));
    }

    @PostMapping("/simulation")
    @PreAuthorize("@ss.hasAnyPermi('lab:reservation:apply,lab:reservation:delegate,lab:device:edit')")
    public AjaxResult simulate(@RequestParam(required = false) @Positive Long ruleId,
            @Valid @RequestBody ReservationApplyDto request)
    {
        if (ruleId != null && !com.ruoyi.common.utils.SecurityUtils.hasPermi("lab:device:edit"))
        {
            throw new com.ruoyi.lab.exception.LabBusinessException(com.ruoyi.lab.exception.LabErrorCode.ACCESS_DENIED,
                    "无规则管理权限");
        }
        return success(queries.simulate(request, ruleId));
    }
}
