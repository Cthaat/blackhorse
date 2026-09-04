package com.ruoyi.lab.service;

/** Best-effort Redis pointer; MySQL remains authoritative. */
public record IdempotencySnapshot(long reservationId, String requestHash)
{
}
