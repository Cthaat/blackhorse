package com.ruoyi.lab.restriction;

import java.time.LocalDateTime;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

/** Immutable restriction facts; only explicit revocation metadata can change. */
public class RestrictionRecord
{
    @LabBusinessId public Long id;
    @LabBusinessId public Long laboratoryId;
    public String laboratoryName;
    @LabBusinessId public Long userId;
    public String userName;
    public String source;
    @LabBusinessId public Long sourceReservationId;
    public String reason;
    @LabBusinessTime public LocalDateTime startsAt;
    @LabBusinessTime public LocalDateTime endsAt;
    @LabBusinessTime public LocalDateTime revokedAt;
    @LabBusinessId public Long revokedBy;
    public String revokeReason;
    @LabBusinessId public Long ruleVersionId;
    public String ruleSnapshot;
    @LabBusinessId public Long createdBy;
    @LabBusinessTime public LocalDateTime createdAt;
    public String status;
}
