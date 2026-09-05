package com.ruoyi.lab.domain;

import java.time.LocalDateTime;

/** Internal execution record. Never serialize this payload to the operator API. */
public class LabMessageDelivery
{
    public Long id;
    public String dedupeKey;
    public Long receiverId;
    public String eventType;
    public String sourceType;
    public Long sourceId;
    public Long eventVersion;
    public String businessType;
    public Long businessId;
    public String templateVersion;
    public String titleSnapshot;
    public String contentSnapshot;
    public String status;
    public Integer attemptCount;
    public Integer executionVersion;
    public LocalDateTime nextRetryAt;
    public LocalDateTime leaseUntil;
    public String errorCode;
    public String traceId;
    public LocalDateTime createTime;
    public LocalDateTime updateTime;
}
