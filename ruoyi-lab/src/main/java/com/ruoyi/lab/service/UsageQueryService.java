package com.ruoyi.lab.service;

import java.util.List;
import com.ruoyi.lab.dto.UsageQueryDto;
import com.ruoyi.lab.vo.UsageRecordDetailVo;
import com.ruoyi.lab.vo.UsageRecordVo;

public interface UsageQueryService
{
    List<UsageRecordVo> list(UsageQueryDto query, Long currentUserId);

    UsageRecordDetailVo detail(Long usageId, Long currentUserId);
}
