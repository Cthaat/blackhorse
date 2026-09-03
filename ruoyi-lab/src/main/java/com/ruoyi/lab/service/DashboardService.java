package com.ruoyi.lab.service;

import com.ruoyi.lab.vo.DashboardSnapshotVo;

/** Role-aware laboratory workbench. */
public interface DashboardService
{
    DashboardSnapshotVo snapshot(Long currentUserId);
}
