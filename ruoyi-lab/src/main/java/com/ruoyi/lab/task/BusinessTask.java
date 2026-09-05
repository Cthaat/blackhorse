package com.ruoyi.lab.task;

import java.time.LocalDateTime;

/** Internal persistence model. Never returned directly by an HTTP controller. */
public class BusinessTask
{
    public Long id;
    public Long ownerId;
    public String kind;
    public String direction;
    public String status;
    public String scopeJson;
    public String filterJson;
    public String inputKey;
    public String resultKey;
    public String errorKey;
    public Long parentId;
    public long maxId;
    public int totalCount;
    public int successCount;
    public int failureCount;
    public long cursorId;
    public String leaseToken;
    public LocalDateTime leaseUntil;
    public String errorCode;
    public String traceId;
    public LocalDateTime createdAt;
    public LocalDateTime startedAt;
    public LocalDateTime finishedAt;
    public LocalDateTime expiresAt;
}
