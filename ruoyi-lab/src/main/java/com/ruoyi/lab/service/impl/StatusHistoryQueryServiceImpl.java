package com.ruoyi.lab.service.impl;

import java.util.List;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabStatusHistoryMapper;
import com.ruoyi.lab.security.LabStatusHistoryObjectAuthorizer;
import com.ruoyi.lab.service.StatusHistoryQueryService;
import com.ruoyi.lab.vo.StatusHistoryVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StatusHistoryQueryServiceImpl implements StatusHistoryQueryService
{
    private final LabStatusHistoryMapper historyMapper;
    private final LabStatusHistoryObjectAuthorizer objectAuthorizer;

    public StatusHistoryQueryServiceImpl(LabStatusHistoryMapper historyMapper,
            LabStatusHistoryObjectAuthorizer objectAuthorizer)
    {
        this.historyMapper = historyMapper;
        this.objectAuthorizer = objectAuthorizer;
    }

    @Override
    @Transactional(readOnly = true)
    public List<StatusHistoryVo> list(String objectType, Long objectId, Long currentUserId)
    {
        if (objectId == null || objectId <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "对象编号无效");
        }
        String normalizedType = objectAuthorizer.normalizeObjectType(objectType);
        objectAuthorizer.assertReadable(normalizedType, objectId, currentUserId);
        return historyMapper.selectByObject(normalizedType, objectId);
    }
}
