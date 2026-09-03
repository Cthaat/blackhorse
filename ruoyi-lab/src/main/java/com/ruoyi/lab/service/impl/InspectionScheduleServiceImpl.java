package com.ruoyi.lab.service.impl;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import com.ruoyi.lab.domain.InspectionFrequencyType;
import com.ruoyi.lab.domain.InspectionTaskStatus;
import com.ruoyi.lab.domain.LabInspectionItem;
import com.ruoyi.lab.domain.LabInspectionPlan;
import com.ruoyi.lab.domain.LabInspectionPlanItem;
import com.ruoyi.lab.domain.LabInspectionTask;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabInspectionItemMapper;
import com.ruoyi.lab.mapper.LabInspectionPlanItemMapper;
import com.ruoyi.lab.mapper.LabInspectionPlanMapper;
import com.ruoyi.lab.mapper.LabInspectionTaskMapper;
import com.ruoyi.lab.service.InspectionScheduleService;
import com.ruoyi.lab.service.LabStatusHistoryService;
import com.ruoyi.lab.service.LabSystemOperator;
import com.ruoyi.lab.service.LabSystemOperatorProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InspectionScheduleServiceImpl implements InspectionScheduleService
{
    private static final DateTimeFormatter NUMBER_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private final LabInspectionPlanMapper planMapper;
    private final LabInspectionPlanItemMapper planItemMapper;
    private final LabInspectionTaskMapper taskMapper;
    private final LabInspectionItemMapper itemMapper;
    private final LabSystemOperatorProvider operatorProvider;
    private final LabStatusHistoryService historyService;
    private final Clock clock;

    public InspectionScheduleServiceImpl(LabInspectionPlanMapper planMapper,
            LabInspectionPlanItemMapper planItemMapper, LabInspectionTaskMapper taskMapper,
            LabInspectionItemMapper itemMapper, LabSystemOperatorProvider operatorProvider,
            LabStatusHistoryService historyService, Clock clock)
    {
        this.planMapper = planMapper;
        this.planItemMapper = planItemMapper;
        this.taskMapper = taskMapper;
        this.itemMapper = itemMapper;
        this.operatorProvider = operatorProvider;
        this.historyService = historyService;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int generateDueTasks(LocalDateTime now, int batchSize)
    {
        LabSystemOperator operator = operatorProvider.requiredOperator();
        if (now == null || batchSize < 1 || batchSize > 500)
        {
            throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR, "巡检任务批处理参数无效");
        }
        int created = 0;
        for (LabInspectionPlan plan : planMapper.selectDuePlansForUpdate(now, batchSize))
        {
            LocalDateTime scheduledAt = plan.getNextRunAt();
            LocalDateTime nextRun = nextScheduled(plan, scheduledAt);
            if (taskMapper.existsByPlanAndSchedule(plan.getId(), scheduledAt) > 0)
            {
                requireAdvanced(plan, scheduledAt, nextRun, operator, now);
                continue;
            }
            List<LabInspectionPlanItem> sourceItems = planItemMapper.selectEnabledByPlan(plan.getId());
            if (sourceItems.isEmpty())
            {
                throw new LabBusinessException(LabErrorCode.VALIDATION_ERROR,
                        "启用巡检计划缺少检查项");
            }
            LabInspectionTask task = new LabInspectionTask();
                task.setTaskNo("IT" + NUMBER_TIME.format(scheduledAt)
                        + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase());
                task.setPlanId(plan.getId());
                task.setLaboratoryId(plan.getLaboratoryId());
                task.setScheduledAt(scheduledAt);
                task.setDeadlineAt(scheduledAt.plusMinutes(plan.getDeadlineOffsetMinutes()));
                task.setAssigneeId(plan.getOwnerId());
                task.setStatus(InspectionTaskStatus.PENDING);
                task.setOverdueFlag("0");
                task.setVersion(0);
                task.setCreateBy(operator.userName());
                task.setUpdateBy(operator.userName());
                task.setCreateTime(LocalDateTime.now(clock));
                task.setDelFlag("0");
            try
            {
                taskMapper.insert(task);
            }
            catch (DuplicateKeyException duplicate)
            {
                if (taskMapper.existsByPlanAndSchedule(plan.getId(), scheduledAt) < 1)
                {
                    throw duplicate;
                }
                requireAdvanced(plan, scheduledAt, nextRun, operator, now);
                continue;
            }
            for (LabInspectionPlanItem source : sourceItems)
            {
                LabInspectionItem snapshot = new LabInspectionItem();
                snapshot.setTaskId(task.getId());
                snapshot.setPlanItemId(source.getId());
                snapshot.setItemCodeSnapshot(source.getItemCode());
                snapshot.setContentSnapshot(source.getContent());
                snapshot.setSortOrderSnapshot(source.getSortOrder());
                snapshot.setVersion(0);
                snapshot.setCreateTime(LocalDateTime.now(clock));
                snapshot.setDelFlag("0");
                itemMapper.insert(snapshot);
            }
            historyService.append("INSPECTION_TASK", task.getId(), null,
                    InspectionTaskStatus.PENDING.name(), operator.userId(), "定时生成巡检任务");
            requireAdvanced(plan, scheduledAt, nextRun, operator, now);
            created++;
        }
        return created;
    }

    private void requireAdvanced(LabInspectionPlan plan, LocalDateTime expected,
            LocalDateTime next, LabSystemOperator operator, LocalDateTime now)
    {
        if (planMapper.advanceNextRun(plan.getId(), expected, next, operator.userName(), now) != 1)
        {
            throw new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION,
                    "巡检计划已被其他任务处理");
        }
    }

    private static LocalDateTime nextScheduled(LabInspectionPlan plan, LocalDateTime current)
    {
        LocalDateTime next = plan.getFrequencyType().next(current, plan.getIntervalValue());
        if (plan.getFrequencyType() == InspectionFrequencyType.MONTHLY && plan.getDayOfMonth() != null)
        {
            YearMonth month = YearMonth.from(next);
            next = LocalDateTime.of(month.atDay(Math.min(plan.getDayOfMonth(), month.lengthOfMonth())),
                    plan.getExecuteTime());
        }
        return next;
    }
}
