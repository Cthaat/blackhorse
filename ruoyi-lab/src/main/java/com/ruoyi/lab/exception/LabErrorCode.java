package com.ruoyi.lab.exception;

import com.ruoyi.common.constant.HttpStatus;

/**
 * Stable error codes exposed by laboratory APIs.
 */
public enum LabErrorCode
{
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND),
    INTERNAL_ERROR(HttpStatus.ERROR),
    LAB_RESERVATION_TIME_CONFLICT(HttpStatus.CONFLICT),
    LAB_QUALIFICATION_INVALID(HttpStatus.CONFLICT),
    LAB_DEVICE_UNAVAILABLE(HttpStatus.CONFLICT),
    LAB_LABORATORY_DISABLED(HttpStatus.CONFLICT),
    LAB_MAJOR_HAZARD_BLOCKED(HttpStatus.CONFLICT),
    LAB_ILLEGAL_STATE_TRANSITION(HttpStatus.CONFLICT),
    LAB_DUPLICATE_OPERATION(HttpStatus.CONFLICT),
    LAB_OUT_OF_DATA_SCOPE(HttpStatus.FORBIDDEN),
    LAB_REPAIR_ALREADY_OPEN(HttpStatus.CONFLICT);

    private final int httpStatus;

    LabErrorCode(int httpStatus)
    {
        this.httpStatus = httpStatus;
    }

    public int getHttpStatus()
    {
        return httpStatus;
    }
}
