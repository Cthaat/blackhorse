package com.ruoyi.lab.storage;

import java.util.List;
import java.util.Locale;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.domain.LabLaboratory;
import com.ruoyi.lab.domain.LabQualification;
import com.ruoyi.lab.domain.LabHazard;
import com.ruoyi.lab.domain.LabRectification;
import com.ruoyi.lab.domain.LabRepairOrder;
import com.ruoyi.lab.domain.RepairStatus;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabLaboratoryMapper;
import com.ruoyi.lab.mapper.LabQualificationMapper;
import com.ruoyi.lab.mapper.LabHazardMapper;
import com.ruoyi.lab.mapper.LabRectificationMapper;
import com.ruoyi.lab.mapper.LabRepairOrderMapper;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.security.LabObjectPermissionService;
import org.springframework.stereotype.Component;

/** Resolves an attachment's parent and applies the parent's object authorization. */
@Component
public class LabAttachmentObjectAuthorizer
{
    private final LabObjectPermissionService objectPermissionService;
    private final LabDataScopeService dataScopeService;
    private final LabLaboratoryMapper laboratoryMapper;
    private final LabDeviceMapper deviceMapper;
    private final LabQualificationMapper qualificationMapper;
    private final LabHazardMapper hazardMapper;
    private final LabRectificationMapper rectificationMapper;
    private final LabRepairOrderMapper repairOrderMapper;
    private final com.ruoyi.lab.restriction.RestrictionService restrictions;

    public LabAttachmentObjectAuthorizer(LabObjectPermissionService objectPermissionService,
            LabDataScopeService dataScopeService, LabLaboratoryMapper laboratoryMapper,
            LabDeviceMapper deviceMapper, LabQualificationMapper qualificationMapper,
            LabHazardMapper hazardMapper, LabRectificationMapper rectificationMapper,
            LabRepairOrderMapper repairOrderMapper, com.ruoyi.lab.restriction.RestrictionService restrictions)
    {
        this.objectPermissionService = objectPermissionService;
        this.dataScopeService = dataScopeService;
        this.laboratoryMapper = laboratoryMapper;
        this.deviceMapper = deviceMapper;
        this.qualificationMapper = qualificationMapper;
        this.hazardMapper = hazardMapper;
        this.rectificationMapper = rectificationMapper;
        this.repairOrderMapper = repairOrderMapper;
        this.restrictions = restrictions;
    }

