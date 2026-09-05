package com.ruoyi.lab.service;

import java.util.List;
import java.util.HashSet;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import org.springframework.stereotype.Service;

/** Labels use the same object authorization as device details, without operational or personal data. */
@Service
public class AssetLabelService
{
    private final DeviceService devices;

    public AssetLabelService(DeviceService devices) { this.devices = devices; }

    public record Label(String id, String assetNo, String name) { }

    public List<Label> labels(List<String> ids)
    {
        if (ids == null || ids.isEmpty() || ids.size() > 100 || new HashSet<>(ids).size() != ids.size())
            throw invalid();
        List<Long> parsed = ids.stream().map(id -> {
            if (id == null || !id.matches("[1-9][0-9]{0,18}")) throw invalid();
            try { return Long.valueOf(id); }
            catch (NumberFormatException failure) { throw invalid(); }
        }).toList();
        return parsed.stream().map(id -> {
            var device = devices.getById(id);
            return new Label(device.getId().toString(), device.getAssetNo(), device.getName());
        }).toList();
    }

    private LabBusinessException invalid()
    {
        return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "请选择1至100台不同设备，设备编号必须是有效整数字符串");
    }
}
