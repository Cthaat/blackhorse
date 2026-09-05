package com.ruoyi.lab.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.service.LabPage;
import java.util.Objects;
import com.ruoyi.lab.domain.DeviceStatus;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.domain.LabLaboratory;
import com.ruoyi.lab.domain.LaboratoryStatus;
import com.ruoyi.lab.dto.DeviceCreateDto;
import com.ruoyi.lab.dto.DeviceUpdateDto;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabDictionaryMapper;
import com.ruoyi.lab.mapper.LabLaboratoryMapper;
import com.ruoyi.lab.mapper.LabOptionsMapper;
import com.ruoyi.lab.mapper.LabReservationMapper;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.service.DeviceService;
import com.ruoyi.lab.service.LabSortWhitelist;
import com.ruoyi.lab.service.LabStatusHistoryService;
import com.ruoyi.lab.service.LabUserDirectory;
import com.ruoyi.lab.vo.DeviceVo;
import com.ruoyi.lab.vo.OccupiedRangeVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default device profile service. */
@Service
public class DeviceServiceImpl implements DeviceService
{
    private static final String OBJECT_TYPE = "DEVICE";
    private static final String DEVICE_CATEGORY_DICT_TYPE = "lab_device_category";

    private final LabDeviceMapper deviceMapper;
    private final LabLaboratoryMapper laboratoryMapper;
    private final LabDataScopeService dataScopeService;
    private final LabObjectPermissionService objectPermissionService;
    private final LabSortWhitelist sortWhitelist;
    private final LabStatusHistoryService historyService;
    private final LabReservationMapper reservationMapper;
    private final LabDictionaryMapper dictionaryMapper;
    private final Clock clock;
    private final LabUserDirectory userDirectory;
    private final LabOptionsMapper optionsMapper;

    public DeviceServiceImpl(LabDeviceMapper deviceMapper, LabLaboratoryMapper laboratoryMapper,
            LabDataScopeService dataScopeService, LabObjectPermissionService objectPermissionService,
            LabSortWhitelist sortWhitelist, LabStatusHistoryService historyService,
            LabReservationMapper reservationMapper, LabDictionaryMapper dictionaryMapper,
            Clock clock, LabUserDirectory userDirectory, LabOptionsMapper optionsMapper)
    {
        this.deviceMapper = deviceMapper;
        this.laboratoryMapper = laboratoryMapper;
        this.dataScopeService = dataScopeService;
        this.objectPermissionService = objectPermissionService;
        this.sortWhitelist = sortWhitelist;
        this.historyService = historyService;
        this.reservationMapper = reservationMapper;
        this.dictionaryMapper = dictionaryMapper;
        this.clock = clock;
        this.userDirectory = userDirectory;
        this.optionsMapper = optionsMapper;
    }

    @Override
    public List<DeviceVo> list(Long laboratoryId, String categoryCode, DeviceStatus status,
            String keyword, String sortBy, String sortDirection)
    {
        LabDataScope scope = dataScopeService.resolveCurrentScope();
        LabSortWhitelist.SortClause sort = sortWhitelist.resolve("device",
                defaultValue(sortBy, "createTime"), defaultValue(sortDirection, "desc"));
        return LabPage.query(() -> deviceMapper.selectListByScope(scope, scope.userId(), LocalDateTime.now(clock),
                laboratoryId, trimToNull(categoryCode), status, trimToNull(keyword), sort), DeviceVo::from);
    }

