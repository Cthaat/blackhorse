package com.ruoyi.web.controller.lab;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.lab.dto.MessageTemplateDto;
import com.ruoyi.lab.service.MessageTemplateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/lab/message-templates")
public class LabMessageTemplateController extends LabBaseController
{
    private final MessageTemplateService service;
    public LabMessageTemplateController(MessageTemplateService service) {this.service=service;}
    @PreAuthorize("@ss.hasPermi('lab:message-template:list')")
    @GetMapping({"","/versions"})
    public TableDataInfo list(@RequestParam(required=false) @Size(max=32) String eventType)
    {startPage();try{return getDataTable(service.list(eventType));}finally{clearPage();}}
    @PreAuthorize("@ss.hasPermi('lab:message-template:edit')")
    @Log(title="消息模板草稿",businessType=BusinessType.INSERT,isSaveRequestData=false)
    @PostMapping public AjaxResult create(@Valid @RequestBody MessageTemplateDto dto) {return success(service.save(null,dto,getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:message-template:edit')")
    @Log(title="消息模板编辑",businessType=BusinessType.UPDATE,isSaveRequestData=false)
    @PutMapping("/{id}") public AjaxResult edit(@PathVariable @Positive Long id,@Valid @RequestBody MessageTemplateDto dto) {return success(service.save(id,dto,getUserId()));}
    @PreAuthorize("@ss.hasPermi('lab:message-template:edit')")
    @PostMapping("/commands/preview") public AjaxResult preview(@Valid @RequestBody MessageTemplateDto dto) {return success(service.preview(dto));}
    @PreAuthorize("@ss.hasPermi('lab:message-template:edit')")
    @Log(title="消息模板发布",businessType=BusinessType.UPDATE,isSaveRequestData=false)
    @PostMapping("/{id}/commands/publish") public AjaxResult publish(@PathVariable @Positive Long id) {service.publish(id,getUserId());return success();}
}
