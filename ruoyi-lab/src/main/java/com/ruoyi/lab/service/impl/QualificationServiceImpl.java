package com.ruoyi.lab.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import com.ruoyi.lab.domain.LabQualification;
import com.ruoyi.lab.domain.QualificationComputedStatus;
import com.ruoyi.lab.domain.QualificationScopeType;
import com.ruoyi.lab.dto.QualificationCreateDto;
import com.ruoyi.lab.dto.QualificationRevokeDto;
import com.ruoyi.lab.dto.QualificationUpdateDto;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDictionaryMapper;
import com.ruoyi.lab.mapper.LabQualificationMapper;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.service.LabSortWhitelist;
import com.ruoyi.lab.service.LabStatusHistoryService;
import com.ruoyi.lab.service.QualificationService;
import com.ruoyi.lab.vo.QualificationVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default qualification service with fail-closed scope checks. */
@Service
public class QualificationServiceImpl implements QualificationService
{
    private static final String OBJECT_TYPE = "QUALIFICATION";
    private static final String DEVICE_CATEGORY_DICT_TYPE = "lab_device_category";
    private static final String CREATE_REASON = "创建资格";
    private static final String UPDATE_REASON = "更新资格有效信息";

    private final LabQualificationMapper qualificationMapper;
    private final LabDictionaryMapper dictionaryMapper;
    private final LabDataScopeService dataScopeService;
    private final LabObjectPermissionService objectPermissionService;
    private final LabSortWhitelist sortWhitelist;
    private final LabStatusHistoryService historyService;
    private final Clock clock;

    public QualificationServiceImpl(LabQualificationMapper qualificationMapper,
            LabDictionaryMapper dictionaryMapper, LabDataScopeService dataScopeService,
            LabObjectPermissionService objectPermissionService, LabSortWhitelist sortWhitelist,
            LabStatusHistoryService historyService, Clock clock)
    {
        this.qualificationMapper = qualificationMapper;
        this.dictionaryMapper = dictionaryMapper;
        this.dataScopeService = dataScopeService;
        this.objectPermissionService = objectPermissionService;
        this.sortWhitelist = sortWhitelist;
        this.historyService = historyService;
        this.clock = clock;
    }

    @Override
    public List<QualificationVo> list(Long userId, QualificationScopeType scopeType,
            String sortBy, String sortDirection)
    {
        if (userId != null)
        {
            requirePositive(userId, "用户编号无效");
        }
        LabDataScope scope = dataScopeService.resolveCurrentScope();
        if (scope.empty())
        {
            return List.of();
        }
        LabSortWhitelist.SortClause sort = qualificationSort(sortBy, sortDirection);
        return qualificationMapper.selectListByScope(scope, userId, scopeType, sort)
                .stream().map(this::toVo).toList();
    }

    @Override
    public QualificationVo getById(Long qualificationId)
    {
        LabQualification qualification = requireActive(requirePositive(qualificationId,
                "资格编号无效"));
        assertManagementAccess(qualification);
        return toVo(qualification);
    }

    @Override
    public QualificationVo getMineById(Long qualificationId)
    {
        long userId = requirePositive(objectPermissionService.currentUserId(), "用户编号无效");
        LabQualification qualification = requireActive(requirePositive(qualificationId,
                "资格编号无效"));
        if (!Objects.equals(qualification.getUserId(), userId))
        {
            throw outOfScope();
        }
        return toVo(qualification);
    }

    @Override
    @Transactional
    public QualificationVo create(QualificationCreateDto input, String username, Long actorId)
    {
        Objects.requireNonNull(input, "input");
        String operator = requireUsername(username);
        LabDataScope actorScope = requireActorScope(actorId);
        String scopeId = validateTargetScope(input.getScopeType(), input.getScopeId(), actorScope);
        validateValidity(input.getValidFrom(), input.getValidUntil());

        LabQualification qualification = new LabQualification();
        qualification.setUserId(requirePositive(input.getUserId(), "用户编号无效"));
        qualification.setScopeType(input.getScopeType());
        qualification.setScopeId(scopeId);
        qualification.setValidFrom(input.getValidFrom());
        qualification.setValidUntil(input.getValidUntil());
        qualification.setVersion(0);
        qualification.setCreateBy(operator);
        qualification.setUpdateBy(operator);
        qualification.setDelFlag("0");
        qualificationMapper.insert(qualification);

        QualificationComputedStatus status = computeStatus(qualification.getValidFrom(),
                qualification.getValidUntil(), null, clock);
        historyService.append(OBJECT_TYPE, qualification.getId(), null, status.name(),
                actorId, CREATE_REASON);
        return toVo(requireActive(qualification.getId()));
    }

