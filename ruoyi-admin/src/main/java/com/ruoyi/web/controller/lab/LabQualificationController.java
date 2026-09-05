package com.ruoyi.web.controller.lab;

import java.util.List;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.domain.QualificationScopeType;
import com.ruoyi.lab.dto.QualificationCreateDto;
import com.ruoyi.lab.dto.QualificationRevokeDto;
import com.ruoyi.lab.dto.QualificationUpdateDto;
import com.ruoyi.lab.service.QualificationService;
import com.ruoyi.lab.vo.QualificationVo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
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

/** Qualification management and current-user HTTP API. */
@Validated
@RestController
@RequestMapping("/lab/qualifications")
public class LabQualificationController extends LabBaseController
{
    private final QualificationService qualificationService;

    public LabQualificationController(QualificationService qualificationService)
    {
        this.qualificationService = qualificationService;
    }

    @PreAuthorize("@ss.hasPermi('lab:qualification:list')")
    @GetMapping("/list")
    public TableDataInfo list(@RequestParam(required = false) @Positive Long userId,
            @RequestParam(required = false) QualificationScopeType scopeType,
            @RequestParam(defaultValue = "createTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection)
    {
        startPage();
        try
        {
            List<QualificationVo> qualifications = qualificationService.list(userId, scopeType,
                    sortBy, sortDirection);
            return getDataTable(qualifications);
        }
        finally
        {
            clearPage();
        }
    }

    @PreAuthorize("@ss.hasAnyPermi('lab:qualification:query,lab:qualification:mine')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable @Positive Long id)
    {
        if (SecurityUtils.hasPermi("lab:qualification:query"))
        {
            return success(qualificationService.getById(id));
        }
        return success(qualificationService.getMineById(id));
    }

    @PreAuthorize("@ss.hasPermi('lab:qualification:add')")
    @Log(title = "实验室资格", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody QualificationCreateDto input)
    {
        return success(qualificationService.create(input, SecurityUtils.getUsername(),
                SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:qualification:edit')")
    @Log(title = "实验室资格", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public AjaxResult edit(@PathVariable @Positive Long id,
            @Valid @RequestBody QualificationUpdateDto input)
    {
        return success(qualificationService.update(id, input, SecurityUtils.getUsername(),
                SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:qualification:revoke')")
    @Log(title = "实验室资格撤销", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/commands/revoke")
    public AjaxResult revoke(@PathVariable @Positive Long id,
            @Valid @RequestBody QualificationRevokeDto input)
    {
        return success(qualificationService.revoke(id, input, SecurityUtils.getUsername(),
                SecurityUtils.getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:qualification:mine')")
    @GetMapping("/mine")
    public TableDataInfo mine(
            @RequestParam(defaultValue = "createTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection)
    {
        startPage();
        try
        {
            return getDataTable(qualificationService.listMine(sortBy, sortDirection));
        }
        finally
        {
            clearPage();
        }
    }
}
