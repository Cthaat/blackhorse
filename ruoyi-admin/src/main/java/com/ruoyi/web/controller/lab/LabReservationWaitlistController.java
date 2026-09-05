package com.ruoyi.web.controller.lab;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.lab.dto.ReservationApplyDto;
import com.ruoyi.lab.dto.ReservationRuleCommandDto;
import com.ruoyi.lab.service.ReservationWaitlistService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/lab/reservation-waitlist")
public class LabReservationWaitlistController extends LabBaseController
{
    private final ReservationWaitlistService waitlist;
    public LabReservationWaitlistController(ReservationWaitlistService waitlist) { this.waitlist = waitlist; }

    @GetMapping
    @PreAuthorize("@ss.hasAnyPermi('lab:reservation:mine,lab:reservation:apply')")
    public TableDataInfo mine(@RequestParam(required = false) @Positive Long deviceId,
            @RequestParam(required = false) String status)
    {
        startPage();
        try { return getDataTable(waitlist.mine(deviceId, status)); }
        finally { clearPage(); }
    }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('lab:reservation:apply')")
    @Log(title = "预约候补登记", businessType = BusinessType.INSERT)
    public AjaxResult join(@RequestHeader("X-Idempotency-Key") @NotBlank String key,
            @Valid @RequestBody ReservationApplyDto request)
    {
        return success(waitlist.join(key, request));
    }

    @PostMapping("/{id}/commands/confirm")
    @PreAuthorize("@ss.hasPermi('lab:reservation:apply')")
    @Log(title = "预约候补确认", businessType = BusinessType.UPDATE)
    public AjaxResult confirm(@PathVariable @Positive Long id,
            @Valid @RequestBody ReservationRuleCommandDto request)
    {
        return success(waitlist.confirm(id, request.expectedVersion()));
    }

    @PostMapping("/{id}/commands/cancel")
    @PreAuthorize("@ss.hasPermi('lab:reservation:cancel')")
    @Log(title = "预约候补退出", businessType = BusinessType.UPDATE)
    public AjaxResult cancel(@PathVariable @Positive Long id,
            @Valid @RequestBody ReservationRuleCommandDto request)
    {
        return success(waitlist.cancel(id, request.expectedVersion()));
    }
}
