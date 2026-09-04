package com.ruoyi.lab.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import com.ruoyi.lab.domain.InspectionFrequencyType;
import com.ruoyi.lab.domain.InspectionPlanStatus;
import com.ruoyi.lab.domain.LabInspectionPlan;
import com.ruoyi.lab.domain.LabInspectionPlanItem;
import com.ruoyi.lab.dto.InspectionPlanCommand;
import com.ruoyi.lab.dto.InspectionPlanItemCommand;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabInspectionPlanItemMapper;
import com.ruoyi.lab.mapper.LabInspectionPlanMapper;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.service.InspectionPlanService;
import com.ruoyi.lab.service.LabStatusHistoryService;
import com.ruoyi.lab.vo.InspectionPlanDetailVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionPlanServiceImpl implements InspectionPlanService
{
    private static final String OBJECT_TYPE = "INSPECTION_PLAN";
    private static final String DEADLINE_RULE = "AFTER_SCHEDULED";

    private final LabInspectionPlanMapper planMapper;
    private final LabInspectionPlanItemMapper itemMapper;
    private final LabDataScopeService dataScopeService;
    private final LabObjectPermissionService permissionService;
    private final LabStatusHistoryService historyService;
    private final Clock clock;

    public InspectionPlanServiceImpl(LabInspectionPlanMapper planMapper,
            LabInspectionPlanItemMapper itemMapper, LabDataScopeService dataScopeService,
            LabObjectPermissionService permissionService, LabStatusHistoryService historyService,
            Clock clock)
    {
        this.planMapper = planMapper;
        this.itemMapper = itemMapper;
        this.dataScopeService = dataScopeService;
        this.permissionService = permissionService;
        this.historyService = historyService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Long create(InspectionPlanCommand command, Long actorId, String actorName)
    {
        LocalDateTime now = LocalDateTime.now(clock);
        validate(command);
        requireActor(actorId, actorName);
        permissionService.assertLaboratoryManageable(command.laboratoryId());
        LabInspectionPlan plan = from(command, now, actorName);
        plan.setStatus(InspectionPlanStatus.DISABLED);
        plan.setVersion(0);
        plan.setCreateTime(now);
        plan.setCreateBy(actorName);
        plan.setDelFlag("0");
        planMapper.insert(plan);
        replaceItems(plan.getId(), command.items(), now);
        return plan.getId();
    }

    @Override
    @Transactional
    public void update(Long planId, Integer expectedVersion, InspectionPlanCommand command,
            Long actorId, String actorName)
    {
        validateIdAndVersion(planId, expectedVersion);
        validate(command);
        requireActor(actorId, actorName);
        LabInspectionPlan locked = requireLocked(planId);
        permissionService.assertLaboratoryManageable(locked.getLaboratoryId());
        permissionService.assertLaboratoryManageable(command.laboratoryId());
        if (locked.getStatus() != InspectionPlanStatus.DISABLED)
        {
            throw conflict("启用中的巡检计划不能修改");
        }
        if (!Objects.equals(locked.getVersion(), expectedVersion))
        {
            throw duplicate();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LabInspectionPlan changed = from(command, now, actorName);
        changed.setId(planId);
        if (planMapper.updateDetailsConditionally(changed, expectedVersion) != 1)
        {
            throw duplicate();
        }
        replaceItems(planId, command.items(), now);
    }

    @Override
    @Transactional
    public void enable(Long planId, Long actorId, String actorName)
    {
        changeStatus(planId, InspectionPlanStatus.DISABLED, InspectionPlanStatus.ENABLED,
                actorId, actorName, "启用巡检计划");
    }

    @Override
    @Transactional
    public void disable(Long planId, Long actorId, String actorName)
    {
        changeStatus(planId, InspectionPlanStatus.ENABLED, InspectionPlanStatus.DISABLED,
                actorId, actorName, "停用巡检计划");
    }

    @Override
    public List<LabInspectionPlan> list(InspectionPlanStatus status, String keyword)
    {
        return planMapper.selectListByScope(dataScopeService.resolveCurrentScope(), status,
                normalize(keyword, 100));
    }

    @Override
    public InspectionPlanDetailVo get(Long planId)
    {
        if (planId == null || planId <= 0)
        {
            throw validation("巡检计划编号无效");
        }
        LabInspectionPlan plan = planMapper.selectActiveById(planId);
        if (plan == null)
        {
            throw notFound();
        }
        permissionService.assertLaboratoryReadable(plan.getLaboratoryId());
        List<LabInspectionPlanItem> items = itemMapper.selectByPlan(planId);
        return new InspectionPlanDetailVo(plan, items == null ? List.of() : items);
    }

    private void changeStatus(Long planId, InspectionPlanStatus expected,
            InspectionPlanStatus target, Long actorId, String actorName, String reason)
    {
        requireActor(actorId, actorName);
        LabInspectionPlan plan = requireLocked(planId);
        permissionService.assertLaboratoryManageable(plan.getLaboratoryId());
        if (plan.getStatus() != expected)
        {
            throw duplicate();
        }
        if (target == InspectionPlanStatus.ENABLED
                && (plan.getOwnerId() == null || plan.getOwnerId() <= 0
                        || itemMapper.countEnabledByPlan(planId) < 1))
        {
            throw validation("巡检计划缺少负责人或启用检查项");
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (planMapper.updateStatusConditionally(planId, expected.name(), target.name(),
                actorName, now) != 1)
        {
            throw duplicate();
        }
        historyService.append(OBJECT_TYPE, planId, expected.name(), target.name(), actorId, reason);
    }

    private LabInspectionPlan from(InspectionPlanCommand command, LocalDateTime now,
            String actorName)
    {
        LabInspectionPlan plan = new LabInspectionPlan();
        plan.setPlanName(command.planName().trim());
        plan.setLaboratoryId(command.laboratoryId());
        plan.setFrequencyType(command.frequencyType());
        plan.setIntervalValue(command.intervalValue());
        plan.setExecuteTime(command.executeTime());
        plan.setDayOfWeek(command.dayOfWeek());
        plan.setDayOfMonth(command.dayOfMonth());
        plan.setNextRunAt(command.frequencyType().firstAfter(now, command.intervalValue(),
                command.executeTime(), command.dayOfWeek(), command.dayOfMonth()));
        plan.setOwnerId(command.ownerId());
        plan.setDeadlineRule(command.deadlineRule().trim());
        plan.setDeadlineOffsetMinutes(command.deadlineOffsetMinutes());
        plan.setUpdateBy(actorName);
        plan.setUpdateTime(now);
        return plan;
    }

    private void replaceItems(Long planId, List<InspectionPlanItemCommand> commands,
            LocalDateTime now)
    {
        itemMapper.retireByPlan(planId, now);
        if (commands == null)
        {
            return;
        }
        Set<String> codes = new HashSet<>();
        for (InspectionPlanItemCommand command : commands)
        {
            if (command == null || command.itemCode() == null || command.itemCode().isBlank()
                    || command.content() == null || command.content().isBlank())
            {
                throw validation("巡检检查项内容无效");
            }
            String code = command.itemCode().trim();
            if (!codes.add(code))
            {
                throw validation("巡检检查项编码重复");
            }
            LabInspectionPlanItem item = new LabInspectionPlanItem();
            item.setPlanId(planId);
            item.setItemCode(code);
            item.setContent(command.content().trim());
            item.setSortOrder(command.sortOrder());
            item.setEnabled(Boolean.TRUE.equals(command.enabled()) ? "1" : "0");
            item.setCreateTime(now);
            item.setDelFlag("0");
            itemMapper.insert(item);
        }
    }

    private static void validate(InspectionPlanCommand command)
    {
        if (command == null || command.planName() == null || command.planName().isBlank()
                || command.planName().trim().length() > 100 || command.laboratoryId() == null
                || command.laboratoryId() <= 0 || command.frequencyType() == null
                || command.ownerId() == null || command.ownerId() <= 0
                || command.executeTime() == null || command.intervalValue() < 1
                || command.intervalValue() > 31 || command.deadlineOffsetMinutes() < 1
                || command.deadlineOffsetMinutes() > 43200
                || !DEADLINE_RULE.equals(command.deadlineRule()))
        {
            throw validation("巡检计划参数无效");
        }
        boolean validDays = switch (command.frequencyType())
        {
            case DAILY -> command.dayOfWeek() == null && command.dayOfMonth() == null;
            case WEEKLY -> command.dayOfWeek() != null && command.dayOfWeek() >= 1
                    && command.dayOfWeek() <= 7 && command.dayOfMonth() == null;
            case MONTHLY -> command.dayOfMonth() != null && command.dayOfMonth() >= 1
                    && command.dayOfMonth() <= 31 && command.dayOfWeek() == null;
        };
        if (!validDays)
        {
            throw validation("巡检周期日期参数无效");
        }
    }

    private LabInspectionPlan requireLocked(Long planId)
    {
        if (planId == null || planId <= 0)
        {
            throw validation("巡检计划编号无效");
        }
        LabInspectionPlan plan = planMapper.selectForUpdate(planId);
        if (plan == null)
        {
            throw notFound();
        }
        return plan;
    }

    private void requireActor(Long actorId, String actorName)
    {
        if (actorId == null || actorId <= 0 || actorName == null || actorName.isBlank()
                || permissionService.currentUserId() != actorId)
        {
            throw new LabBusinessException(LabErrorCode.ACCESS_DENIED, "当前用户无权执行该操作");
        }
    }

    private static void validateIdAndVersion(Long id, Integer version)
    {
        if (id == null || id <= 0 || version == null || version < 0)
        {
            throw validation("巡检计划版本参数无效");
        }
    }

    private static String normalize(String value, int max)
    {
        if (value == null || value.isBlank()) return null;
        String normalized = value.trim();
        if (normalized.length() > max) throw validation("查询条件长度无效");
        return normalized;
    }

    private static LabBusinessException validation(String message) { return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, message); }
    private static LabBusinessException notFound() { return new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "巡检计划不存在"); }
    private static LabBusinessException duplicate() { return new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION, "操作已被其他请求处理"); }
    private static LabBusinessException conflict(String message) { return new LabBusinessException(LabErrorCode.LAB_ILLEGAL_STATE_TRANSITION, message); }
}
