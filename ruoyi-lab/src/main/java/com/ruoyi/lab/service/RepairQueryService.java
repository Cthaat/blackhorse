package com.ruoyi.lab.service;

import java.util.List;
import com.ruoyi.lab.dto.RepairQueryDto;
import com.ruoyi.lab.vo.RepairOrderVo;

public interface RepairQueryService
{
    List<RepairOrderVo> list(RepairQueryDto query, Long currentUserId);

    RepairOrderVo detail(Long orderId, Long currentUserId);
}
