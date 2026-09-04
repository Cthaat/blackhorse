package com.ruoyi.web.controller.lab;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.lab.service.NotificationService;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Current-user station message center. */
@Validated
@RestController
@RequestMapping("/lab/notifications")
public class LabNotificationController extends BaseController
{
    private final NotificationService notificationService;

    public LabNotificationController(NotificationService notificationService)
    {
        this.notificationService = notificationService;
    }

    @PreAuthorize("@ss.hasPermi('lab:notification:list')")
    @GetMapping
    public TableDataInfo list(@RequestParam(defaultValue = "false") boolean unreadOnly)
    {
        startPage();
        try
        {
            return getDataTable(notificationService.listMine(getUserId(), unreadOnly));
        }
        finally
        {
            clearPage();
        }
    }

    @PreAuthorize("@ss.hasPermi('lab:notification:list')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable @Positive Long id)
    {
        return success(notificationService.getMine(id, getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:notification:read')")
    @PutMapping("/{id}/commands/read")
    public AjaxResult markRead(@PathVariable @Positive Long id)
    {
        return success(notificationService.markRead(id, getUserId()));
    }
}