    @Override
    @Transactional
    public QualificationVo update(Long qualificationId, QualificationUpdateDto input,
            String username, Long actorId)
    {
        Objects.requireNonNull(input, "input");
        long id = requirePositive(qualificationId, "资格编号无效");
        int expectedVersion = requireVersion(input.getExpectedVersion());
        String operator = requireUsername(username);

        assertManagementAccess(requireActive(id));
        LabDataScope actorScope = requireActorScope(actorId);
        LabQualification locked = requireLocked(id);
        assertManagementAccess(locked);
        String scopeId = validateTargetScope(input.getScopeType(), input.getScopeId(), actorScope);
        validateValidity(input.getValidFrom(), input.getValidUntil());

        LocalDateTime now = LocalDateTime.now(clock);
        QualificationComputedStatus previous = computeStatusAt(locked.getValidFrom(),
                locked.getValidUntil(), locked.getRevokedAt(), now);

        LabQualification update = new LabQualification();
        update.setId(id);
        update.setUserId(requirePositive(input.getUserId(), "用户编号无效"));
        update.setScopeType(input.getScopeType());
        update.setScopeId(scopeId);
        update.setValidFrom(input.getValidFrom());
        update.setValidUntil(input.getValidUntil());
        update.setUpdateBy(operator);
        if (qualificationMapper.updateDetailsConditionally(update, expectedVersion) != 1)
        {
            throw duplicateOperation();
        }

        QualificationComputedStatus current = computeStatusAt(input.getValidFrom(),
                input.getValidUntil(), locked.getRevokedAt(), now);
        if (previous != current)
        {
            historyService.append(OBJECT_TYPE, id, previous.name(), current.name(), actorId,
                    UPDATE_REASON);
        }
        return toVo(requireActive(id));
    }

    @Override
    @Transactional
    public QualificationVo revoke(Long qualificationId, QualificationRevokeDto input,
            String username, Long actorId)
    {
        Objects.requireNonNull(input, "input");
        long id = requirePositive(qualificationId, "资格编号无效");
        int expectedVersion = requireVersion(input.getExpectedVersion());
        String reason = requireReason(input.getReason());
        String operator = requireUsername(username);

        assertManagementAccess(requireActive(id));
        requireActorScope(actorId);
        LabQualification locked = requireLocked(id);
        assertManagementAccess(locked);
        if (locked.getRevokedAt() != null)
        {
            return toVo(locked);
        }

        LocalDateTime revokedAt = LocalDateTime.now(clock);
        QualificationComputedStatus previous = computeStatusAt(locked.getValidFrom(),
                locked.getValidUntil(), null, revokedAt);
        if (qualificationMapper.revokeConditionally(id, expectedVersion, revokedAt, reason,
                operator) != 1)
        {
            throw duplicateOperation();
        }
        historyService.append(OBJECT_TYPE, id, previous.name(),
                QualificationComputedStatus.REVOKED.name(), actorId, reason);
        return toVo(requireActive(id));
    }

    @Override
    public List<QualificationVo> listMine(String sortBy, String sortDirection)
    {
        long userId = requirePositive(objectPermissionService.currentUserId(), "用户编号无效");
        return qualificationMapper.selectMine(userId, qualificationSort(sortBy, sortDirection))
                .stream().map(this::toVo).toList();
    }

    @Override
    public QualificationComputedStatus computeStatus(LocalDateTime validFrom,
            LocalDateTime validUntil, LocalDateTime revokedAt, Clock requestedClock)
    {
        if (requestedClock == null)
        {
            throw validation("资格状态计算时钟不能为空");
        }
        validateValidity(validFrom, validUntil);
        return computeStatusAt(validFrom, validUntil, revokedAt,
                LocalDateTime.now(requestedClock));
    }

    private QualificationVo toVo(LabQualification qualification)
    {
        return QualificationVo.from(qualification, computeStatus(qualification.getValidFrom(),
                qualification.getValidUntil(), qualification.getRevokedAt(), clock));
    }

    private LabSortWhitelist.SortClause qualificationSort(String sortBy, String sortDirection)
    {
        return sortWhitelist.resolve("qualification", defaultValue(sortBy, "createTime"),
                defaultValue(sortDirection, "desc"));
    }

    private LabQualification requireActive(long qualificationId)
    {
        LabQualification qualification = qualificationMapper.selectActiveById(qualificationId);
        if (qualification == null)
        {
            throw notFound();
        }
        return qualification;
    }

