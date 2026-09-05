package com.ruoyi.web.controller.lab;

import java.util.List;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.lab.domain.LaboratoryStatus;
import com.ruoyi.lab.dto.LaboratoryCreateDto;
import com.ruoyi.lab.dto.LaboratoryUpdateDto;
import com.ruoyi.lab.service.LaboratoryService;
import com.ruoyi.lab.vo.LaboratoryVo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Laboratory HTTP API. */
@Validated
@RestController
@RequestMapping("/lab/laboratories")
public class LabLaboratoryController extends LabBaseController
{
    private final LaboratoryService laboratoryService;

    public LabLaboratoryController(LaboratoryService laboratoryService)
    {
        this.laboratoryService = laboratoryService;
    }

    @PreAuthorize("@ss.hasAnyPermi('lab:laboratory:list,lab:restriction:manual,lab:restriction:rule,lab:restriction:list')")
    @GetMapping("/list")
    public TableDataInfo list(
            @RequestParam(required = false) LaboratoryStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "createTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection)
    {
        startPage();
        try
        {
            List<LaboratoryVo> laboratories = laboratoryService.list(status, keyword, sortBy, sortDirection);
            return getDataTable(laboratories);
        }
        finally
        {
            clearPage();
        }
    }

    @PreAuthorize("@ss.hasPermi('lab:laboratory:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable @Positive Long id)
    {
        return success(laboratoryService.getById(id));
    }

    @PreAuthorize("@ss.hasPermi('lab:laboratory:add')")
    @Log(title = "实验室", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody LaboratoryCreateDto input)
    {
        return success(laboratoryService.create(input, getUsername(), getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:laboratory:edit')")
    @Log(title = "实验室", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public AjaxResult edit(@PathVariable @Positive Long id,
            @Valid @RequestBody LaboratoryUpdateDto input)
    {
        laboratoryService.update(id, input, getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:laboratory:status')")
    @Log(title = "实验室状态", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/commands/enable")
    public AjaxResult enable(@PathVariable @Positive Long id,
            @Valid @RequestBody StatusReasonCommand command)
    {
        laboratoryService.enable(id, command.getReason(), getUserId());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:laboratory:status')")
    @Log(title = "实验室状态", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/commands/disable")
    public AjaxResult disable(@PathVariable @Positive Long id,
            @Valid @RequestBody StatusReasonCommand command)
    {
        laboratoryService.disable(id, command.getReason(), getUserId());
        return success();
    }

    /** Reason-only command body; actor identity always comes from the login context. */
    public static class StatusReasonCommand
    {
        @NotBlank
        @Size(max = 500)
        private String reason;

        public String getReason()
        {
            return reason;
        }

        public void setReason(String reason)
        {
            this.reason = reason;
        }
    }
}
