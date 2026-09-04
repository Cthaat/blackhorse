package com.ruoyi.lab.vo;

import com.ruoyi.lab.serializer.LabBusinessId;

/** Deliberately minimal department selector projection. */
public record LabDepartmentOptionVo(@LabBusinessId Long id, String name)
{
}
