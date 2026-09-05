package com.ruoyi.lab.restriction;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.TreeSet;
import com.ruoyi.lab.mapper.LabRestrictionMapper;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import org.springframework.stereotype.Service;

/**
 * Admission locking order: gate, sorted applicant rows, device, business row.
 * The gate serializes queue membership and batches across devices, so pre-locking
 * queue applicants cannot miss a concurrent join. All locks last until commit.
 */
@Service
public class RestrictionGuard
{
    private final LabRestrictionMapper mapper;
    private final Clock clock;
    public RestrictionGuard(LabRestrictionMapper mapper, Clock clock) { this.mapper=mapper; this.clock=clock; }

    public LocalDateTime gate()
    {
        LocalDateTime enabled = mapper.lockGate();
        if (enabled == null) throw new LabBusinessException(LabErrorCode.INTERNAL_ERROR, "预约限制锁未初始化");
        return enabled;
    }

    public void lockUsers(Collection<Long> users)
    {
        gate();
        for (Long user : new TreeSet<>(users))
        {
            mapper.ensureUser(user);
            mapper.lockUser(user);
        }
    }

    public void lockDeviceUsers(Long deviceId, Long applicantId)
    {
        gate();
        TreeSet<Long> users = new TreeSet<>(mapper.deviceUsers(deviceId));
        if (applicantId != null) users.add(applicantId);
        for (Long user : users)
        {
            mapper.ensureUser(user);
            mapper.lockUser(user);
        }
    }

    /** Caller must already hold the applicant lock acquired before the device. */
    public void assertAllowed(Long userId, Long laboratoryId)
    {
        if (mapper.activeCount(userId, laboratoryId, LocalDateTime.now(clock)) > 0)
            throw new LabBusinessException(LabErrorCode.ACCESS_DENIED, "您在该实验室存在生效中的预约限制，请查看我的限制；仍可取消、归还和报修");
    }
}
