package com.ruoyi.lab.service.impl;

import java.util.List;
import java.util.Objects;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.domain.LabLaboratory;
import com.ruoyi.lab.domain.LaboratoryStatus;
import com.ruoyi.lab.dto.LaboratoryCreateDto;
import com.ruoyi.lab.dto.LaboratoryUpdateDto;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabLaboratoryMapper;
import com.ruoyi.lab.mapper.LabOptionsMapper;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.service.LabSortWhitelist;
import com.ruoyi.lab.service.LabStatusHistoryService;
import com.ruoyi.lab.service.LabUserDirectory;
import com.ruoyi.lab.service.DeviceAvailabilityService;
import com.ruoyi.lab.service.LaboratoryService;
import com.ruoyi.lab.vo.LaboratoryVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default laboratory application service. */
@Service
public class LaboratoryServiceImpl implements LaboratoryService
{
    private static final String OBJECT_TYPE = "LABORATORY";

    private final LabLaboratoryMapper laboratoryMapper;
    private final LabDataScopeService dataScopeService;
    private final LabObjectPermissionService objectPermissionService;
    private final LabSortWhitelist sortWhitelist;
    private final LabStatusHistoryService historyService;
    private final LabOptionsMapper optionsMapper;
    private final DeviceAvailabilityService availabilityService;
    private final LabUserDirectory userDirectory;

    public LaboratoryServiceImpl(LabLaboratoryMapper laboratoryMapper,
            LabDataScopeService dataScopeService,
            LabObjectPermissionService objectPermissionService,
            LabSortWhitelist sortWhitelist, LabStatusHistoryService historyService,
            LabOptionsMapper optionsMapper, DeviceAvailabilityService availabilityService,
            LabUserDirectory userDirectory)
    {
        this.laboratoryMapper = laboratoryMapper;
        this.dataScopeService = dataScopeService;
        this.objectPermissionService = objectPermissionService;
        this.sortWhitelist = sortWhitelist;
        this.historyService = historyService;
        this.optionsMapper = optionsMapper;
        this.availabilityService = availabilityService;
        this.userDirectory = userDirectory;
    }

    @Override
    public List<LaboratoryVo> list(LaboratoryStatus status, String keyword,
            String sortBy, String sortDirection)
    {
        LabDataScope scope = dataScopeService.resolveCurrentScope();
        if (scope.empty())
        {
            return List.of();
        }
        LabSortWhitelist.SortClause sort = sortWhitelist.resolve("laboratory",
                defaultValue(sortBy, "createTime"), defaultValue(sortDirection, "desc"));
        return laboratoryMapper.selectListByScope(scope, status, trimToNull(keyword), sort)
                .stream().map(LaboratoryVo::from).toList();
    }

    @Override
    public LaboratoryVo getById(Long laboratoryId)
    {
        return LaboratoryVo.from(requireInScope(requirePositive(laboratoryId),
                dataScopeService.resolveCurrentScope()));
    }

    @Override
    @Transactional
    public LaboratoryVo create(LaboratoryCreateDto input, String username, Long actorId)
    {
        Objects.requireNonNull(input, "input");
        LabDataScope scope = dataScopeService.resolveCurrentScope();
        if (!Objects.equals(scope.userId(), actorId))
        {
            throw outOfScope();
        }
        assertDepartmentUsable(input.getDeptId(), actorId);
        assertManagerCanManageDepartment(input.getManagerId(), input.getDeptId());

        LabLaboratory laboratory = details(input.getLabCode(), input.getName(), input.getDeptId(),
                input.getManagerId(), input.getLocation(), input.getDescription(), username);
        laboratory.setStatus(LaboratoryStatus.ENABLED);
        laboratory.setVersion(0);
        laboratory.setCreateBy(requireUsername(username));
        laboratory.setDelFlag("0");
        laboratoryMapper.insert(laboratory);
        historyService.append(OBJECT_TYPE, laboratory.getId(), null, LaboratoryStatus.ENABLED.name(),
                actorId, "创建实验室");
        return LaboratoryVo.from(laboratory);
    }

    @Override
    @Transactional
    public void update(Long laboratoryId, LaboratoryUpdateDto input, String username)
    {
        Objects.requireNonNull(input, "input");
        long id = requirePositive(laboratoryId);
        objectPermissionService.assertLaboratoryManageable(id);
        LabDataScope scope = dataScopeService.resolveCurrentScope();
        assertDepartmentUsable(input.getDeptId(), scope.userId());
        assertManagerCanManageDepartment(input.getManagerId(), input.getDeptId());
        LabLaboratory laboratory = details(input.getLabCode(), input.getName(), input.getDeptId(),
                input.getManagerId(), input.getLocation(), input.getDescription(), username);
        laboratory.setId(id);
        if (laboratoryMapper.updateDetailsConditionally(laboratory, input.getExpectedVersion()) != 1)
        {
            throw duplicateOperation();
        }
    }

