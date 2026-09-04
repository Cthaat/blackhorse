package com.ruoyi.web.controller.lab;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.lab.service.StatusHistoryQueryService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Authorized read-only access to immutable business status history. */
@Validated
@RestController
@RequestMapping("/lab/status-histories")
public class LabStatusHistoryController extends BaseController
{
    private final StatusHistoryQueryService historyQueryService;

    public LabStatusHistoryController(StatusHistoryQueryService historyQueryService)
    {
        this.historyQueryService = historyQueryService;
    }

    @PreAuthorize("@ss.hasAnyPermi('lab:laboratory:query,lab:device:query,"
            + "lab:qualification:query,lab:qualification:mine,lab:reservation:list,"
            + "lab:reservation:mine,lab:repair:query,lab:inspection:plan:list,"
            + "lab:inspection:task:list,lab:hazard:list')")
    @GetMapping
    public AjaxResult list(@RequestParam @NotBlank @Size(max = 32) String objectType,
            @RequestParam @Positive Long objectId)
    {
        return success(historyQueryService.list(objectType, objectId, getUserId()));
    }
}
