package com.ruoyi.lab.service.impl;

import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDashboardMapper;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.service.DashboardService;
import com.ruoyi.lab.vo.DashboardSnapshotVo;
import org.springframework.stereotype.Service;

/** MySQL aggregate dashboard constrained by the current data scope. */
@Service
public class DashboardServiceImpl implements DashboardService
{
    private final LabDashboardMapper dashboardMapper;
    private final LabDataScopeService dataScopeService;

    public DashboardServiceImpl(LabDashboardMapper dashboardMapper,
            LabDataScopeService dataScopeService)
    {
        this.dashboardMapper = dashboardMapper;
        this.dataScopeService = dataScopeService;
    }

    @Override
    public DashboardSnapshotVo snapshot(Long currentUserId)
    {
        if (currentUserId == null || currentUserId <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "用户编号无效");
        }
        LabDataScope scope = dataScopeService.resolveCurrentScope();
        if (scope.userId() != currentUserId)
        {
            throw new LabBusinessException(LabErrorCode.LAB_OUT_OF_DATA_SCOPE,
                    "对象不在当前数据范围内");
        }
        return new DashboardSnapshotVo(
                dashboardMapper.countPendingReservations(currentUserId, scope),
                dashboardMapper.countOpenUsage(currentUserId, scope),
                dashboardMapper.countOpenRepairs(currentUserId, scope),
                dashboardMapper.countPendingInspections(currentUserId, scope),
                dashboardMapper.countOpenHazards(currentUserId, scope),
                dashboardMapper.countUnreadNotifications(currentUserId));
    }
}