    @Override
    @Transactional
    public void enable(Long laboratoryId, String reason, Long actorId)
    {
        changeStatus(laboratoryId, LaboratoryStatus.ENABLED, reason, actorId);
    }

    @Override
    @Transactional
    public void disable(Long laboratoryId, String reason, Long actorId)
    {
        changeStatus(laboratoryId, LaboratoryStatus.DISABLED, reason, actorId);
    }

    private void changeStatus(Long laboratoryId, LaboratoryStatus target, String reason, Long actorId)
    {
        String normalizedReason = requireReason(reason);
        long id = requirePositive(laboratoryId);
        LabDataScope scope = dataScopeService.resolveCurrentScope();
        if (!Objects.equals(scope.userId(), actorId))
        {
            throw outOfScope();
        }
        requireInScope(id, scope);
        LabLaboratory locked = laboratoryMapper.selectByIdForUpdate(id);
        if (locked == null)
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "实验室不存在");
        }
        if (scope.restricted() && !scope.laboratoryIds().contains(locked.getId()))
        {
            throw outOfScope();
        }
        LaboratoryStatus current = locked.getStatus();
        if (current == target || !((current == LaboratoryStatus.ENABLED && target == LaboratoryStatus.DISABLED)
                || (current == LaboratoryStatus.DISABLED && target == LaboratoryStatus.ENABLED)))
        {
            throw illegalTransition();
        }
        if (laboratoryMapper.updateStatusConditionally(id, current.name(), target.name()) != 1)
        {
            throw duplicateOperation();
        }
        historyService.append(OBJECT_TYPE, id, current.name(), target.name(), actorId, normalizedReason);
        if (target == LaboratoryStatus.ENABLED)
        {
            availabilityService.restoreAfterLaboratoryEnabled(id, actorId);
        }
    }

    private LabLaboratory requireInScope(long id, LabDataScope scope)
    {
        LabLaboratory laboratory = laboratoryMapper.selectByIdInScope(id, scope);
        if (laboratory != null)
        {
            return laboratory;
        }
        if (scope.allLaboratories() || scope.laboratoryIds().contains(id))
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "实验室不存在");
        }
        throw outOfScope();
    }

    private void assertDepartmentUsable(Long departmentId, Long actorId)
    {
        long id = requirePositive(departmentId);
        if (optionsMapper.countActiveDepartment(id) <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "部门不存在或已停用");
        }
        if (!SecurityUtils.isAdmin(actorId)
                && !objectPermissionService.readableDepartmentIds().contains(id))
        {
            throw outOfScope();
        }
    }

    private void assertManagerCanManageDepartment(Long managerId, Long departmentId)
    {
        userDirectory.assertActiveRole(managerId, "lab_manager");
        if (optionsMapper.countActiveUserDepartmentScope(managerId, departmentId) <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR,
                    "所选实验室负责人无权管理目标部门");
        }
    }

    private static LabLaboratory details(String code, String name, Long departmentId,
            Long managerId, String location, String description, String username)
    {
        LabLaboratory laboratory = new LabLaboratory();
        laboratory.setLabCode(requireText(code));
        laboratory.setName(requireText(name));
        laboratory.setDeptId(departmentId);
        laboratory.setManagerId(managerId);
        laboratory.setLocation(requireText(location));
        laboratory.setDescription(trimToNull(description));
        laboratory.setUpdateBy(requireUsername(username));
        return laboratory;
    }

    private static long requirePositive(Long id)
    {
        if (id == null || id <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "对象编号无效");
        }
        return id;
    }

    private static String requireText(String value)
    {
        String normalized = trimToNull(value);
        if (normalized == null)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "必填文本不能为空");
        }
        return normalized;
    }

    private static String requireUsername(String username)
    {
        return requireText(username);
    }

    private static String requireReason(String reason)
    {
        String normalized = trimToNull(reason);
        if (normalized == null || normalized.length() > 500)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "状态变更原因长度无效");
        }
        return normalized;
    }

    private static String trimToNull(String value)
    {
        if (value == null)
        {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String defaultValue(String value, String fallback)
    {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static LabBusinessException outOfScope()
    {
        return new LabBusinessException(LabErrorCode.LAB_OUT_OF_DATA_SCOPE, "对象不在当前数据范围内");
    }

    private static LabBusinessException duplicateOperation()
    {
        return new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION, "操作已被其他请求处理");
    }

    private static LabBusinessException illegalTransition()
    {
        return new LabBusinessException(LabErrorCode.LAB_ILLEGAL_STATE_TRANSITION, "实验室状态变更不合法");
    }
}
