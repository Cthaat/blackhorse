package com.ruoyi.lab.security;

import java.util.Set;

/** Object-level authorization facade shared by laboratory domains. */
public interface LabObjectPermissionService
{
    void assertLaboratoryReadable(long laboratoryId);
    void assertLaboratoryManageable(long laboratoryId);
    void assertDeviceReadable(long deviceId);
    void assertDeviceManageable(long deviceId);
    Set<Long> readableDepartmentIds();
    long currentUserId();
}
