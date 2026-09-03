package com.ruoyi.lab.domain;

/** Qualification status calculated from its validity interval and revocation time. */
public enum QualificationComputedStatus
{
    NOT_EFFECTIVE,
    VALID,
    EXPIRED,
    REVOKED
}
