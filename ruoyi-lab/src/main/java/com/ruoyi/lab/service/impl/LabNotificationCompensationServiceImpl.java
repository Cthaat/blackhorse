package com.ruoyi.lab.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.config.LabJobProperties;
import com.ruoyi.lab.domain.LabHazard;
import com.ruoyi.lab.domain.LabInspectionTask;
import com.ruoyi.lab.dto.NotificationCommand;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabHazardMapper;
import com.ruoyi.lab.mapper.LabInspectionTaskMapper;
import com.ruoyi.lab.mapper.LabStatusHistoryMapper;
import com.ruoyi.lab.service.LabNotificationCompensationService;
import com.ruoyi.lab.service.LabNotificationDeliveryService;
import com.ruoyi.lab.service.NotificationExpectationResolver;
import com.ruoyi.lab.service.MessageDeliveryEngine;
import org.springframework.stereotype.Service;

@Service
public class LabNotificationCompensationServiceImpl
        implements LabNotificationCompensationService
{
    private final LabStatusHistoryMapper historyMapper;
    private final LabInspectionTaskMapper inspectionTaskMapper;
    private final LabHazardMapper hazardMapper;
    private final NotificationExpectationResolver expectationResolver;
    private final LabNotificationDeliveryService deliveryService;
    private final MessageDeliveryEngine engine;

    public LabNotificationCompensationServiceImpl(LabStatusHistoryMapper historyMapper, LabInspectionTaskMapper inspectionTaskMapper,
            LabHazardMapper hazardMapper, NotificationExpectationResolver expectationResolver,
            LabNotificationDeliveryService deliveryService, MessageDeliveryEngine engine)
    {
        this.historyMapper = historyMapper;
        this.inspectionTaskMapper = inspectionTaskMapper;
        this.hazardMapper = hazardMapper;
        this.expectationResolver = expectationResolver;
        this.deliveryService = deliveryService;
        this.engine = engine;
    }

    @Override
    public int retryFailed(LocalDateTime now, int batchSize)
    {
        validate(now, batchSize);
        return engine.retryDue(now, batchSize);
    }

    @Override
    public int reconcileStatusHistory(LocalDateTime now, int batchSize)
    {
        validate(now, batchSize);
        int delivered = engine.backfillWaitlists(batchSize)+engine.backfillSla(batchSize);
        for (Long historyId : historyMapper.selectNotificationCandidateIds(batchSize))
        {
            delivered += deliver(expectationResolver.resolveHistory(historyId));
        }
        for (LabInspectionTask task : inspectionTaskMapper.selectUnreconciledOverdue(batchSize))
        {
            delivered += deliver(expectationResolver.resolveInspectionOverdue(task.getId(),
                    task.getOverdueEventVersion()));
        }
        for (LabHazard hazard : hazardMapper.selectUnreconciledOverdue(batchSize))
        {
            delivered += deliver(expectationResolver.resolveHazardOverdue(hazard.getId(),
                    hazard.getOverdueEventVersion()));
        }
        return delivered;
    }

    private int deliver(List<NotificationCommand> commands)
    {
        commands.forEach(deliveryService::deliverSafely);
        return commands.size();
    }

    private static void validate(LocalDateTime now, int batchSize)
    {
        if (now == null || batchSize < LabJobProperties.MIN_BATCH_SIZE
                || batchSize > LabJobProperties.MAX_BATCH_SIZE)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR,
                    "通知补偿批处理参数无效");
        }
    }
}
