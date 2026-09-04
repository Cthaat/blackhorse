package com.ruoyi.lab.security;

import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.domain.HazardTargetType;
import com.ruoyi.lab.domain.LabHazard;
import com.ruoyi.lab.domain.LabInspectionPlan;
import com.ruoyi.lab.domain.LabInspectionTask;
import com.ruoyi.lab.domain.LabQualification;
import com.ruoyi.lab.domain.LabReservation;
import com.ruoyi.lab.domain.QualificationScopeType;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabHazardMapper;
import com.ruoyi.lab.mapper.LabInspectionPlanMapper;
import com.ruoyi.lab.mapper.LabInspectionTaskMapper;
import com.ruoyi.lab.mapper.LabQualificationMapper;
import com.ruoyi.lab.mapper.LabRepairOrderMapper;
import com.ruoyi.lab.mapper.LabReservationMapper;
import org.springframework.stereotype.Component;

/** Fail-closed object authorization for the generic history endpoint. */
@Component
public class LabStatusHistoryObjectAuthorizer
{
    private static final Set<String> SUPPORTED_TYPES = Set.of("LABORATORY", "DEVICE",
            "QUALIFICATION", "RESERVATION", "REPAIR_ORDER", "INSPECTION_PLAN",
            "INSPECTION_TASK", "HAZARD");

    private final LabObjectPermissionService objectPermissionService;
    private final LabDataScopeService dataScopeService;
    private final LabQualificationMapper qualificationMapper;
    private final LabReservationMapper reservationMapper;
    private final LabRepairOrderMapper repairOrderMapper;
    private final LabInspectionPlanMapper inspectionPlanMapper;
    private final LabInspectionTaskMapper inspectionTaskMapper;
    private final LabHazardMapper hazardMapper;

    public LabStatusHistoryObjectAuthorizer(LabObjectPermissionService objectPermissionService,
            LabDataScopeService dataScopeService, LabQualificationMapper qualificationMapper,
            LabReservationMapper reservationMapper, LabRepairOrderMapper repairOrderMapper,
            LabInspectionPlanMapper inspectionPlanMapper,
            LabInspectionTaskMapper inspectionTaskMapper, LabHazardMapper hazardMapper)
    {
        this.objectPermissionService = objectPermissionService;
        this.dataScopeService = dataScopeService;
        this.qualificationMapper = qualificationMapper;
        this.reservationMapper = reservationMapper;
        this.repairOrderMapper = repairOrderMapper;
        this.inspectionPlanMapper = inspectionPlanMapper;
        this.inspectionTaskMapper = inspectionTaskMapper;
        this.hazardMapper = hazardMapper;
    }

