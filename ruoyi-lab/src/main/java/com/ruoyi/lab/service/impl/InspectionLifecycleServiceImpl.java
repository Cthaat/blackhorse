package com.ruoyi.lab.service.impl;

import java.time.LocalDateTime;
import com.ruoyi.lab.config.LabJobProperties;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabInspectionTaskMapper;
import com.ruoyi.lab.domain.LabInspectionTask;
import com.ruoyi.lab.event.LabNotificationEventPublisher;
import com.ruoyi.lab.service.InspectionLifecycleService;
import com.ruoyi.lab.service.LabSystemOperator;
import com.ruoyi.lab.service.LabSystemOperatorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionLifecycleServiceImpl implements InspectionLifecycleService
{
    private final LabInspectionTaskMapper taskMapper;
    private final LabSystemOperatorProvider operatorProvider;
    private final LabNotificationEventPublisher notificationEventPublisher;

    public InspectionLifecycleServiceImpl(LabInspectionTaskMapper taskMapper,
            LabSystemOperatorProvider operatorProvider,
            LabNotificationEventPublisher notificationEventPublisher)
    {
        this.taskMapper = taskMapper;
        this.operatorProvider = operatorProvider;
        this.notificationEventPublisher = notificationEventPublisher;
    }

    @Override
    @Transactional
    public int markOverdue(LocalDateTime now, int batchSize)
    {
        LabSystemOperator operator = operatorProvider.requiredOperator();
        if (now == null || batchSize < LabJobProperties.MIN_BATCH_SIZE
                || batchSize > LabJobProperties.MAX_BATCH_SIZE)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "巡检超期批处理参数无效");
        }
        int changed = 0;
        for (LabInspectionTask candidate : taskMapper.selectOverdueCandidates(now, batchSize))
        {
            if (taskMapper.markOneOverdue(candidate.getId(), candidate.getVersion(), now,
                    operator.userName()) == 1)
            {
                LabInspectionTask persisted = taskMapper.selectActiveById(candidate.getId());
                if (persisted == null || persisted.getOverdueEventVersion() == null
                        || persisted.getOverdueEventVersion() <= 0)
                {
                    throw new LabBusinessException(LabErrorCode.INTERNAL_ERROR,
                            "巡检超期版本写入失败");
                }
                notificationEventPublisher.publishInspectionOverdue(persisted.getId(),
                        persisted.getOverdueEventVersion());
                changed++;
            }
        }
        return changed;
    }
}
