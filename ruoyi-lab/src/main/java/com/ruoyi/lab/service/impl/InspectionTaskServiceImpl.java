package com.ruoyi.lab.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.service.LabPage;
import java.util.Objects;
import com.ruoyi.lab.domain.InspectionResult;
import com.ruoyi.lab.domain.InspectionTaskStatus;
import com.ruoyi.lab.domain.LabInspectionItem;
import com.ruoyi.lab.domain.LabInspectionTask;
import com.ruoyi.lab.dto.RecordInspectionItemCommand;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabInspectionItemMapper;
import com.ruoyi.lab.mapper.LabInspectionTaskMapper;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.service.HazardService;
import com.ruoyi.lab.service.InspectionTaskService;
import com.ruoyi.lab.service.LabStatusHistoryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionTaskServiceImpl implements InspectionTaskService
{
    private final LabInspectionTaskMapper taskMapper;
    private final LabInspectionItemMapper itemMapper;
    private final LabDeviceMapper deviceMapper;
    private final HazardAffectedDeviceResolver deviceResolver;
    private final HazardService hazardService;
    private final LabDataScopeService dataScopeService;
    private final LabObjectPermissionService permissionService;
    private final LabStatusHistoryService historyService;
    private final Clock clock;

    public InspectionTaskServiceImpl(LabInspectionTaskMapper taskMapper,
            LabInspectionItemMapper itemMapper, LabDeviceMapper deviceMapper,
            HazardAffectedDeviceResolver deviceResolver, HazardService hazardService,
            LabDataScopeService dataScopeService, LabObjectPermissionService permissionService,
            LabStatusHistoryService historyService, Clock clock)
    {
        this.taskMapper = taskMapper;
        this.itemMapper = itemMapper;
        this.deviceMapper = deviceMapper;
        this.deviceResolver = deviceResolver;
        this.hazardService = hazardService;
        this.dataScopeService = dataScopeService;
        this.permissionService = permissionService;
        this.historyService = historyService;
        this.clock = clock;
    }

    @Override
    public List<LabInspectionTask> list(InspectionTaskStatus status, Long assigneeId)
    {
        var scope = dataScopeService.resolveCurrentScope();
        return LabPage.query(() -> taskMapper.selectListByScope(scope, assigneeId, status));
    }

    @Override
    public LabInspectionTask get(Long taskId)
    {
        LabInspectionTask task = requireTask(taskId);
        permissionService.assertLaboratoryReadable(task.getLaboratoryId());
        return task;
    }

    @Override
    public List<LabInspectionItem> items(Long taskId)
    {
        get(taskId);
        return itemMapper.selectByTask(taskId);
    }

    @Override
    @Transactional
    public void start(Long taskId, Long actorId, String actorName)
    {
        requireActor(actorId, actorName);
        LabInspectionTask task = requireLocked(taskId);
        permissionService.assertLaboratoryReadable(task.getLaboratoryId());
        requireAssignee(task, actorId);
        if (task.getStatus() != InspectionTaskStatus.PENDING)
        {
            throw duplicate();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (taskMapper.startConditionally(taskId, task.getVersion(), now, actorName) != 1)
        {
            throw duplicate();
        }
        historyService.append("INSPECTION_TASK", taskId, InspectionTaskStatus.PENDING.name(),
                InspectionTaskStatus.IN_PROGRESS.name(), actorId, "开始执行巡检任务");
    }

    @Override
    @Transactional
    public void recordItem(Long taskId, Long itemId, RecordInspectionItemCommand command,
            Long actorId)
    {
        if (command == null || command.result() == null || command.version() == null
                || command.version() < 0)
        {
            throw validation("巡检结果参数无效");
        }
        LabInspectionTask task = requireLocked(taskId);
        permissionService.assertLaboratoryReadable(task.getLaboratoryId());
        requireAssignee(task, actorId);
        if (task.getStatus() != InspectionTaskStatus.IN_PROGRESS)
        {
            throw illegal("巡检任务当前状态不能记录检查项");
        }
        LabInspectionItem item = itemMapper.selectForUpdate(itemId);
        if (item == null || !Objects.equals(item.getTaskId(), taskId))
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "巡检检查项不存在");
        }
        item.setResult(command.result());
        item.setInspectedBy(actorId);
        if (command.result() == InspectionResult.FAIL)
        {
            if (command.description() == null || command.description().isBlank()
                    || command.severity() == null || command.targetType() == null
                    || command.targetId() == null || command.targetId() <= 0)
            {
                throw validation("不合格检查项必须填写隐患信息");
            }
            if (command.description().trim().length() > 1000)
            {
                throw validation("巡检问题描述长度无效");
            }
            if (command.targetType().name().equals("DEVICE"))
            {
                permissionService.assertDeviceReadable(command.targetId());
                com.ruoyi.lab.domain.LabDevice target = deviceMapper.selectById(command.targetId());
                if (target == null || !Objects.equals(target.getLaboratoryId(), task.getLaboratoryId()))
                    throw validation("巡检隐患目标不属于当前任务实验室");
            }
            else
            {
                if (!Objects.equals(command.targetId(), task.getLaboratoryId()))
                    throw validation("巡检隐患目标不属于当前任务实验室");
                permissionService.assertLaboratoryReadable(command.targetId());
            }
            item.setDescription(command.description().trim());
            item.setSeverity(command.severity());
            item.setTargetType(command.targetType());
            item.setTargetId(command.targetId());
        }
        else
        {
            item.setDescription(null); item.setSeverity(null); item.setTargetType(null); item.setTargetId(null);
        }
        if (itemMapper.recordConditionally(item, command.version(), LocalDateTime.now(clock)) != 1)
        {
            throw duplicate();
        }
    }

    @Override
    @Transactional
    public void complete(Long taskId, Long actorId, String actorName)
    {
        requireActor(actorId, actorName);
        LabInspectionTask snapshot = requireTask(taskId);
        requireAssignee(snapshot, actorId);
        itemMapper.selectByTask(taskId).stream().filter(i -> i.getResult() == InspectionResult.FAIL)
                .flatMap(i -> deviceResolver.resolveSorted(i.getTargetType(), i.getTargetId()).stream())
                .distinct().sorted().forEach(deviceMapper::selectByIdForUpdate);
        LabInspectionTask task = requireLocked(taskId);
        permissionService.assertLaboratoryReadable(task.getLaboratoryId());
        requireAssignee(task, actorId);
        if (task.getStatus() != InspectionTaskStatus.IN_PROGRESS) throw duplicate();
        List<LabInspectionItem> items = itemMapper.selectByTaskForUpdate(taskId);
        if (items.isEmpty() || items.stream().anyMatch(i -> i.getResult() == null))
        {
            throw validation("巡检检查项尚未全部完成");
        }
        for (LabInspectionItem item : items)
        {
            if (item.getResult() == InspectionResult.FAIL)
                hazardService.createFromInspectionItem(item, actorId, actorName);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (taskMapper.completeConditionally(taskId, task.getVersion(), now, actorName) != 1)
            throw duplicate();
        historyService.append("INSPECTION_TASK", taskId, InspectionTaskStatus.IN_PROGRESS.name(),
                InspectionTaskStatus.COMPLETED.name(), actorId, "提交巡检任务结果");
    }

    private LabInspectionTask requireTask(Long id)
    {
        if (id == null || id <= 0) throw validation("巡检任务编号无效");
        LabInspectionTask task = taskMapper.selectActiveById(id);
        if (task == null) throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "巡检任务不存在");
        return task;
    }
    private LabInspectionTask requireLocked(Long id)
    {
        if (id == null || id <= 0) throw validation("巡检任务编号无效");
        LabInspectionTask task = taskMapper.selectForUpdate(id);
        if (task == null) throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "巡检任务不存在");
        return task;
    }
    private void requireActor(Long id, String name)
    {
        if (id == null || id <= 0 || name == null || name.isBlank() || permissionService.currentUserId() != id)
            throw new LabBusinessException(LabErrorCode.ACCESS_DENIED, "当前用户无权执行该操作");
    }
    private static void requireAssignee(LabInspectionTask task, Long actorId)
    {
        if (!Objects.equals(task.getAssigneeId(), actorId))
            throw new LabBusinessException(LabErrorCode.ACCESS_DENIED, "仅巡检负责人可以执行该任务");
    }
    private static LabBusinessException validation(String m) { return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, m); }
    private static LabBusinessException illegal(String m) { return new LabBusinessException(LabErrorCode.LAB_ILLEGAL_STATE_TRANSITION, m); }
    private static LabBusinessException duplicate() { return new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION, "操作已被其他请求处理"); }
}
