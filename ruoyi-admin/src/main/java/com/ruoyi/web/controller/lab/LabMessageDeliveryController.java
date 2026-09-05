package com.ruoyi.web.controller.lab;

import java.util.List;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.lab.dto.MessageReplayDto;
import com.ruoyi.lab.service.MessageDeliveryStore;
import com.ruoyi.lab.service.MessageDeliveryQueryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/lab/deliveries")
public class LabMessageDeliveryController extends LabBaseController
{
    private final MessageDeliveryQueryService query;
    private final MessageDeliveryStore store;
    public LabMessageDeliveryController(MessageDeliveryQueryService query,MessageDeliveryStore store) {this.query=query;this.store=store;}
    @PreAuthorize("@ss.hasPermi('lab:delivery:list')")
    @GetMapping
    public TableDataInfo list(@RequestParam(required=false) @Size(max=24) String status,
            @RequestParam(required=false) @Size(max=32) String eventType)
    {
        startPage();try {return getDataTable(query.list(status,eventType));} finally {clearPage();}
    }
    @PreAuthorize("@ss.hasPermi('lab:delivery:list')")
    @GetMapping("/{id}") public AjaxResult detail(@PathVariable @Positive Long id) {return success(query.detail(id));}
    @PreAuthorize("@ss.hasPermi('lab:delivery:retry')")
    @Log(title="消息投递重放",businessType=BusinessType.UPDATE,isSaveRequestData=false)
    @PostMapping("/{id}/commands/replay")
    public AjaxResult replay(@PathVariable @Positive Long id,@Valid @RequestBody MessageReplayDto dto)
    { store.replay(id,dto.reason(),getUserId());return success(); }
    @PreAuthorize("@ss.hasPermi('lab:delivery:retry')")
    @Log(title="消息投递提前重试",businessType=BusinessType.UPDATE,isSaveRequestData=false)
    @PostMapping("/{id}/commands/retry-now")
    public AjaxResult retryNow(@PathVariable @Positive Long id,@Valid @RequestBody MessageReplayDto dto)
    { store.retryNow(id,dto.reason(),getUserId());return success(); }
    @PreAuthorize("@ss.hasPermi('lab:delivery:list')")
    @GetMapping("/channels") public AjaxResult channels()
    {return success(List.of(new Channel("STATION","站内消息",true),new Channel("EMAIL","邮件（未接入）",false),new Channel("SMS","短信（未接入）",false),new Channel("ENTERPRISE","企业消息（未接入）",false)));}
    public record Channel(String code,String name,boolean enabled) { }
}
