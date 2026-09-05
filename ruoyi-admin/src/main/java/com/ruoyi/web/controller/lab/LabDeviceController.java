package com.ruoyi.web.controller.lab;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.lab.domain.DeviceStatus;
import com.ruoyi.lab.dto.DeviceCreateDto;
import com.ruoyi.lab.dto.DeviceStatusCommandDto;
import com.ruoyi.lab.dto.DeviceUpdateDto;
import com.ruoyi.lab.service.DeviceService;
import com.ruoyi.lab.service.DeviceStatusCommandService;
import com.ruoyi.lab.vo.DeviceVo;
import com.ruoyi.lab.vo.OccupiedRangeVo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
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

/** Device HTTP API. */
@Validated
@RestController
@RequestMapping("/lab/devices")
public class LabDeviceController extends LabBaseController
{
    private final DeviceService deviceService;
    private final DeviceStatusCommandService statusCommandService;

    public LabDeviceController(DeviceService deviceService,
            DeviceStatusCommandService statusCommandService)
    {
        this.deviceService = deviceService;
        this.statusCommandService = statusCommandService;
    }

    @PreAuthorize("@ss.hasPermi('lab:device:list')")
    @GetMapping("/list")
    public TableDataInfo list(
            @RequestParam(required = false) @Positive Long laboratoryId,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) DeviceStatus status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "createTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDirection)
    {
        startPage();
        try
        {
            List<DeviceVo> devices = deviceService.list(laboratoryId, categoryCode, status, keyword,
                    sortBy, sortDirection);
            return getDataTable(devices);
        }
        finally
        {
            clearPage();
        }
    }

    @PreAuthorize("@ss.hasPermi('lab:device:query')")
    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable @Positive Long id)
    {
        return success(deviceService.getById(id));
    }

    @PreAuthorize("@ss.hasPermi('lab:device:add')")
    @Log(title = "实验室设备", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Valid @RequestBody DeviceCreateDto input)
    {
        return success(deviceService.create(input, getUsername(), getUserId()));
    }

    @PreAuthorize("@ss.hasPermi('lab:device:edit')")
    @Log(title = "实验室设备", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}")
    public AjaxResult edit(@PathVariable @Positive Long id,
            @Valid @RequestBody DeviceUpdateDto input)
    {
        deviceService.update(id, input, getUsername());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:device:status')")
    @Log(title = "设备状态", businessType = BusinessType.UPDATE)
    @PutMapping("/{id}/commands/change-status")
    public AjaxResult changeStatus(@PathVariable @Positive Long id,
            @Valid @RequestBody DeviceStatusCommandDto command)
    {
        statusCommandService.changeStatus(id, command, getUserId());
        return success();
    }

    @PreAuthorize("@ss.hasPermi('lab:device:query')")
    @GetMapping("/{id}/occupied-ranges")
    public List<OccupiedRangeVo> occupiedRanges(@PathVariable @Positive Long id,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to)
    {
        return deviceService.occupiedRanges(id, from, to);
    }
}
