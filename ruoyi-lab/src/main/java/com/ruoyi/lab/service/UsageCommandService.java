package com.ruoyi.lab.service;

import com.ruoyi.lab.dto.CheckOutCommand;
import com.ruoyi.lab.dto.ReturnUsageCommand;
import com.ruoyi.lab.vo.UsageRecordVo;

public interface UsageCommandService
{
    UsageRecordVo checkOut(CheckOutCommand command, Long operatorId);

    UsageRecordVo returnUsage(Long usageId, ReturnUsageCommand command, Long operatorId);
}