    public String normalizeObjectType(String objectType)
    {
        String normalized = objectType == null ? "" : objectType.trim().toUpperCase(Locale.ROOT);
        if (!SUPPORTED_TYPES.contains(normalized))
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "状态历史对象类型无效");
        }
        return normalized;
    }

    public void assertReadable(String objectType, long objectId, Long currentUserId)
    {
        String normalizedType = normalizeObjectType(objectType);
        long userId = requireCurrentUser(currentUserId);
        assertFunctionPermission(normalizedType);
        switch (normalizedType)
        {
            case "LABORATORY" -> objectPermissionService.assertLaboratoryReadable(objectId);
            case "DEVICE" -> objectPermissionService.assertDeviceReadable(objectId);
            case "QUALIFICATION" -> assertQualificationReadable(objectId, userId);
            case "RESERVATION" -> assertReservationReadable(objectId, userId);
            case "REPAIR_ORDER" -> assertRepairReadable(objectId, userId);
            case "INSPECTION_PLAN" -> assertInspectionPlanReadable(objectId);
            case "INSPECTION_TASK" -> assertInspectionTaskReadable(objectId);
            case "HAZARD" -> assertHazardReadable(objectId, userId);
            default -> throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR,
                    "状态历史对象类型无效");
        }
    }

    private void assertQualificationReadable(long objectId, long userId)
    {
        LabQualification qualification = qualificationMapper.selectActiveById(objectId);
        requireExists(qualification);
        if (Objects.equals(qualification.getUserId(), userId))
        {
            return;
        }
        requirePermission("lab:qualification:query");
        long laboratoryId = requirePositive(qualification.getLaboratoryId(),
                "资格实验室编号无效");
        if (qualification.getScopeType() == QualificationScopeType.LABORATORY)
        {
            if (parsePositive(qualification.getScopeId()) != laboratoryId)
            {
                throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR,
                        "资格实验室范围数据不一致");
            }
        }
        else if (qualification.getScopeType() != QualificationScopeType.DEVICE_CATEGORY)
        {
            throw outOfScope();
        }
        objectPermissionService.assertLaboratoryReadable(laboratoryId);
    }

    private void assertReservationReadable(long objectId, long userId)
    {
        LabReservation reservation = reservationMapper.selectActiveById(objectId);
        requireExists(reservation);
        if (reservation.getApplicantId() != null && reservation.getApplicantId() == userId)
        {
            return;
        }
        requirePermission("lab:reservation:list");
        objectPermissionService.assertDeviceManageable(reservation.getDeviceId());
    }

    private static void assertFunctionPermission(String objectType)
    {
        boolean permitted = switch (objectType)
        {
            case "LABORATORY" -> SecurityUtils.hasPermi("lab:laboratory:query");
            case "DEVICE" -> SecurityUtils.hasPermi("lab:device:query");
            case "QUALIFICATION" -> hasAnyPermission("lab:qualification:query",
                    "lab:qualification:mine");
            case "RESERVATION" -> hasAnyPermission("lab:reservation:list",
                    "lab:reservation:mine");
            case "REPAIR_ORDER" -> SecurityUtils.hasPermi("lab:repair:query");
            case "INSPECTION_PLAN" -> SecurityUtils.hasPermi("lab:inspection:plan:list");
            case "INSPECTION_TASK" -> SecurityUtils.hasPermi("lab:inspection:task:list");
            case "HAZARD" -> SecurityUtils.hasPermi("lab:hazard:list");
            default -> false;
        };
        if (!permitted)
        {
            throw new LabBusinessException(LabErrorCode.ACCESS_DENIED,
                    "当前用户无权查询该类状态历史");
        }
    }

    private static boolean hasAnyPermission(String... permissions)
    {
        for (String permission : permissions)
        {
            if (SecurityUtils.hasPermi(permission))
            {
                return true;
            }
        }
        return false;
    }

    private static void requirePermission(String permission)
    {
        if (!SecurityUtils.hasPermi(permission))
        {
            throw new LabBusinessException(LabErrorCode.ACCESS_DENIED,
                    "当前用户无权查询该状态历史");
        }
    }

    private void assertRepairReadable(long objectId, long userId)
    {
        requireExists(repairOrderMapper.selectActiveById(objectId));
        if (repairOrderMapper.selectScopedDetail(objectId, userId,
                dataScopeService.resolveCurrentScope()) == null)
        {
            throw outOfScope();
        }
    }

    private void assertInspectionPlanReadable(long objectId)
    {
        LabInspectionPlan plan = inspectionPlanMapper.selectActiveById(objectId);
        requireExists(plan);
        objectPermissionService.assertLaboratoryReadable(plan.getLaboratoryId());
    }

    private void assertInspectionTaskReadable(long objectId)
    {
        LabInspectionTask task = inspectionTaskMapper.selectActiveById(objectId);
        requireExists(task);
        objectPermissionService.assertLaboratoryReadable(task.getLaboratoryId());
    }

    private void assertHazardReadable(long objectId, long userId)
    {
        LabHazard hazard = hazardMapper.selectActiveById(objectId);
        requireExists(hazard);
        if (hazard.getOwnerId() != null && hazard.getOwnerId() == userId)
        {
            return;
        }
        if (hazard.getTargetType() == HazardTargetType.LABORATORY)
        {
            objectPermissionService.assertLaboratoryReadable(hazard.getTargetId());
        }
        else
        {
            objectPermissionService.assertDeviceReadable(hazard.getTargetId());
        }
    }

    private long requireCurrentUser(Long currentUserId)
    {
        if (currentUserId == null || currentUserId <= 0
                || objectPermissionService.currentUserId() != currentUserId)
        {
            throw new LabBusinessException(LabErrorCode.ACCESS_DENIED, "当前用户无权查询状态历史");
        }
        return currentUserId;
    }

    private static long parsePositive(String value)
    {
        try
        {
            long parsed = Long.parseLong(value);
            if (parsed > 0)
            {
                return parsed;
            }
        }
        catch (RuntimeException ignored)
        {
            // Invalid persisted scope is denied below.
        }
        throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "资格范围编号无效");
    }

    private static long requirePositive(Long value, String message)
    {
        if (value == null || value <= 0)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, message);
        }
        return value;
    }

    private static void requireExists(Object value)
    {
        if (value == null)
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "业务对象不存在");
        }
    }

    private static LabBusinessException outOfScope()
    {
        return new LabBusinessException(LabErrorCode.LAB_OUT_OF_DATA_SCOPE,
                "对象不在当前数据范围内");
    }
}
