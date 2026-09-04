package com.ruoyi.lab.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import com.ruoyi.lab.domain.DeviceStatus;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.domain.LabRepairOrder;
import com.ruoyi.lab.domain.LabUsageRecord;
import com.ruoyi.lab.domain.RepairSourceType;
import com.ruoyi.lab.domain.RepairStatus;
import com.ruoyi.lab.dto.AcceptRepairCommand;
import com.ruoyi.lab.dto.AssignRepairCommand;
import com.ruoyi.lab.dto.ReportFaultCommand;
import com.ruoyi.lab.dto.SubmitRepairResultCommand;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabRepairOrderMapper;
import com.ruoyi.lab.mapper.LabUsageRecordMapper;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.service.DeviceAvailabilityService;
import com.ruoyi.lab.service.LabStatusHistoryService;
import com.ruoyi.lab.service.RepairOrderService;
import com.ruoyi.lab.service.RepairWorkerDirectory;
import com.ruoyi.lab.vo.RepairOrderVo;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Device-first repair reporting, processing and acceptance workflow. */
@Service
public class RepairOrderServiceImpl implements RepairOrderService
{
    private final LabRepairOrderMapper repairMapper;
    private final LabDeviceMapper deviceMapper;
    private final LabUsageRecordMapper usageMapper;
    private final LabObjectPermissionService objectPermissionService;
    private final RepairWorkerDirectory workerDirectory;
    private final DeviceAvailabilityService availabilityService;
    private final LabStatusHistoryService historyService;
    private final RepairNumberGenerator numberGenerator;
    private final Clock clock;

