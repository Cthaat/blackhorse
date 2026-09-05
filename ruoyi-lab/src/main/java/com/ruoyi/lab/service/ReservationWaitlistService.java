package com.ruoyi.lab.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.domain.LabReservationWaitlist;
import com.ruoyi.lab.dto.ReservationApplyDto;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabReservationWaitlistMapper;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.vo.ReservationVo;
import com.ruoyi.lab.vo.ReservationWaitlistVo;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationWaitlistService
{
    private final LabReservationWaitlistMapper queue;
    private final LabDeviceMapper devices;
    private final LabObjectPermissionService permissions;
    private final ReservationPolicy policy;
    private final ReservationRequestHasher hasher;
    private final ReservationWaitlistCoordinator coordinator;
    private final ReservationCommandService reservations;
    private final Clock clock;

    public ReservationWaitlistService(LabReservationWaitlistMapper queue, LabDeviceMapper devices,
            LabObjectPermissionService permissions, ReservationPolicy policy, ReservationRequestHasher hasher,
            ReservationWaitlistCoordinator coordinator, ReservationCommandService reservations, Clock clock)
    {
        this.queue = queue;
        this.devices = devices;
        this.permissions = permissions;
        this.policy = policy;
        this.hasher = hasher;
        this.coordinator = coordinator;
        this.reservations = reservations;
        this.clock = clock;
    }

    public List<ReservationWaitlistVo> mine(Long deviceId, String status)
    {
        String filter = status == null || status.isBlank() ? null : status;
        if (filter != null && !Set.of("WAITING", "OFFERED", "ACCEPTED", "CANCELLED", "EXPIRED", "INELIGIBLE").contains(filter))
        {
            throw invalid("候补状态无效");
        }
        return LabPage.query(() -> queue.mine(permissions.currentUserId(), deviceId, filter), this::view);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationWaitlistVo join(String key, ReservationApplyDto request)
    {
        if (key == null || !key.matches("[A-Za-z0-9_-]{1,64}")) { throw invalid("幂等请求编号无效"); }
        var range = policy.validate(request);
        permissions.assertDeviceReadable(range.deviceId());
        Long userId = permissions.currentUserId();
        String hash = hasher.hash(range, null);
        LabDevice device = devices.selectByIdForUpdate(range.deviceId());
        if (device == null) { throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "设备不存在"); }
        LabReservationWaitlist replay = queue.byKey(userId, key);
        if (replay != null) { return replay(replay, hash); }
        coordinator.reconcileLocked(device);
        LabReservationWaitlist existing = queue.existing(userId, device.getId(), range.startTime(), range.endTime());
        if (existing != null) { return replay(existing, hash); }
        if (queue.activeCount(device.getId()) >= 200) { throw invalid("该设备候补队列已满，请稍后重试"); }
        LocalDateTime now = LocalDateTime.now(clock);
        LabReservationWaitlist row = new LabReservationWaitlist();
        row.setDeviceId(device.getId());
        row.setApplicantId(userId);
        row.setStartTime(range.startTime());
        row.setEndTime(range.endTime());
        row.setPurpose(range.purpose());
        row.setRemark(range.remark());
        row.setStatus("WAITING");
        row.setVersion(0);
        row.setIdempotencyKey(key);
        row.setRequestHash(hash);
        row.setCreateTime(now);
        row.setUpdateTime(now);
        coordinator.validateCandidate(device, row);
        try
        {
            queue.insert(row);
        }
        catch (DuplicateKeyException collision)
        {
            LabReservationWaitlist concurrent = queue.byKey(userId, key);
            if (concurrent == null) { throw collision; }
            return replay(concurrent, hash);
        }
        coordinator.reconcileLocked(device);
        return view(queue.selectById(row.getId()));
    }

    /** Commit expiry/promotion before a rejected claim, so the UI can refresh actual state. */
    public ReservationVo confirm(Long id, int version)
    {
        LabReservationWaitlist row = owned(id);
        coordinator.advanceDevice(row.getDeviceId());
        return reservations.confirmWaitlist(id, version, permissions.currentUserId());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationWaitlistVo cancel(Long id, int version)
    {
        LabReservationWaitlist original = owned(id);
        LabDevice device = devices.selectByIdForUpdate(original.getDeviceId());
        if (device == null) { throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "设备不存在"); }
        LabReservationWaitlist row = queue.locked(id);
        if (row == null || row.getVersion() != version || !Set.of("WAITING", "OFFERED").contains(row.getStatus()))
        {
            throw new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION, "候补状态已变化，请刷新");
        }
        coordinator.change(row, "CANCELLED", "申请人退出候补", null, null, LocalDateTime.now(clock));
        coordinator.reconcileLocked(device);
        return view(row);
    }

    private LabReservationWaitlist owned(Long id)
    {
        LabReservationWaitlist row = queue.selectById(id);
        if (row == null || !Objects.equals(row.getApplicantId(), permissions.currentUserId()))
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "候补记录不存在");
        }
        return row;
    }

    private ReservationWaitlistVo replay(LabReservationWaitlist row, String hash)
    {
        if (!Objects.equals(hash, row.getRequestHash()))
        {
            throw new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION, "重复请求的候补内容不一致");
        }
        return view(row);
    }

    private ReservationWaitlistVo view(LabReservationWaitlist row)
    {
        Integer position = Set.of("WAITING", "OFFERED").contains(row.getStatus()) ? queue.position(row) : null;
        return new ReservationWaitlistVo(row.getId(), row.getDeviceId(), row.getStartTime(), row.getEndTime(),
                row.getPurpose(), row.getStatus(), position, row.getOfferedUntil(), row.getReservationId(),
                row.getVersion(), row.getReason(), row.getCreateTime());
    }

    private static LabBusinessException invalid(String message) { return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, message); }
}
