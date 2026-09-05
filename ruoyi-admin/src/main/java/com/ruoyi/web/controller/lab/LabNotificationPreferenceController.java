package com.ruoyi.web.controller.lab;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.lab.dto.NotificationPreferenceDto;
import com.ruoyi.lab.service.MessageTemplateService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/lab/notification-preferences")
public class LabNotificationPreferenceController extends LabBaseController
{
    private final MessageTemplateService service;
    public LabNotificationPreferenceController(MessageTemplateService service) {this.service=service;}
    @PreAuthorize("@ss.hasPermi('lab:notification:list')")
    @GetMapping public AjaxResult mine() {return success(new NotificationPreferenceDto(service.preference(getUserId())));}
    @PreAuthorize("@ss.hasPermi('lab:notification:list')")
    @PutMapping public AjaxResult update(@Valid @RequestBody NotificationPreferenceDto dto)
    {service.preference(getUserId(),dto.optionalReminders());return success();}
}
