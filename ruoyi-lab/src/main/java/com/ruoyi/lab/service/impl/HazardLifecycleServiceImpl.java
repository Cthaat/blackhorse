package com.ruoyi.lab.service.impl;

import java.time.LocalDateTime;
import com.ruoyi.lab.config.LabJobProperties;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabHazardMapper;
import com.ruoyi.lab.domain.LabHazard;
import com.ruoyi.lab.event.LabNotificationEventPublisher;
import com.ruoyi.lab.service.HazardLifecycleService;
import com.ruoyi.lab.service.LabSystemOperator;
import com.ruoyi.lab.service.LabSystemOperatorProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HazardLifecycleServiceImpl implements HazardLifecycleService
{
    private final LabHazardMapper hazardMapper;
    private final LabSystemOperatorProvider operatorProvider;
    private final LabNotificationEventPublisher notificationEventPublisher;

    public HazardLifecycleServiceImpl(LabHazardMapper hazardMapper,
            LabSystemOperatorProvider operatorProvider,
            LabNotificationEventPublisher notificationEventPublisher)
    {
        this.hazardMapper = hazardMapper;
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
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "隐患超期批处理参数无效");
        }
        int changed = 0;
        for (LabHazard candidate : hazardMapper.selectOverdueCandidates(now, batchSize))
        {
            if (hazardMapper.markOneOverdue(candidate.getId(), candidate.getVersion(), now,
                    operator.userName()) == 1)
            {
                LabHazard persisted = hazardMapper.selectActiveById(candidate.getId());
                if (persisted == null || persisted.getOverdueEventVersion() == null
                        || persisted.getOverdueEventVersion() <= 0)
                {
                    throw new LabBusinessException(LabErrorCode.INTERNAL_ERROR,
                            "隐患超期版本写入失败");
                }
                notificationEventPublisher.publishHazardOverdue(persisted.getId(),
                        persisted.getOverdueEventVersion());
                changed++;
            }
        }
        return changed;
    }
}
