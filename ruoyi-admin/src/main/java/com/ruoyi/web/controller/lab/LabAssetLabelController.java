package com.ruoyi.web.controller.lab;

import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.service.AssetLabelService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/lab/asset-labels")
public class LabAssetLabelController extends LabBaseController
{
    private final AssetLabelService labels;
    public LabAssetLabelController(AssetLabelService labels) { this.labels = labels; }

    @PostMapping
    @PreAuthorize("@ss.hasPermi('lab:device:query')")
    public AjaxResult labels(@RequestBody JsonNode body)
    {
        JsonNode ids = body.get("deviceIds");
        if (!body.isObject() || body.size() != 1 || ids == null || !ids.isArray() || ids.size() > 100)
            throw invalid();
        List<String> values = new java.util.ArrayList<>();
        for (JsonNode id : ids)
        {
            if (!id.isTextual()) throw invalid();
            values.add(id.textValue());
        }
        return success(labels.labels(values));
    }

    private LabBusinessException invalid()
    {
        return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "deviceIds必须是1至100个不同设备编号字符串");
    }
}