    public String normalizeBusinessType(String businessType)
    {
        if (businessType == null)
        {
            throw invalidType();
        }
        String normalized = businessType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("LABORATORY", "DEVICE", "QUALIFICATION", "RECTIFICATION",
                "REPAIR_ORDER", "RESTRICTION").contains(normalized))
        {
            throw invalidType();
        }
        return normalized;
    }

    public void assertReadable(String businessType, long businessId)
    {
        switch (normalizeBusinessType(businessType))
        {
            case "LABORATORY" -> objectPermissionService.assertLaboratoryReadable(businessId);
            case "DEVICE" -> objectPermissionService.assertDeviceReadable(businessId);
            case "QUALIFICATION" -> assertQualificationReadable(businessId);
            case "RECTIFICATION" -> assertRectificationReadable(businessId);
            case "REPAIR_ORDER" -> assertRepairReadable(businessId);
            case "RESTRICTION" -> restrictions.readable(businessId);
            default -> throw invalidType();
        }
    }

    public void lockAndAssertManageable(String businessType, long businessId)
    {
        switch (normalizeBusinessType(businessType))
        {
            case "LABORATORY" -> {
                requireBusinessPermission("lab:laboratory:edit");
                LabLaboratory laboratory = laboratoryMapper.selectByIdForUpdate(businessId);
                requireExists(laboratory);
                objectPermissionService.assertLaboratoryManageable(businessId);
            }
            case "DEVICE" -> {
                requireBusinessPermission("lab:device:edit");
                LabDevice device = deviceMapper.selectByIdForUpdate(businessId);
                requireExists(device);
                objectPermissionService.assertDeviceManageable(businessId);
            }
            case "QUALIFICATION" -> {
                requireBusinessPermission("lab:qualification:edit");
                LabQualification qualification = qualificationMapper.selectByIdForUpdate(businessId);
                requireExists(qualification);
                assertQualificationManageable(businessId);
            }
            case "RECTIFICATION" -> assertRectificationManageable(businessId);
            case "REPAIR_ORDER" -> assertRepairManageable(businessId);
            case "RESTRICTION" -> restrictions.lockEvidenceOwner(businessId);
            default -> throw invalidType();
        }
    }

    private void assertQualificationReadable(long qualificationId)
    {
        LabQualification qualification = qualificationMapper.selectById(qualificationId);
        if (qualification == null || !"0".equals(qualification.getDelFlag()))
        {
            throw notFound();
        }
        if (qualification.getUserId() != null
                && qualification.getUserId() == objectPermissionService.currentUserId())
        {
            return;
        }
        assertQualificationManageable(qualificationId);
    }

    private void assertQualificationManageable(long qualificationId)
    {
        LabDataScope scope = dataScopeService.resolveCurrentScope();
        boolean visible = qualificationMapper.selectListByScope(scope, null, null, null)
                .stream().anyMatch(item -> item.getId() != null && item.getId() == qualificationId);
        if (!visible)
        {
            throw new LabBusinessException(LabErrorCode.LAB_OUT_OF_DATA_SCOPE,
                    "对象不在当前数据范围内");
        }
    }

    private void assertRectificationReadable(long rectificationId)
    {
        LabRectification round = rectificationMapper.selectById(rectificationId);
        if (round == null || !"0".equals(round.getDelFlag()))
        {
            throw notFound();
        }
        LabHazard hazard = hazardMapper.selectActiveById(round.getHazardId());
        requireExists(hazard);
        long currentUserId = objectPermissionService.currentUserId();
        if ((hazard.getOwnerId() != null && hazard.getOwnerId() == currentUserId)
                || (round.getSubmitterId() != null && round.getSubmitterId() == currentUserId)
                || (round.getReviewerId() != null && round.getReviewerId() == currentUserId))
        {
            return;
        }
        if (hazard.getTargetType().name().equals("LABORATORY"))
        {
            objectPermissionService.assertLaboratoryReadable(hazard.getTargetId());
        }
        else
        {
            objectPermissionService.assertDeviceReadable(hazard.getTargetId());
        }
    }

    private void assertRectificationManageable(long rectificationId)
    {
        LabRectification round = rectificationMapper.selectById(rectificationId);
        if (round == null || !"0".equals(round.getDelFlag()))
        {
            throw notFound();
        }
        assertRectificationReadable(rectificationId);
        if (round.getSubmitterId() == null
                || round.getSubmitterId() != objectPermissionService.currentUserId()
                || round.getReviewResult() != null)
        {
            throw new LabBusinessException(LabErrorCode.ACCESS_DENIED, "当前用户无权管理整改附件");
        }
    }

    private void assertRepairReadable(long repairOrderId)
    {
        LabRepairOrder order = repairOrderMapper.selectActiveById(repairOrderId);
        requireExists(order);
        long currentUserId = objectPermissionService.currentUserId();
        LabDataScope scope = dataScopeService.resolveCurrentScope();
        if (repairOrderMapper.selectScopedDetail(repairOrderId, currentUserId, scope) == null)
        {
            throw new LabBusinessException(LabErrorCode.LAB_OUT_OF_DATA_SCOPE,
                    "对象不在当前数据范围内");
        }
    }

    private void assertRepairManageable(long repairOrderId)
    {
        LabRepairOrder order = repairOrderMapper.selectByIdForUpdate(repairOrderId);
        requireExists(order);
        if (order.getStatus() == RepairStatus.CLOSED)
        {
            throw attachmentAccessDenied();
        }

        long currentUserId = objectPermissionService.currentUserId();
        boolean assignedWorker = order.getAssigneeId() != null
                && order.getAssigneeId() == currentUserId
                && (order.getStatus() == RepairStatus.WAIT_REPAIR
                        || order.getStatus() == RepairStatus.IN_PROGRESS);
        if (assignedWorker)
        {
            return;
        }

        if (repairOrderMapper.countActiveUserRole(currentUserId, "lab_manager") > 0)
        {
            objectPermissionService.assertDeviceManageable(order.getDeviceId());
            return;
        }
        throw attachmentAccessDenied();
    }

    private static void requireExists(Object value)
    {
        if (value == null)
        {
            throw notFound();
        }
    }

    private static void requireBusinessPermission(String permission)
    {
        if (!SecurityUtils.hasPermi(permission))
        {
            throw new LabBusinessException(LabErrorCode.ACCESS_DENIED,
                    "当前用户无权管理此类业务附件");
        }
    }

    private static LabBusinessException notFound()
    {
        return new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "业务对象不存在");
    }

    private static LabBusinessException invalidType()
    {
        return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "附件业务类型无效");
    }

    private static LabBusinessException attachmentAccessDenied()
    {
        return new LabBusinessException(LabErrorCode.ACCESS_DENIED, "当前用户无权管理维修附件");
    }
}
