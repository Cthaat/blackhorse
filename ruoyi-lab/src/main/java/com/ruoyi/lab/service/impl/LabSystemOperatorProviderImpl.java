package com.ruoyi.lab.service.impl;

import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabSystemOperatorMapper;
import com.ruoyi.lab.service.LabSystemOperator;
import com.ruoyi.lab.service.LabSystemOperatorProvider;
import org.springframework.stereotype.Service;

@Service
public class LabSystemOperatorProviderImpl implements LabSystemOperatorProvider
{
    private final LabSystemOperatorMapper mapper;

    public LabSystemOperatorProviderImpl(LabSystemOperatorMapper mapper)
    {
        this.mapper = mapper;
    }

    @Override
    public LabSystemOperator requiredOperator()
    {
        LabSystemOperator operator = mapper.selectConfiguredDisabledOperator();
        if (operator == null || operator.userId() == null || operator.userId() <= 0
                || operator.userName() == null || operator.userName().isBlank())
        {
            throw new LabBusinessException(LabErrorCode.INTERNAL_ERROR,
                    "系统任务操作主体配置无效");
        }
        return operator;
    }
}
