package com.ruoyi.web.controller.lab;

import java.util.List;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.dto.ReservationApplyDto;
import com.ruoyi.lab.dto.ReservationCancelDto;
import com.ruoyi.lab.dto.ReservationDecisionDto;
import com.ruoyi.lab.dto.ReservationQueryDto;
import com.ruoyi.lab.service.ReservationApplyResult;
import com.ruoyi.lab.service.ReservationCommandService;
import com.ruoyi.lab.service.ReservationQueryService;
import com.ruoyi.lab.vo.ReservationVo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Reservation application, review, cancellation and scoped query API. */
@Validated
@RestController
@RequestMapping("/lab/reservations")
public class LabReservationController extends BaseController
{
    private final ReservationCommandService commandService;
    private final ReservationQueryService queryService;

    public LabReservationController(ReservationCommandService commandService,
            ReservationQueryService queryService)
    {
        this.commandService = commandService;
        this.queryService = queryService;
    }

    @PreAuthorize("@ss.hasPermi('lab:reservation:apply')")
    @Log(title = "实验室预约", businessType = BusinessType.INSERT)
    @PostMapping
    public ResponseEntity<AjaxResult> apply(
            @RequestHeader("X-Idempotency-Key") @NotBlank String idempotencyKey,
            @Valid @RequestBody ReservationApplyDto request)
    {
        ReservationApplyResult result = commandService.apply(getUserId(), idempotencyKey, request);
        HttpStatus status = result.replayed() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(AjaxResult.success(result.reservation()));
    }

    @PreAuthorize("@ss.hasAnyPermi('lab:reservation:list,lab:reservation:mine')")
    @GetMapping
    public TableDataInfo list(@Valid ReservationQueryDto query)
    {
        startPage();
        try
        {
            boolean managementView = SecurityUtils.hasPermi("lab:reservation:list");
            List<ReservationVo> reservations = queryService.list(query, getUserId(), managementView);
            return getDataTable(reservations);
        }
        finally
        {
            clearPage();
        }
    }

    @PreAuthorize("@ss.hasAnyPermi('lab:reservation:list,lab:reservation:mine')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable @Positive Long id)
    {
        return success(queryService.getById(id, getUserId(),
                SecurityUtils.hasPermi("lab:reservation:list")));
    }

    @PreAuthorize("@ss.hasPermi('lab:reservation:approve')")
    @Log(title = "预约批准", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/commands/approve")
    public AjaxResult approve(@PathVariable @Positive Long id,
            @Valid @RequestBody ReservationDecisionDto command)
    {
        return success(commandService.approve(id, command, getUserId(), getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('lab:reservation:reject')")
    @Log(title = "预约驳回", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/commands/reject")
    public AjaxResult reject(@PathVariable @Positive Long id,
            @Valid @RequestBody ReservationDecisionDto command)
    {
        return success(commandService.reject(id, command, getUserId(), getUsername()));
    }

    @PreAuthorize("@ss.hasPermi('lab:reservation:cancel')")
    @Log(title = "预约取消", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/commands/cancel")
    public AjaxResult cancel(@PathVariable @Positive Long id,
            @Valid @RequestBody ReservationCancelDto command)
    {
        return success(commandService.cancel(id, command, getUserId(), getUsername()));
    }
}
