package com.ruoyi.lab.service.impl;

import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabRepairOrderMapper;
import com.ruoyi.lab.service.RepairWorkerDirectory;
import org.springframework.stereotype.Service;

@Service
public class DatabaseRepairWorkerDirectory implements RepairWorkerDirectory
{
    private static final String REPAIR_ROLE = "lab_repair_worker";
    private final LabRepairOrderMapper repairOrderMapper;

    public DatabaseRepairWorkerDirectory(LabRepairOrderMapper repairOrderMapper)
    {
        this.repairOrderMapper = repairOrderMapper;
    }

    @Override
    public void assertRepairWorker(Long userId)
    {
        if (userId == null || userId <= 0
                || repairOrderMapper.countActiveUserRole(userId, REPAIR_ROLE) != 1)
        {
            throw new LabBusinessException(LabErrorCode.ACCESS_DENIED, "指定用户不是有效维修人员");
        }
    }
}
