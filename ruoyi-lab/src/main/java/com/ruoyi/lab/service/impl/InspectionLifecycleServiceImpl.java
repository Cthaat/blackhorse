package com.ruoyi.lab.service.impl;

import java.time.LocalDateTime;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabInspectionTaskMapper;
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

    public InspectionLifecycleServiceImpl(LabInspectionTaskMapper taskMapper,
            LabSystemOperatorProvider operatorProvider)
    {
        this.taskMapper = taskMapper;
        this.operatorProvider = operatorProvider;
    }

    @Override
    @Transactional
    public int markOverdue(LocalDateTime now, int batchSize)
    {
        LabSystemOperator operator = operatorProvider.requiredOperator();
        if (now == null || batchSize < 1 || batchSize > 500)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "巡检超期批处理参数无效");
        }
        return taskMapper.markOverdue(now, batchSize, operator.userName());
    }
}