    public RepairOrderServiceImpl(LabRepairOrderMapper repairMapper,
            LabDeviceMapper deviceMapper, LabUsageRecordMapper usageMapper,
            LabObjectPermissionService objectPermissionService,
            RepairWorkerDirectory workerDirectory,
            DeviceAvailabilityService availabilityService,
            LabStatusHistoryService historyService,
            RepairNumberGenerator numberGenerator, Clock clock)
    {
        this.repairMapper = repairMapper;
        this.deviceMapper = deviceMapper;
        this.usageMapper = usageMapper;
        this.objectPermissionService = objectPermissionService;
        this.workerDirectory = workerDirectory;
        this.availabilityService = availabilityService;
        this.historyService = historyService;
        this.numberGenerator = numberGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public LabRepairOrder openOrGetFromAbnormalReturn(LabUsageRecord usage,
            String description, Long reporterId)
    {
        if (usage == null || usage.getId() == null || usage.getDeviceId() == null)
        {
            throw validation("异常归还记录无效");
        }
        return openOrGet(usage.getDeviceId(), RepairSourceType.ABNORMAL_RETURN,
                usage.getId(), requirePositive(reporterId, "报修用户编号无效"),
                requiredText(description, 1000, "故障描述不能为空"),
                "异常归还自动报修");
    }

    @Override
    @Transactional
    public RepairOrderVo reportFault(ReportFaultCommand command, Long reporterId)
    {
        if (command == null)
        {
            throw validation("报修命令不能为空");
        }
        long actorId = requirePositive(reporterId, "报修用户编号无效");
        long deviceId = requirePositive(command.deviceId(), "设备编号无效");
        String description = requiredText(command.description(), 1000, "故障描述不能为空");
        LabDevice device = requireDeviceForUpdate(deviceId);
        objectPermissionService.assertDeviceReadable(deviceId);
        DeviceStatus current = device.getStatus();
        if (current != DeviceStatus.AVAILABLE && current != DeviceStatus.IN_USE
                && current != DeviceStatus.FAULT)
        {
            throw illegalState("设备当前不能提交报修");
        }
        if (current != DeviceStatus.FAULT)
        {
            requireOne(deviceMapper.updateStatusConditionally(deviceId, current.name(),
                    DeviceStatus.FAULT.name()));
            historyService.append("DEVICE", deviceId, current.name(), DeviceStatus.FAULT.name(),
                    actorId, "用户主动提交报修");
        }
        return RepairOrderVo.from(openOrGet(deviceId, RepairSourceType.ACTIVE_REPORT,
                null, actorId, description, "用户主动提交报修"));
    }

    @Override
    @Transactional
    public RepairOrderVo assign(Long orderId, AssignRepairCommand command, Long managerId)
    {
        long id = requirePositive(orderId, "维修工单编号无效");
        long actorId = requirePositive(managerId, "操作用户编号无效");
        if (command == null)
        {
            throw validation("维修分派命令不能为空");
        }
        long assigneeId = requirePositive(command.assigneeId(), "维修人员编号无效");
        LabRepairOrder snapshot = requireOrder(id);
        LabDevice device = requireDeviceForUpdate(snapshot.getDeviceId());
        LabRepairOrder locked = requireOrderForUpdate(id);
        requireSameDevice(device, snapshot, locked);
        objectPermissionService.assertDeviceManageable(device.getId());
        workerDirectory.assertRepairWorker(assigneeId);
        requireStatus(locked, RepairStatus.WAIT_ASSIGN);
        LocalDateTime now = LocalDateTime.now(clock);
        requireOne(repairMapper.assignConditionally(id, RepairStatus.WAIT_ASSIGN.name(),
                RepairStatus.WAIT_REPAIR.name(), assigneeId, actorId, now,
                Long.toString(actorId)));
        historyService.append("REPAIR_ORDER", id, RepairStatus.WAIT_ASSIGN.name(),
                RepairStatus.WAIT_REPAIR.name(), actorId, "维修工单完成分派");
        return RepairOrderVo.from(requireOrder(id));
    }

    @Override
    @Transactional
    public RepairOrderVo start(Long orderId, Long repairerId)
    {
        long id = requirePositive(orderId, "维修工单编号无效");
        long actorId = requirePositive(repairerId, "维修人员编号无效");
        LabRepairOrder snapshot = requireOrder(id);
        LabDevice device = requireDeviceForUpdate(snapshot.getDeviceId());
        if (!usageMapper.selectUnreturnedIdsByDeviceIdForUpdate(device.getId()).isEmpty())
        {
            throw new LabBusinessException(LabErrorCode.LAB_DEVICE_UNAVAILABLE,
                    "设备仍有未归还使用记录");
        }
        LabRepairOrder locked = requireOrderForUpdate(id);
        requireSameDevice(device, snapshot, locked);
        requireAssignee(locked, actorId);
        requireStatus(locked, RepairStatus.WAIT_REPAIR);
        if (device.getStatus() != DeviceStatus.FAULT)
        {
            throw illegalState("设备当前不能开始维修");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        requireOne(repairMapper.startConditionally(id, RepairStatus.WAIT_REPAIR.name(),
                RepairStatus.IN_PROGRESS.name(), actorId, now, Long.toString(actorId)));
        requireOne(deviceMapper.updateStatusConditionally(device.getId(),
                DeviceStatus.FAULT.name(), DeviceStatus.MAINTENANCE.name()));
        historyService.append("REPAIR_ORDER", id, RepairStatus.WAIT_REPAIR.name(),
                RepairStatus.IN_PROGRESS.name(), actorId, "维修人员开始处理");
        historyService.append("DEVICE", device.getId(), DeviceStatus.FAULT.name(),
                DeviceStatus.MAINTENANCE.name(), actorId, "设备进入维修状态");
        return RepairOrderVo.from(requireOrder(id));
    }

    @Override
    @Transactional
    public RepairOrderVo submitResult(Long orderId, SubmitRepairResultCommand command,
            Long repairerId)
    {
        long id = requirePositive(orderId, "维修工单编号无效");
        long actorId = requirePositive(repairerId, "维修人员编号无效");
        if (command == null)
        {
            throw validation("维修结果命令不能为空");
        }
        String result = requiredText(command.result(), 2000, "维修结果不能为空");
        LabRepairOrder snapshot = requireOrder(id);
        LabDevice device = requireDeviceForUpdate(snapshot.getDeviceId());
        LabRepairOrder locked = requireOrderForUpdate(id);
        requireSameDevice(device, snapshot, locked);
        requireAssignee(locked, actorId);
        requireStatus(locked, RepairStatus.IN_PROGRESS);
        if (device.getStatus() != DeviceStatus.MAINTENANCE)
        {
            throw illegalState("设备当前不在维修状态");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        requireOne(repairMapper.submitResultConditionally(id,
                RepairStatus.IN_PROGRESS.name(), RepairStatus.WAIT_ACCEPTANCE.name(),
                actorId, result, now, Long.toString(actorId)));
        historyService.append("REPAIR_ORDER", id, RepairStatus.IN_PROGRESS.name(),
                RepairStatus.WAIT_ACCEPTANCE.name(), actorId, "维修人员提交处理结果");
        return RepairOrderVo.from(requireOrder(id));
    }

    @Override
    @Transactional
    public RepairOrderVo accept(Long orderId, AcceptRepairCommand command, Long managerId)
    {
        long id = requirePositive(orderId, "维修工单编号无效");
        long actorId = requirePositive(managerId, "操作用户编号无效");
        if (command == null)
        {
            throw validation("维修验收命令不能为空");
        }
        String reason = requiredText(command.reason(), 1000, "维修验收原因不能为空");
        LabRepairOrder snapshot = requireOrder(id);
        LabDevice device = requireDeviceForUpdate(snapshot.getDeviceId());
        LabRepairOrder locked = requireOrderForUpdate(id);
        requireSameDevice(device, snapshot, locked);
        objectPermissionService.assertDeviceManageable(device.getId());
        if (Objects.equals(locked.getAssigneeId(), actorId))
        {
            throw new LabBusinessException(LabErrorCode.ACCESS_DENIED, "维修人员不能验收本人结果");
        }
        requireStatus(locked, RepairStatus.WAIT_ACCEPTANCE);
        if (device.getStatus() != DeviceStatus.MAINTENANCE)
        {
            throw illegalState("设备当前不在维修状态");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        RepairStatus target = command.passed() ? RepairStatus.CLOSED : RepairStatus.IN_PROGRESS;
        String result = command.passed() ? "PASSED" : "REJECTED";
        requireOne(repairMapper.saveAcceptanceConditionally(id,
                RepairStatus.WAIT_ACCEPTANCE.name(), target.name(), result, reason,
                actorId, now, Long.toString(actorId)));
        historyService.append("REPAIR_ORDER", id, RepairStatus.WAIT_ACCEPTANCE.name(),
                target.name(), actorId,
                command.passed() ? "维修结果验收通过" : "维修结果验收退回");
        if (command.passed())
        {
            availabilityService.restoreAfterRepair(device.getId(), actorId);
        }
        return RepairOrderVo.from(requireOrder(id));
    }

    private LabRepairOrder openOrGet(long deviceId, RepairSourceType sourceType,
            Long sourceId, long reporterId, String description, String historyReason)
    {
        LabRepairOrder open = repairMapper.selectOpenByDeviceIdForUpdate(deviceId);
        if (open != null)
        {
            return open;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LabRepairOrder created = new LabRepairOrder();
        created.setRepairNo(numberGenerator.next());
        created.setDeviceId(deviceId);
        created.setSourceType(sourceType);
        created.setSourceId(sourceId);
        created.setReporterId(reporterId);
        created.setFaultDescription(description);
        created.setStatus(RepairStatus.WAIT_ASSIGN);
        created.setVersion(0);
        created.setCreateBy(Long.toString(reporterId));
        created.setCreateTime(now);
        created.setUpdateBy(Long.toString(reporterId));
        created.setUpdateTime(now);
        created.setDelFlag("0");
        try
        {
            repairMapper.insert(created);
        }
        catch (DuplicateKeyException exception)
        {
            LabRepairOrder concurrent = repairMapper.selectOpenByDeviceIdForUpdate(deviceId);
            if (concurrent != null)
            {
                return concurrent;
            }
            throw new LabBusinessException(LabErrorCode.LAB_REPAIR_ALREADY_OPEN,
                    "设备已有开放维修工单");
        }
        historyService.append("REPAIR_ORDER", created.getId(), null,
                RepairStatus.WAIT_ASSIGN.name(), reporterId, historyReason);
        return created;
    }

    private LabDevice requireDeviceForUpdate(Long deviceId)
    {
        LabDevice device = deviceMapper.selectByIdForUpdate(deviceId);
        if (device == null)
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "设备不存在");
        }
        return device;
    }

    private LabRepairOrder requireOrder(long orderId)
    {
        LabRepairOrder order = repairMapper.selectActiveById(orderId);
        if (order == null)
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "维修工单不存在");
        }
        return order;
    }

    private LabRepairOrder requireOrderForUpdate(long orderId)
    {
        LabRepairOrder order = repairMapper.selectByIdForUpdate(orderId);
        if (order == null)
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "维修工单不存在");
        }
        return order;
    }

    private static void requireSameDevice(LabDevice device, LabRepairOrder snapshot,
            LabRepairOrder locked)
    {
        if (!Objects.equals(device.getId(), snapshot.getDeviceId())
                || !Objects.equals(device.getId(), locked.getDeviceId()))
        {
            throw duplicateOperation();
        }
    }

    private static void requireAssignee(LabRepairOrder order, long actorId)
    {
        if (!Objects.equals(order.getAssigneeId(), actorId))
        {
            throw new LabBusinessException(LabErrorCode.ACCESS_DENIED, "只能处理分派给本人的维修工单");
        }
    }

    private static void requireStatus(LabRepairOrder order, RepairStatus expected)
    {
        if (order.getStatus() != expected)
        {
            throw illegalState("维修工单状态不允许当前操作");
        }
    }

    private static void requireOne(int rows)
    {
        if (rows != 1)
        {
            throw duplicateOperation();
        }
    }

    private static long requirePositive(Long value, String message)
    {
        if (value == null || value <= 0)
        {
            throw validation(message);
        }
        return value;
    }

    private static String requiredText(String value, int max, String message)
    {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > max)
        {
            throw validation(message);
        }
        return normalized;
    }

    private static LabBusinessException validation(String message)
    {
        return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, message);
    }

    private static LabBusinessException illegalState(String message)
    {
        return new LabBusinessException(LabErrorCode.LAB_ILLEGAL_STATE_TRANSITION, message);
    }

    private static LabBusinessException duplicateOperation()
    {
        return new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION,
                "操作已被其他请求处理");
    }
}
