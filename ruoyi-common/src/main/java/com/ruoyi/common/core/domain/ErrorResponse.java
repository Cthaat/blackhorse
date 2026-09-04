package com.ruoyi.common.core.domain;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Stable error response used at API boundaries.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public final class ErrorResponse
{
    private final int code;

    private final String errorCode;

    private final String msg;

    private final String traceId;

    private final OffsetDateTime timestamp;

    private final Map<String, String> data;

    public ErrorResponse(int code, String errorCode, String msg, String traceId, OffsetDateTime timestamp,
            Map<String, String> data)
    {
        this.code = code;
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.msg = Objects.requireNonNull(msg, "msg must not be null");
        this.traceId = Objects.requireNonNull(traceId, "traceId must not be null");
        this.timestamp = Objects.requireNonNull(timestamp, "timestamp must not be null");
        this.data = immutableData(data);
    }

    public ErrorResponse(int code, String errorCode, String msg, String traceId, OffsetDateTime timestamp)
    {
        this(code, errorCode, msg, traceId, timestamp, null);
    }

    private static Map<String, String> immutableData(Map<String, String> data)
    {
        if (data == null || data.isEmpty())
        {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        data.forEach((key, value) -> copy.put(
                Objects.requireNonNull(key, "data key must not be null"),
                Objects.requireNonNull(value, "data value must not be null")));
        return Collections.unmodifiableMap(copy);
    }

    public int getCode()
    {
        return code;
    }

    public String getErrorCode()
    {
        return errorCode;
    }

    public String getMsg()
    {
        return msg;
    }

    public String getTraceId()
    {
        return traceId;
    }

    public OffsetDateTime getTimestamp()
    {
        return timestamp;
    }

    public Map<String, String> getData()
    {
        return data;
    }
}
