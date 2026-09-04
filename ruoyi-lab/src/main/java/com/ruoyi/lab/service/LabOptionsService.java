package com.ruoyi.lab.service;

import java.util.List;
import com.ruoyi.lab.dto.LabUserOptionQueryDto;
import com.ruoyi.lab.vo.LabDepartmentOptionVo;
import com.ruoyi.lab.vo.LabUserOptionVo;

public interface LabOptionsService
{
    List<LabUserOptionVo> users(LabUserOptionQueryDto query);

    List<LabDepartmentOptionVo> departments();
}
