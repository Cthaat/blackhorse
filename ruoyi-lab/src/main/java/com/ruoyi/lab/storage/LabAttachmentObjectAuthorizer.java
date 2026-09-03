package com.ruoyi.lab.storage;

import java.util.List;
import java.util.Locale;
import com.ruoyi.lab.domain.LabDevice;
import com.ruoyi.lab.domain.LabLaboratory;
import com.ruoyi.lab.domain.LabQualification;
import com.ruoyi.lab.domain.LabHazard;
import com.ruoyi.lab.domain.LabRectification;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabLaboratoryMapper;
import com.ruoyi.lab.mapper.LabQualificationMapper;
import com.ruoyi.lab.mapper.LabHazardMapper;
import com.ruoyi.lab.mapper.LabRectificationMapper;
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

    public LabAttachmentObjectAuthorizer(LabObjectPermissionService objectPermissionService,
            LabDataScopeService dataScopeService, LabLaboratoryMapper laboratoryMapper,
            LabDeviceMapper deviceMapper, LabQualificationMapper qualificationMapper,
            LabHazardMapper hazardMapper, LabRectificationMapper rectificationMapper)
    {
        this.objectPermissionService = objectPermissionService;
        this.dataScopeService = dataScopeService;
        this.laboratoryMapper = laboratoryMapper;
        this.deviceMapper = deviceMapper;
        this.qualificationMapper = qualificationMapper;
        this.hazardMapper = hazardMapper;
        this.rectificationMapper = rectificationMapper;
    }

    public String normalizeBusinessType(String businessType)
    {
        if (businessType == null)
        {
            throw invalidType();
        }
        String normalized = businessType.trim().toUpperCase(Locale.ROOT);
        if (!List.of("LABORATORY", "DEVICE", "QUALIFICATION", "RECTIFICATION").contains(normalized))
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
            default -> throw invalidType();
        }
    }

    public void lockAndAssertManageable(String businessType, long businessId)
    {
        switch (normalizeBusinessType(businessType))
        {
            case "LABORATORY" -> {
                LabLaboratory laboratory = laboratoryMapper.selectByIdForUpdate(businessId);
                requireExists(laboratory);
                objectPermissionService.assertLaboratoryManageable(businessId);
            }
            case "DEVICE" -> {
                LabDevice device = deviceMapper.selectByIdForUpdate(businessId);
                requireExists(device);
                objectPermissionService.assertDeviceManageable(businessId);
            }
            case "QUALIFICATION" -> {
                LabQualification qualification = qualificationMapper.selectByIdForUpdate(businessId);
                requireExists(qualification);
                assertQualificationManageable(businessId);
            }
            case "RECTIFICATION" -> assertRectificationManageable(businessId);
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

    private static void requireExists(Object value)
    {
        if (value == null)
        {
            throw notFound();
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
}
