package com.ruoyi.web.controller.lab;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.service.ReservationTraceService;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/lab/reservations")
public class LabReservationTraceController extends LabBaseController
{
    private final ReservationTraceService traceService;

    public LabReservationTraceController(ReservationTraceService traceService)
    {
        this.traceService = traceService;
    }

    @PreAuthorize("@ss.hasAnyPermi('lab:reservation:list,lab:reservation:mine')")
    @GetMapping("/{id}/trace")
    public AjaxResult trace(@PathVariable @Positive Long id)
    {
        return success(traceService.trace(id, getUserId(), SecurityUtils.hasPermi("lab:reservation:list")));
    }
}
