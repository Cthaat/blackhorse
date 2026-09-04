package com.ruoyi.lab.service.impl;

import java.util.List;
import java.util.Set;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.dto.LabUserOptionQueryDto;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabOptionsMapper;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.service.LabOptionsService;
import com.ruoyi.lab.service.LabUserDirectory;
import com.ruoyi.lab.vo.LabDepartmentOptionVo;
import com.ruoyi.lab.vo.LabUserOptionVo;
import org.springframework.stereotype.Service;

@Service
public class LabOptionsServiceImpl implements LabOptionsService, LabUserDirectory
{
    private static final Set<String> LAB_ROLES = Set.of("lab_student", "lab_manager",
            "lab_safety_officer", "lab_repair_worker", "lab_system_admin");

    private final LabOptionsMapper optionsMapper;
    private final LabObjectPermissionService objectPermissionService;

    public LabOptionsServiceImpl(LabOptionsMapper optionsMapper,
            LabObjectPermissionService objectPermissionService)
    {
        this.optionsMapper = optionsMapper;
        this.objectPermissionService = objectPermissionService;
    }

    @Override
    public List<LabUserOptionVo> users(LabUserOptionQueryDto query)
    {
        LabUserOptionQueryDto filters = query == null ? new LabUserOptionQueryDto() : query;
        String roleKey = filters.getRoleKey();
        String keyword = filters.getKeyword();
        if (roleKey != null && !LAB_ROLES.contains(roleKey))
        {
            throw validation("实验室角色标识无效");
        }
        if (keyword != null && keyword.length() > 50)
        {
            throw validation("关键词长度不能超过50个字符");
        }
        return optionsMapper.selectActiveUserOptions(roleKey, keyword);
    }

    @Override
    public List<LabDepartmentOptionVo> departments()
    {
        Long userId = objectPermissionService.currentUserId();
        boolean builtInAdmin = SecurityUtils.isAdmin(userId);
        Set<Long> departmentIds = builtInAdmin ? Set.of()
                : objectPermissionService.readableDepartmentIds();
        return optionsMapper.selectActiveDepartmentOptions(builtInAdmin, departmentIds);
    }

    @Override
    public void assertActiveRole(Long userId, String roleKey)
    {
        requirePositive(userId);
        if (!LAB_ROLES.contains(roleKey)
                || optionsMapper.countActiveUserRole(userId, roleKey) <= 0)
        {
            throw validation("所选用户未启用或不具备所需实验室角色");
        }
    }

    @Override
    public void assertActiveBusinessParticipant(Long userId)
    {
        requirePositive(userId);
        if (optionsMapper.countActiveBusinessParticipant(userId) <= 0)
        {
            throw validation("所选用户未启用或不具备实验室业务角色");
        }
    }

    private static long requirePositive(Long value)
    {
        if (value == null || value <= 0)
        {
            throw validation("用户编号无效");
        }
        return value;
    }

    private static LabBusinessException validation(String message)
    {
        return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, message);
    }
}
