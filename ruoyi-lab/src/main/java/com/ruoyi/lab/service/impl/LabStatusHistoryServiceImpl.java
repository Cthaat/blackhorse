package com.ruoyi.lab.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import com.ruoyi.lab.domain.LabStatusHistory;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabStatusHistoryMapper;
import com.ruoyi.lab.service.LabStatusHistoryService;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The sole append path for immutable lifecycle history. */
@Service
public class LabStatusHistoryServiceImpl implements LabStatusHistoryService
{
    private static final String TRACE_ID_KEY = "traceId";

    private final LabStatusHistoryMapper historyMapper;
    private final Clock clock;

    public LabStatusHistoryServiceImpl(LabStatusHistoryMapper historyMapper, Clock clock)
    {
        this.historyMapper = historyMapper;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Long append(String objectType, Long objectId, String fromStatus, String toStatus,
            Long operatorId, String reason)
    {
        String normalizedReason = requireReason(reason);
        if (objectType == null || objectType.isBlank() || objectId == null || objectId <= 0
                || toStatus == null || toStatus.isBlank() || operatorId == null)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "状态历史参数无效");
        }

        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.isBlank())
        {
            traceId = UUID.randomUUID().toString();
        }

        LabStatusHistory history = new LabStatusHistory();
        history.setObjectType(objectType);
        history.setObjectId(objectId);
        history.setFromStatus(fromStatus);
        history.setToStatus(toStatus);
        history.setOperatorId(operatorId);
        history.setReason(normalizedReason);
        history.setTraceId(traceId);
        history.setCreateTime(LocalDateTime.now(clock));
        history.setDelFlag("0");
        historyMapper.insert(history);
        return history.getId();
    }

    private static String requireReason(String reason)
    {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.isEmpty() || normalized.length() > 500)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "状态变更原因长度无效");
        }
        return normalized;
    }
}
