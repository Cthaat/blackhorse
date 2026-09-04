package com.ruoyi.lab.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;

/** Resolves active role users whose data scope includes a laboratory. */
public interface LabNotificationRecipientMapper
{
    List<Long> selectScopedRoleUserIds(@Param("laboratoryId") Long laboratoryId,
            @Param("roleKey") String roleKey);
}
