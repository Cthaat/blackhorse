package com.ruoyi.lab.service;

import com.ruoyi.lab.dto.DashboardQueryDto;
import com.ruoyi.lab.vo.DashboardSnapshotVo;

/** Role-aware laboratory workbench. */
public interface DashboardService
{
    DashboardSnapshotVo snapshot(Long currentUserId, DashboardQueryDto query);

    default DashboardSnapshotVo snapshot(Long currentUserId)
    {
        return snapshot(currentUserId, new DashboardQueryDto());
    }
}
