package com.ruoyi.lab.service;

/** Append-only lifecycle audit service. */
public interface LabStatusHistoryService
{
    Long append(String objectType, Long objectId, String fromStatus, String toStatus,
            Long operatorId, String reason);
}
