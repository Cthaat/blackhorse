package com.ruoyi.lab.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import com.ruoyi.lab.domain.HazardSeverity;
import com.ruoyi.lab.domain.HazardStatus;
import com.ruoyi.lab.domain.HazardTargetType;
import com.ruoyi.lab.domain.InspectionResult;
import com.ruoyi.lab.domain.LabHazard;
import com.ruoyi.lab.domain.LabInspectionItem;
import com.ruoyi.lab.domain.LabInspectionTask;
import com.ruoyi.lab.domain.LabRectification;
import com.ruoyi.lab.dto.CreateHazardCommand;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabHazardMapper;
import com.ruoyi.lab.mapper.LabInspectionTaskMapper;
import com.ruoyi.lab.mapper.LabRectificationMapper;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.service.HazardService;
import com.ruoyi.lab.service.LabStatusHistoryService;
import com.ruoyi.lab.service.LabUserDirectory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HazardServiceImpl implements HazardService
{
    private static final DateTimeFormatter NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final LabHazardMapper hazardMapper;
    private final LabRectificationMapper rectificationMapper;
    private final LabInspectionTaskMapper taskMapper;
    private final LabDeviceMapper deviceMapper;
    private final HazardAffectedDeviceResolver affectedDeviceResolver;
    private final LabObjectPermissionService permissionService;
    private final LabDataScopeService dataScopeService;
    private final LabStatusHistoryService historyService;
    private final Clock clock;
    private final LabUserDirectory userDirectory;

    @Autowired
    public HazardServiceImpl(LabHazardMapper hazardMapper,
            LabRectificationMapper rectificationMapper, LabInspectionTaskMapper taskMapper,
            LabDeviceMapper deviceMapper, HazardAffectedDeviceResolver affectedDeviceResolver,
            LabObjectPermissionService permissionService, LabDataScopeService dataScopeService,
            LabStatusHistoryService historyService, Clock clock, LabUserDirectory userDirectory)
    {
        this.hazardMapper = hazardMapper;
        this.rectificationMapper = rectificationMapper;
        this.taskMapper = taskMapper;
        this.deviceMapper = deviceMapper;
        this.affectedDeviceResolver = affectedDeviceResolver;
        this.permissionService = permissionService;
        this.dataScopeService = dataScopeService;
        this.historyService = historyService;
        this.clock = clock;
        this.userDirectory = userDirectory;
    }

    public HazardServiceImpl(LabHazardMapper hazardMapper,
            LabRectificationMapper rectificationMapper, LabInspectionTaskMapper taskMapper,
            LabDeviceMapper deviceMapper, HazardAffectedDeviceResolver affectedDeviceResolver,
            LabObjectPermissionService permissionService, LabDataScopeService dataScopeService,
            LabStatusHistoryService historyService, Clock clock)
    {
        this(hazardMapper, rectificationMapper, taskMapper, deviceMapper, affectedDeviceResolver,
                permissionService, dataScopeService, historyService, clock, null);
    }

    @Override
    @Transactional
    public Long create(CreateHazardCommand command, Long actorId, String actorName)
    {
        validate(command);
        requireActor(actorId, actorName);
        assertTargetManageable(command.targetType(), command.targetId());
        List<Long> deviceIds = affectedDeviceResolver.resolveSorted(command.targetType(),
                command.targetId());
        deviceIds.forEach(deviceMapper::selectByIdForUpdate);
        LabHazard related = validateRelated(command);
        return insert(null, related == null ? null : related.getId(), command.targetType(),
                command.targetId(), command.severity(), command.ownerId(), command.deadline(),
                command.requirements(), actorId, actorName);
    }

    @Override
    @Transactional
    public Long createFromInspectionItem(LabInspectionItem item, Long actorId, String actorName)
    {
        requireActor(actorId, actorName);
        if (item == null || item.getId() == null || item.getResult() != InspectionResult.FAIL
                || item.getTargetType() == null || item.getTargetId() == null
                || item.getSeverity() == null || item.getDescription() == null
                || item.getDescription().isBlank())
        {
            throw validation("不合格巡检项参数无效");
        }
        LabHazard existing = hazardMapper.selectBySourceItem(item.getId());
        if (existing != null)
        {
            return existing.getId();
        }
        assertTargetManageable(item.getTargetType(), item.getTargetId());
        affectedDeviceResolver.resolveSorted(item.getTargetType(), item.getTargetId())
                .forEach(deviceMapper::selectByIdForUpdate);
        LabInspectionTask task = taskMapper.selectActiveById(item.getTaskId());
        if (task == null)
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "巡检任务不存在");
        }
        assertTargetInLaboratory(item.getTargetType(), item.getTargetId(), task.getLaboratoryId());
        try
        {
            return insert(item.getId(), null, item.getTargetType(), item.getTargetId(),
                    item.getSeverity(), task.getAssigneeId(), task.getDeadlineAt(),
                    item.getDescription(), actorId, actorName);
        }
        catch (DuplicateKeyException duplicate)
        {
            LabHazard winner = hazardMapper.selectBySourceItem(item.getId());
            if (winner != null)
            {
                return winner.getId();
            }
            throw duplicate;
        }
    }

    @Override
    public List<LabHazard> list(HazardStatus status, HazardSeverity severity, Long ownerId)
    {
        return hazardMapper.selectListByScope(dataScopeService.resolveCurrentScope(),
                permissionService.currentUserId(), ownerId, status, severity);
    }

    @Override
    public LabHazard get(Long hazardId)
    {
        LabHazard hazard = requireActive(hazardId);
        if (Objects.equals(hazard.getOwnerId(), permissionService.currentUserId()))
        {
            return hazard;
        }
        assertTargetReadable(hazard.getTargetType(), hazard.getTargetId());
        return hazard;
    }

    @Override
    public List<LabRectification> rectifications(Long hazardId)
    {
        get(hazardId);
        return rectificationMapper.selectByHazard(hazardId);
    }

    private Long insert(Long sourceItemId, Long relatedHazardId, HazardTargetType targetType,
            Long targetId, HazardSeverity severity, Long ownerId, LocalDateTime deadline,
            String requirements, Long actorId, String actorName)
    {
        assertHazardOwner(ownerId);
        LocalDateTime now = LocalDateTime.now(clock);
        LabHazard hazard = new LabHazard();
        hazard.setHazardNo("HZ" + NUMBER_TIME.format(now)
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
        hazard.setSourceItemId(sourceItemId);
        hazard.setRelatedHazardId(relatedHazardId);
        hazard.setTargetType(targetType);
        hazard.setTargetId(targetId);
        hazard.setSeverity(severity);
        hazard.setOwnerId(ownerId);
        hazard.setDeadline(deadline);
        hazard.setRequirements(requirements.trim());
        hazard.setStatus(HazardStatus.PENDING_RECTIFICATION);
        hazard.setOverdueFlag("0");
        hazard.setVersion(0);
        hazard.setCreateBy(actorName);
        hazard.setCreateTime(now);
        hazard.setDelFlag("0");
        hazardMapper.insert(hazard);
        historyService.append("HAZARD", hazard.getId(), null,
                HazardStatus.PENDING_RECTIFICATION.name(), actorId, "登记实验室安全隐患");
        return hazard.getId();
    }

    private LabHazard validateRelated(CreateHazardCommand command)
    {
        if (command.relatedHazardId() == null)
        {
            return null;
        }
        LabHazard related = hazardMapper.selectActiveById(command.relatedHazardId());
        if (related == null || related.getStatus() != HazardStatus.CLOSED
                || related.getTargetType() != command.targetType()
                || !related.getTargetId().equals(command.targetId()))
        {
            throw validation("关联隐患必须是同一目标的已销号记录");
        }
        return related;
    }

    private static void validate(CreateHazardCommand command)
    {
        if (command == null || command.targetType() == null || command.targetId() == null
                || command.targetId() <= 0 || command.severity() == null
                || command.ownerId() == null || command.ownerId() <= 0 || command.deadline() == null
                || command.requirements() == null || command.requirements().isBlank()
                || command.requirements().trim().length() > 2000)
        {
            throw validation("隐患登记参数无效");
        }
    }

    private void requireActor(Long actorId, String actorName)
    {
        if (actorId == null || actorId <= 0 || actorName == null || actorName.isBlank()
                || permissionService.currentUserId() != actorId)
        {
            throw new LabBusinessException(LabErrorCode.ACCESS_DENIED, "当前用户无权执行该操作");
        }
    }

    private void assertTargetManageable(HazardTargetType type, Long id)
    {
        if (type == HazardTargetType.LABORATORY) permissionService.assertLaboratoryManageable(id);
        else permissionService.assertDeviceManageable(id);
    }

    private void assertTargetReadable(HazardTargetType type, Long id)
    {
        if (type == HazardTargetType.LABORATORY) permissionService.assertLaboratoryReadable(id);
        else permissionService.assertDeviceReadable(id);
    }

    private void assertTargetInLaboratory(HazardTargetType type, Long targetId, Long laboratoryId)
    {
        if (type == HazardTargetType.LABORATORY)
        {
            if (!laboratoryId.equals(targetId))
            {
                throw validation("巡检隐患目标不属于当前任务实验室");
            }
            return;
        }
        com.ruoyi.lab.domain.LabDevice device = deviceMapper.selectById(targetId);
        if (device == null || !laboratoryId.equals(device.getLaboratoryId()))
        {
            throw validation("巡检隐患目标不属于当前任务实验室");
        }
    }

    private void assertHazardOwner(Long ownerId)
    {
        if (userDirectory == null)
        {
            throw new IllegalStateException("实验室用户目录未配置");
        }
        userDirectory.assertActiveBusinessParticipant(ownerId);
    }

    private LabHazard requireActive(Long hazardId)
    {
        if (hazardId == null || hazardId <= 0) throw validation("隐患编号无效");
        LabHazard hazard = hazardMapper.selectActiveById(hazardId);
        if (hazard == null) throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "隐患记录不存在");
        return hazard;
    }

    private static LabBusinessException validation(String message) { return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, message); }
}
