package com.ruoyi.lab.service.impl;

import java.util.List;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.dto.UsageQueryDto;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabUsageRecordMapper;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.service.UsageQueryService;
import com.ruoyi.lab.vo.UsageRecordDetailVo;
import com.ruoyi.lab.vo.UsageRecordVo;
import org.springframework.stereotype.Service;

@Service
public class UsageQueryServiceImpl implements UsageQueryService
{
    private final LabUsageRecordMapper usageMapper;
    private final LabDataScopeService dataScopeService;

    public UsageQueryServiceImpl(LabUsageRecordMapper usageMapper,
            LabDataScopeService dataScopeService)
    {
        this.usageMapper = usageMapper;
        this.dataScopeService = dataScopeService;
    }

    @Override
    public List<UsageRecordVo> list(UsageQueryDto query, Long currentUserId)
    {
        long userId = requirePositive(currentUserId);
        UsageQueryDto filters = query == null ? new UsageQueryDto() : query;
        validateRange(filters);
        boolean studentOnly = studentOnly();
        LabDataScope scope = studentOnly ? null : dataScopeService.resolveCurrentScope();
        return usageMapper.selectScopedList(filters, userId, studentOnly, scope);
    }

    @Override
    public UsageRecordDetailVo detail(Long usageId, Long currentUserId)
    {
        long id = requirePositive(usageId);
        long userId = requirePositive(currentUserId);
        boolean studentOnly = studentOnly();
        LabDataScope scope = studentOnly ? null : dataScopeService.resolveCurrentScope();
        UsageRecordDetailVo detail = usageMapper.selectScopedDetail(id, userId, studentOnly, scope);
        if (detail == null)
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "使用记录不存在");
        }
        return detail;
    }

    private static boolean studentOnly()
    {
        return SecurityUtils.hasRole("lab_student") && !SecurityUtils.hasRole("lab_manager")
                && !SecurityUtils.hasRole("lab_safety_officer")
                && !SecurityUtils.hasRole("lab_system_admin");
    }

    private static void validateRange(UsageQueryDto query)
    {
        if (query.getCheckedOutFrom() != null && query.getCheckedOutTo() != null
                && !query.getCheckedOutFrom().isBefore(query.getCheckedOutTo()))
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "领用查询时间范围无效");
        }
    }

    private static long requirePositive(Long value)
    {
        if (value == null || value <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "对象编号无效");
        }
        return value;
    }
}
