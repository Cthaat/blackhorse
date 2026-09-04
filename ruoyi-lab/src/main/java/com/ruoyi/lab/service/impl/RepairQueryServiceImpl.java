package com.ruoyi.lab.service.impl;

import java.util.List;
import com.ruoyi.lab.dto.RepairQueryDto;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabRepairOrderMapper;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.service.AttachmentService;
import com.ruoyi.lab.service.RepairQueryService;
import com.ruoyi.lab.service.StatusHistoryQueryService;
import com.ruoyi.lab.vo.RepairOrderDetailVo;
import com.ruoyi.lab.vo.RepairOrderVo;
import org.springframework.stereotype.Service;

@Service
public class RepairQueryServiceImpl implements RepairQueryService
{
    private final LabRepairOrderMapper repairMapper;
    private final LabDataScopeService dataScopeService;
    private final StatusHistoryQueryService statusHistoryQueryService;
    private final AttachmentService attachmentService;

    public RepairQueryServiceImpl(LabRepairOrderMapper repairMapper,
            LabDataScopeService dataScopeService,
            StatusHistoryQueryService statusHistoryQueryService,
            AttachmentService attachmentService)
    {
        this.repairMapper = repairMapper;
        this.dataScopeService = dataScopeService;
        this.statusHistoryQueryService = statusHistoryQueryService;
        this.attachmentService = attachmentService;
    }

    @Override
    public List<RepairOrderVo> list(RepairQueryDto query, Long currentUserId)
    {
        long userId = requirePositive(currentUserId);
        LabDataScope scope = dataScopeService.resolveCurrentScope();
        return repairMapper.selectScopedList(query == null ? new RepairQueryDto() : query,
                userId, scope);
    }

    @Override
    public RepairOrderDetailVo detail(Long orderId, Long currentUserId)
    {
        long id = requirePositive(orderId);
        long userId = requirePositive(currentUserId);
        RepairOrderVo result = repairMapper.selectScopedDetail(id, userId,
                dataScopeService.resolveCurrentScope());
        if (result == null)
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "维修工单不存在");
        }
        return new RepairOrderDetailVo(result,
                statusHistoryQueryService.list("REPAIR_ORDER", id, userId),
                attachmentService.list("REPAIR_ORDER", id));
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
