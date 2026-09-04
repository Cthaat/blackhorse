package com.ruoyi.lab.mapper;

import java.util.List;
import java.util.Set;
import com.ruoyi.lab.vo.LabDepartmentOptionVo;
import com.ruoyi.lab.vo.LabUserOptionVo;
import org.apache.ibatis.annotations.Param;

/** Read-only selector and active-role lookups for laboratory workflows. */
public interface LabOptionsMapper
{
    List<LabUserOptionVo> selectActiveUserOptions(@Param("roleKey") String roleKey,
            @Param("keyword") String keyword);

    List<LabDepartmentOptionVo> selectActiveDepartmentOptions(
            @Param("allDepartments") boolean allDepartments,
            @Param("departmentIds") Set<Long> departmentIds);

    int countActiveUserRole(@Param("userId") Long userId,
            @Param("roleKey") String roleKey);

    int countActiveUserLaboratoryScope(@Param("userId") Long userId,
            @Param("laboratoryId") Long laboratoryId);

    int countActiveUserDepartmentScope(@Param("userId") Long userId,
            @Param("departmentId") Long departmentId);

    int countActiveBusinessParticipant(@Param("userId") Long userId);

    int countActiveDepartment(@Param("departmentId") Long departmentId);
}