    @Override
    public DeviceVo getById(Long deviceId)
    {
        long id = requirePositive(deviceId);
        LabDataScope scope = dataScopeService.resolveCurrentScope();
        LabDevice device = deviceMapper.selectByIdReadable(id, scope, scope.userId(),
                LocalDateTime.now(clock));
        if (device != null)
        {
            return DeviceVo.from(device);
        }
        if (scope.allLaboratories())
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "设备不存在");
        }
        throw outOfScope();
    }

    @Override
    @Transactional
    public DeviceVo create(DeviceCreateDto input, String username, Long actorId)
    {
        Objects.requireNonNull(input, "input");
        LabDataScope scope = dataScopeService.resolveCurrentScope();
        if (!Objects.equals(scope.userId(), actorId))
        {
            throw outOfScope();
        }
        LabLaboratory laboratory = laboratoryMapper.selectByIdInScope(input.getLaboratoryId(), scope);
        if (laboratory == null)
        {
            throw missingLaboratory(scope, input.getLaboratoryId());
        }
        if (laboratory.getStatus() != LaboratoryStatus.ENABLED)
        {
            throw new LabBusinessException(LabErrorCode.LAB_LABORATORY_DISABLED, "实验室已停用");
        }

        String categoryCode = requireActiveCategory(input.getCategoryCode());
        assertManagerCanManageLaboratory(input.getManagerId(), input.getLaboratoryId());
        LabDevice device = details(input.getAssetNo(), input.getLaboratoryId(), input.getName(),
                categoryCode, input.getModel(), requireActiveRisk(input.getRiskLevel()), input.getLocation(),
                input.getManagerId(), input.getDescription(), username);
        device.setStatus(DeviceStatus.AVAILABLE);
        device.setVersion(0);
        device.setCreateBy(requireText(username));
        device.setDelFlag("0");
        deviceMapper.insert(device);
        historyService.append(OBJECT_TYPE, device.getId(), null, DeviceStatus.AVAILABLE.name(),
                actorId, "创建设备");
        return DeviceVo.from(device);
    }

    @Override
    @Transactional
    public void update(Long deviceId, DeviceUpdateDto input, String username)
    {
        Objects.requireNonNull(input, "input");
        long id = requirePositive(deviceId);
        objectPermissionService.assertDeviceManageable(id);
        objectPermissionService.assertLaboratoryManageable(requirePositive(input.getLaboratoryId()));
        String categoryCode = requireActiveCategory(input.getCategoryCode());
        assertManagerCanManageLaboratory(input.getManagerId(), input.getLaboratoryId());
        LabDevice device = details(input.getAssetNo(), input.getLaboratoryId(), input.getName(),
                categoryCode, input.getModel(), requireActiveRisk(input.getRiskLevel()), input.getLocation(),
                input.getManagerId(), input.getDescription(), username);
        device.setId(id);
        if (deviceMapper.updateDetailsConditionally(device, input.getExpectedVersion()) != 1)
        {
            throw duplicateOperation();
        }
    }

    @Override
    public List<OccupiedRangeVo> occupiedRanges(Long deviceId, LocalDateTime from, LocalDateTime to)
    {
        long id = requirePositive(deviceId);
        if (from == null || to == null || !from.isBefore(to))
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "占用时间范围无效");
        }
        if (java.time.Duration.between(from, to).compareTo(java.time.Duration.ofDays(30)) > 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "占用时间范围不能超过三十天");
        }
        objectPermissionService.assertDeviceReadable(id);
        return reservationMapper.selectOccupiedRanges(id, from, to);
    }

    private String requireActiveRisk(String riskLevel)
    {
        String normalized = requireText(riskLevel);
        if (dictionaryMapper.countEnabledValue("lab_risk_level", normalized) <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "设备风险等级无效或已停用");
        }
        return normalized;
    }

    private String requireActiveCategory(String categoryCode)
    {
        String normalized = requireText(categoryCode);
        if (dictionaryMapper.countEnabledValue(DEVICE_CATEGORY_DICT_TYPE, normalized) <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "设备类别无效或已停用");
        }
        return normalized;
    }

    private void assertManagerCanManageLaboratory(Long managerId, Long laboratoryId)
    {
        userDirectory.assertActiveRole(managerId, "lab_manager");
        if (optionsMapper.countActiveUserLaboratoryScope(managerId, laboratoryId) <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR,
                    "所选设备负责人无权管理目标实验室");
        }
    }

    private static LabBusinessException missingLaboratory(LabDataScope scope, Long laboratoryId)
    {
        if (scope.allLaboratories() || scope.laboratoryIds().contains(laboratoryId))
        {
            return new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "实验室不存在");
        }
        return outOfScope();
    }

    private static LabDevice details(String assetNo, Long laboratoryId, String name,
            String categoryCode, String model, String riskLevel, String location,
            Long managerId, String description, String username)
    {
        LabDevice device = new LabDevice();
        device.setAssetNo(requireText(assetNo));
        device.setLaboratoryId(laboratoryId);
        device.setName(requireText(name));
        device.setCategoryCode(requireText(categoryCode));
        device.setModel(trimToNull(model));
        device.setRiskLevel(requireText(riskLevel));
        device.setLocation(requireText(location));
        device.setManagerId(managerId);
        device.setDescription(trimToNull(description));
        device.setUpdateBy(requireText(username));
        return device;
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
}