    private LabQualification requireLocked(long qualificationId)
    {
        LabQualification qualification = qualificationMapper.selectByIdForUpdate(qualificationId);
        if (qualification == null)
        {
            throw notFound();
        }
        return qualification;
    }

    private void assertManagementAccess(LabQualification qualification)
    {
        if (qualification.getScopeType() == QualificationScopeType.LABORATORY)
        {
            objectPermissionService.assertLaboratoryManageable(
                    parseLaboratoryScopeId(qualification.getScopeId()));
            return;
        }
        if (qualification.getScopeType() == QualificationScopeType.DEVICE_CATEGORY)
        {
            LabDataScope scope = dataScopeService.resolveCurrentScope();
            if (scope.empty())
            {
                throw outOfScope();
            }
            return;
        }
        throw outOfScope();
    }

    private String validateTargetScope(QualificationScopeType scopeType, String rawScopeId,
            LabDataScope actorScope)
    {
        if (scopeType == null)
        {
            throw validation("资格范围类型不能为空");
        }
        String scopeId = requireTrimmedLength(rawScopeId, 64, "资格范围编号无效");
        if (scopeType == QualificationScopeType.LABORATORY)
        {
            long laboratoryId = parseLaboratoryScopeId(scopeId);
            objectPermissionService.assertLaboratoryManageable(laboratoryId);
            return Long.toString(laboratoryId);
        }
        if (actorScope.empty())
        {
            throw outOfScope();
        }
        if (dictionaryMapper.countEnabledValue(DEVICE_CATEGORY_DICT_TYPE, scopeId) <= 0)
        {
            throw validation("设备类别字典值不存在或已停用");
        }
        return scopeId;
    }

    private LabDataScope requireActorScope(Long actorId)
    {
        long operatorId = requirePositive(actorId, "操作人编号无效");
        LabDataScope scope = dataScopeService.resolveCurrentScope();
        if (scope == null || scope.userId() != operatorId)
        {
            throw outOfScope();
        }
        return scope;
    }

    private static QualificationComputedStatus computeStatusAt(LocalDateTime validFrom,
            LocalDateTime validUntil, LocalDateTime revokedAt, LocalDateTime now)
    {
        validateValidity(validFrom, validUntil);
        if (revokedAt != null)
        {
            return QualificationComputedStatus.REVOKED;
        }
        if (now.isBefore(validFrom))
        {
            return QualificationComputedStatus.NOT_EFFECTIVE;
        }
        if (!now.isBefore(validUntil))
        {
            return QualificationComputedStatus.EXPIRED;
        }
        return QualificationComputedStatus.VALID;
    }

    private static void validateValidity(LocalDateTime validFrom, LocalDateTime validUntil)
    {
        if (validFrom == null || validUntil == null || !validUntil.isAfter(validFrom))
        {
            throw validation("资格有效期结束时间必须严格晚于开始时间");
        }
    }

    private static long parseLaboratoryScopeId(String scopeId)
    {
        String normalized = requireTrimmedLength(scopeId, 64, "实验室范围编号无效");
        try
        {
            return requirePositive(Long.valueOf(normalized), "实验室范围编号无效");
        }
        catch (NumberFormatException exception)
        {
            throw validation("实验室范围编号无效");
        }
    }

    private static long requirePositive(Long value, String message)
    {
        if (value == null || value <= 0)
        {
            throw validation(message);
        }
        return value;
    }

    private static int requireVersion(Integer value)
    {
        if (value == null || value < 0)
        {
            throw validation("期望版本无效");
        }
        return value;
    }

    private static String requireUsername(String username)
    {
        return requireTrimmedLength(username, 64, "操作人账号无效");
    }

    private static String requireReason(String reason)
    {
        return requireTrimmedLength(reason, 500, "撤销原因长度无效");
    }

    private static String requireTrimmedLength(String value, int maximum, String message)
    {
        if (value == null)
        {
            throw validation(message);
        }
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maximum)
        {
            throw validation(message);
        }
        return normalized;
    }

    private static String defaultValue(String value, String fallback)
    {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static LabBusinessException validation(String message)
    {
        return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, message);
    }

    private static LabBusinessException outOfScope()
    {
        return new LabBusinessException(LabErrorCode.LAB_OUT_OF_DATA_SCOPE,
                "资格不在当前数据范围内");
    }

    private static LabBusinessException notFound()
    {
        return new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "资格不存在");
    }

    private static LabBusinessException duplicateOperation()
    {
        return new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION,
                "资格已被其他请求修改");
    }
}
