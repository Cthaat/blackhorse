package com.ruoyi.web.controller.lab;

import com.github.pagehelper.PageInfo;
import java.util.List;
import java.util.function.Supplier;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.lab.dto.LabUserOptionQueryDto;
import com.ruoyi.lab.service.LabOptionsService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Project-local safe selectors that never expose system-user contact or credential fields. */
@Validated
@RestController
@RequestMapping("/lab/options")
public class LabOptionsController extends LabBaseController
{
    private final LabOptionsService optionsService;

    public LabOptionsController(LabOptionsService optionsService)
    {
        this.optionsService = optionsService;
    }

    @PreAuthorize("@ss.hasAnyRoles('lab_manager,lab_safety_officer,lab_system_admin')")
    @GetMapping("/users")
    public AjaxResult users(@Valid LabUserOptionQueryDto query)
    {
        return options(() -> optionsService.users(query));
    }

    @PreAuthorize("@ss.hasAnyRoles('lab_manager,lab_safety_officer,lab_system_admin')")
    @GetMapping("/departments")
    public AjaxResult departments()
    {
        return options(optionsService::departments);
    }

    private AjaxResult options(Supplier<? extends List<?>> supplier)
    {
        startPage();
        try
        {
            List<?> rows = supplier.get();
            return success(rows).put("total", new PageInfo<>(rows).getTotal());
        }
        finally { clearPage(); }
    }
}
