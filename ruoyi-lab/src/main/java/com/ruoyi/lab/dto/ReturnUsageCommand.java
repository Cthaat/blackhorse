package com.ruoyi.lab.dto;

import com.ruoyi.lab.domain.ReturnCondition;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ReturnUsageCommand(@NotNull ReturnCondition condition,
        @Size(max = 500) String note, @Size(max = 1000) String faultDescription)
{
    @AssertTrue(message = "异常归还必须填写故障描述")
    public boolean hasFaultDescriptionWhenAbnormal()
    {
        return condition == null || !condition.isAbnormal()
                || faultDescription != null && !faultDescription.isBlank();
    }
}
