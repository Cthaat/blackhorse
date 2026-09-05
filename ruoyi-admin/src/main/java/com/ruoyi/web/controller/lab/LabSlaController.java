package com.ruoyi.web.controller.lab;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.lab.sla.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
@Validated
@RestController
@RequestMapping("/lab/sla")
public class LabSlaController extends LabBaseController
{
    private final SlaService service;
    public LabSlaController(SlaService service){this.service=service;}
    @GetMapping("/records") @PreAuthorize("@ss.hasPermi('lab:sla:list')")
    public TableDataInfo records(@RequestParam(required=false) String businessType,@RequestParam(required=false) String state,@RequestParam(defaultValue="false") boolean mine)
    {startPage();try{return getDataTable(service.list(businessType,state,mine));}finally{clearPage();}}
    @GetMapping("/records/{id}") @PreAuthorize("@ss.hasPermi('lab:sla:list')")
    public AjaxResult detail(@PathVariable @Positive Long id){return success(service.detail(id));}
    @GetMapping("/rules") @PreAuthorize("@ss.hasPermi('lab:sla:rule')")
    public AjaxResult rules(@RequestParam @Positive Long laboratoryId){return success(service.rules(laboratoryId));}
    @PostMapping("/rules") @PreAuthorize("@ss.hasPermi('lab:sla:rule')")
    @Log(title="SLA 规则发布",businessType=BusinessType.INSERT,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult publish(@Valid @RequestBody SlaCommands.Rule command){return success(service.publish(command));}
    @PostMapping("/records/{id}/commands/pause") @PreAuthorize("@ss.hasPermi('lab:sla:manage')")
    @Log(title="SLA 暂停处理计时",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult pause(@PathVariable @Positive Long id,@Valid @RequestBody SlaCommands.ClockCommand command){return success(service.clock(id,true,command));}
    @PostMapping("/records/{id}/commands/resume") @PreAuthorize("@ss.hasPermi('lab:sla:manage')")
    @Log(title="SLA 恢复处理计时",businessType=BusinessType.UPDATE,isSaveRequestData=false,isSaveResponseData=false)
    public AjaxResult resume(@PathVariable @Positive Long id,@Valid @RequestBody SlaCommands.ClockCommand command){return success(service.clock(id,false,command));}
}
