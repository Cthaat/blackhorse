package com.ruoyi.lab.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import com.ruoyi.lab.domain.DeviceStatus;
import com.ruoyi.lab.domain.HazardSeverity;
import com.ruoyi.lab.domain.HazardStatus;
import com.ruoyi.lab.domain.HazardTargetType;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.domain.LabHazard;
import com.ruoyi.lab.domain.LabLaboratory;
import com.ruoyi.lab.domain.LabRectification;
import com.ruoyi.lab.domain.LaboratoryStatus;
import com.ruoyi.lab.domain.RectificationReviewResult;
import com.ruoyi.lab.dto.ReviewRectificationCommand;
import com.ruoyi.lab.dto.SubmitRectificationCommand;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabHazardMapper;
import com.ruoyi.lab.mapper.LabLaboratoryMapper;
import com.ruoyi.lab.mapper.LabRectificationMapper;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.service.LabStatusHistoryService;
import com.ruoyi.lab.service.RectificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RectificationServiceImpl implements RectificationService
{
    private final LabHazardMapper hazardMapper;
    private final LabRectificationMapper rectificationMapper;
    private final LabDeviceMapper deviceMapper;
    private final LabLaboratoryMapper laboratoryMapper;
    private final HazardAffectedDeviceResolver resolver;
    private final LabObjectPermissionService permissionService;
    private final LabStatusHistoryService historyService;
    private final Clock clock;
    private final com.ruoyi.lab.maintenance.MaintenanceWindowGuard maintenance;

    public RectificationServiceImpl(LabHazardMapper hazardMapper,
            LabRectificationMapper rectificationMapper, LabDeviceMapper deviceMapper,
            LabLaboratoryMapper laboratoryMapper, HazardAffectedDeviceResolver resolver,
            LabObjectPermissionService permissionService, LabStatusHistoryService historyService,
            Clock clock, com.ruoyi.lab.maintenance.MaintenanceWindowGuard maintenance)
    {
        this.hazardMapper = hazardMapper;
        this.rectificationMapper = rectificationMapper;
        this.deviceMapper = deviceMapper;
        this.laboratoryMapper = laboratoryMapper;
        this.resolver = resolver;
        this.permissionService = permissionService;
        this.historyService = historyService;
        this.clock = clock;
        this.maintenance = maintenance;
    }

    @Override
    @Transactional
    public void start(Long hazardId, Long actorId, String actorName)
    {
        requireActor(actorId, actorName);
        LabHazard hazard = requireLocked(hazardId);
        requireOwnerOrManager(hazard, actorId);
        move(hazard, HazardStatus.PENDING_RECTIFICATION, HazardStatus.RECTIFYING,
                actorId, actorName, "开始隐患整改");
    }

    @Override
    @Transactional
    public Long submit(Long hazardId, SubmitRectificationCommand command, Long actorId,
            String actorName)
    {
        requireActor(actorId, actorName);
        String description = requireText(command == null ? null : command.description(), 2000,
                "整改说明不能为空");
        LabHazard hazard = requireLocked(hazardId);
        requireOwnerOrManager(hazard, actorId);
        if (hazard.getStatus() != HazardStatus.RECTIFYING) throw duplicate();
        LocalDateTime now = LocalDateTime.now(clock);
        LabRectification round = new LabRectification();
        round.setHazardId(hazardId);
        round.setRoundNo(rectificationMapper.selectMaxRound(hazardId) + 1);
        round.setSubmitterId(actorId);
        round.setDescription(description);
        round.setSubmittedAt(now);
        round.setCreateTime(now);
        round.setVersion(0);
        round.setDelFlag("0");
        rectificationMapper.insert(round);
        move(hazard, HazardStatus.RECTIFYING, HazardStatus.PENDING_REVIEW,
                actorId, actorName, "提交隐患整改复查");
        return round.getId();
    }

    @Override
    @Transactional
    public void review(Long hazardId, Long rectificationId, ReviewRectificationCommand command,
            Long reviewerId, String reviewerName)
    {
        requireActor(reviewerId, reviewerName);
        String reason = requireText(command == null ? null : command.reason(), 1000,
                "复查原因不能为空");
        LabHazard snapshot = requireActive(hazardId);
        assertTargetManageable(snapshot);
        List<Long> deviceIds = resolver.resolveSorted(snapshot.getTargetType(), snapshot.getTargetId());
        deviceIds.forEach(deviceMapper::selectByIdForUpdate);
        LabHazard hazard = requireLocked(hazardId);
        LabRectification round = rectificationMapper.selectForUpdate(rectificationId);
        if (round == null || !Objects.equals(round.getHazardId(), hazardId))
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "整改轮次不存在");
        if (Objects.equals(round.getSubmitterId(), reviewerId))
            throw new LabBusinessException(LabErrorCode.ACCESS_DENIED, "整改提交人不能复查本人整改");
        if (hazard.getStatus() != HazardStatus.PENDING_REVIEW) throw duplicate();
        LocalDateTime now = LocalDateTime.now(clock);
        RectificationReviewResult result = command.passed()
                ? RectificationReviewResult.PASSED : RectificationReviewResult.REJECTED;
        if (rectificationMapper.reviewConditionally(round.getId(), reviewerId, result.name(),
                reason, now, round.getVersion()) != 1) throw duplicate();
        HazardStatus target = command.passed() ? HazardStatus.CLOSED : HazardStatus.RECTIFYING;
        move(hazard, HazardStatus.PENDING_REVIEW, target, reviewerId, reviewerName, reason);
        if (command.passed() && hazard.getSeverity() == HazardSeverity.MAJOR)
            restoreAvailableDevices(deviceIds, reviewerId);
    }

    private void restoreAvailableDevices(List<Long> deviceIds, Long actorId)
    {
        for (Long deviceId : deviceIds)
        {
            LabDevice device = deviceMapper.selectByIdForUpdate(deviceId);
            if (device == null || device.getStatus() != DeviceStatus.MAINTENANCE) continue;
            LabLaboratory laboratory = laboratoryMapper.selectById(device.getLaboratoryId());
            if (laboratory == null || laboratory.getStatus() != LaboratoryStatus.ENABLED
                    || hazardMapper.countOpenUsageForDevice(deviceId) > 0
                    || hazardMapper.countOpenRepairForDevice(deviceId) > 0
                    || maintenance.blocksNow(deviceId)
                    || !hazardMapper.selectOpenMajorHazardIdsForDeviceForUpdate(deviceId).isEmpty())
                continue;
            if (deviceMapper.updateStatusConditionally(deviceId, DeviceStatus.MAINTENANCE.name(),
                    DeviceStatus.AVAILABLE.name()) == 1)
                historyService.append("DEVICE", deviceId, DeviceStatus.MAINTENANCE.name(),
                        DeviceStatus.AVAILABLE.name(), actorId, "重大隐患销号后恢复设备可用");
        }
    }

    private void move(LabHazard hazard, HazardStatus expected, HazardStatus target,
            Long actorId, String actorName, String reason)
    {
        if (hazard.getStatus() != expected || !expected.canMoveTo(target)) throw duplicate();
        LocalDateTime now = LocalDateTime.now(clock);
        if (hazardMapper.updateStatusConditionally(hazard.getId(), expected.name(), target.name(),
                actorName, now) != 1) throw duplicate();
        historyService.append("HAZARD", hazard.getId(), expected.name(), target.name(), actorId, reason);
    }

    private void requireOwnerOrManager(LabHazard hazard, Long actorId)
    {
        if (!Objects.equals(hazard.getOwnerId(), actorId)) assertTargetManageable(hazard);
    }
    private void assertTargetManageable(LabHazard hazard)
    {
        if (hazard.getTargetType() == HazardTargetType.LABORATORY)
            permissionService.assertLaboratoryManageable(hazard.getTargetId());
        else permissionService.assertDeviceManageable(hazard.getTargetId());
    }
    private LabHazard requireActive(Long id)
    {
        if (id == null || id <= 0) throw validation("隐患编号无效");
        LabHazard hazard = hazardMapper.selectActiveById(id);
        if (hazard == null) throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "隐患记录不存在");
        return hazard;
    }
    private LabHazard requireLocked(Long id)
    {
        if (id == null || id <= 0) throw validation("隐患编号无效");
        LabHazard hazard = hazardMapper.selectForUpdate(id);
        if (hazard == null) throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "隐患记录不存在");
        return hazard;
    }
    private void requireActor(Long id, String name)
    {
        if (id == null || id <= 0 || name == null || name.isBlank() || permissionService.currentUserId() != id)
            throw new LabBusinessException(LabErrorCode.ACCESS_DENIED, "当前用户无权执行该操作");
    }
    private static String requireText(String value, int max, String message)
    {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) throw validation(message);
        return normalized;
    }
    private static LabBusinessException validation(String m) { return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, m); }
    private static LabBusinessException duplicate() { return new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION, "操作已被其他请求处理"); }
}
