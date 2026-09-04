package com.ruoyi.lab.vo;

import com.ruoyi.lab.serializer.LabBusinessId;

/** Deliberately minimal user selector projection. */
public record LabUserOptionVo(@LabBusinessId Long id, String userName,
        String displayName, String departmentName)
{
}
