package com.ruoyi.lab.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.lab.domain.LabReservationRule;
import com.ruoyi.lab.dto.ReservationRuleDefinition;
import com.ruoyi.lab.dto.ReservationRuleDraftDto;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabDeviceMapper;
import com.ruoyi.lab.mapper.LabReservationRuleMapper;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.vo.ReservationRuleVo;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Device-first locking keeps rule publication and new reservations on one version. */
@Service
public class ReservationRuleService
{
    private final LabReservationRuleMapper rules;
    private final LabDeviceMapper devices;
    private final LabObjectPermissionService permissions;
    private final LabSystemParameterProvider parameters;
    private final ObjectMapper json;
    private final Validator validator;
    private final Clock clock;

    public ReservationRuleService(LabReservationRuleMapper rules, LabDeviceMapper devices,
            LabObjectPermissionService permissions, LabSystemParameterProvider parameters,
            ObjectMapper json, Validator validator, Clock clock)
    {
        this.rules = rules;
        this.devices = devices;
        this.permissions = permissions;
        this.parameters = parameters;
        this.json = json;
        this.validator = validator;
        this.clock = clock;
    }

    public List<ReservationRuleVo> history(Long deviceId)
    {
        permissions.assertDeviceManageable(deviceId);
        return LabPage.query(() -> rules.history(deviceId), this::view);
    }

    public ReservationRuleVo active(Long deviceId)
    {
        LabReservationRule row = rules.active(deviceId);
        return row == null ? null : view(row);
    }

    public ReservationRuleVo readableVersion(Long id)
    {
        LabReservationRule row = require(id);
        permissions.assertDeviceManageable(row.getDeviceId());
        return view(row);
    }

    @Transactional
    public ReservationRuleVo create(ReservationRuleDraftDto request)
    {
        lockManagedDevice(request.deviceId());
        validateDefinition(request.definition());
        LabReservationRule row = new LabReservationRule();
        row.setDeviceId(request.deviceId());
        row.setVersionNumber(rules.nextVersion(request.deviceId()));
        row.setRevision(0);
        row.setStatus("DRAFT");
        row.setDefinitionJson(encode(request.definition()));
        row.setCreateBy(permissions.currentUserId());
        row.setCreateTime(LocalDateTime.now(clock));
        rules.insert(row);
        return view(row);
    }

    @Transactional
    public ReservationRuleVo edit(Long id, ReservationRuleDraftDto request)
    {
        LabReservationRule row = require(id);
        lockManagedDevice(row.getDeviceId());
        if (!Objects.equals(row.getDeviceId(), request.deviceId()))
        {
            throw invalid("规则不能移动到其他设备");
        }
        validateDefinition(request.definition());
        if (request.expectedVersion() == null || rules.edit(id, request.expectedVersion(),
                encode(request.definition())) != 1)
        {
            throw conflict();
        }
        return view(require(id));
    }

    @Transactional
    public ReservationRuleVo publish(Long id, int expectedVersion)
    {
        LabReservationRule initial = require(id);
        lockManagedDevice(initial.getDeviceId());
        LabReservationRule row = rules.locked(id);
        if (row == null || !"DRAFT".equals(row.getStatus()) || row.getRevision() != expectedVersion)
        {
            throw conflict();
        }
        validateDefinition(view(row).definition());
        rules.retireActive(row.getDeviceId());
        if (rules.publish(id, expectedVersion, permissions.currentUserId(), LocalDateTime.now(clock)) != 1)
        {
            throw conflict();
        }
        return view(require(id));
    }

    @Transactional
    public ReservationRuleVo retire(Long id, int expectedVersion)
    {
        LabReservationRule initial = require(id);
        lockManagedDevice(initial.getDeviceId());
        LabReservationRule row = rules.locked(id);
        if (row == null || !"PUBLISHED".equals(row.getStatus()) || row.getRevision() != expectedVersion)
        {
            throw conflict();
        }
        rules.retireActive(row.getDeviceId());
        return view(require(id));
    }

    /** Called inside the caller's device lock; returns the immutable snapshot for persistence. */
    public Snapshot validateForApply(Long deviceId, LocalDateTime start, LocalDateTime end)
    {
        LabReservationRule row = rules.activeLocked(deviceId);
        ReservationRuleVo rule = row == null ? null : view(row);
        if (rule != null)
        {
            ReservationRuleEvaluator.validate(rule.definition(), start, end, LocalDateTime.now(clock));
        }
        return new Snapshot(rule, globalLimits());
    }

    public GlobalLimits globalLimits()
    {
        int minimum = parameters.requiredInteger("lab.reservation.min-duration-minutes", 1, 1440);
        return new GlobalLimits(parameters.requiredInteger("lab.reservation.min-lead-minutes", 0, 10080),
                parameters.requiredInteger("lab.reservation.max-advance-days", 1, 365), minimum,
                parameters.requiredInteger("lab.reservation.max-duration-minutes", minimum, 10080));
    }

    public String encode(Object value)
    {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Cannot encode reservation rule", failure); }
    }

    public ReservationRuleVo view(LabReservationRule row)
    {
        try
        {
            return new ReservationRuleVo(row.getId(), row.getDeviceId(), row.getVersionNumber(),
                    row.getRevision(), row.getStatus(), json.readValue(row.getDefinitionJson(),
                    ReservationRuleDefinition.class), row.getCreateTime(), row.getPublishedAt());
        }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Invalid persisted reservation rule", failure); }
    }

    private void validateDefinition(ReservationRuleDefinition rule)
    {
        if (rule == null || !validator.validate(rule).isEmpty()) { throw invalid("规则字段不完整或超出允许范围"); }
        if (!LocalTime.parse(rule.opensAt()).isBefore(LocalTime.parse(rule.closesAt()))
                || rule.minDurationMinutes() > rule.maxDurationMinutes()
                || new HashSet<>(rule.weekdays()).size() != rule.weekdays().size()
                || rule.closedDays().stream().map(ReservationRuleDefinition.ClosedDay::date).distinct().count()
                    != rule.closedDays().size())
        {
            throw invalid("开放起止、时长或重复日期和星期设置无效");
        }
        GlobalLimits global = globalLimits();
        if (rule.minLeadMinutes() < global.minLeadMinutes() || rule.maxAdvanceDays() > global.maxAdvanceDays()
                || rule.minDurationMinutes() < global.minDurationMinutes()
                || rule.maxDurationMinutes() > global.maxDurationMinutes())
        {
            throw invalid("设备规则只能在全局时间约束内进一步收窄");
        }
    }

    private void lockManagedDevice(Long deviceId)
    {
        if (deviceId == null || deviceId <= 0 || devices.selectByIdForUpdate(deviceId) == null)
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "设备不存在");
        }
        permissions.assertDeviceManageable(deviceId);
    }

    private LabReservationRule require(Long id)
    {
        LabReservationRule row = rules.selectById(id);
        if (row == null) { throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "规则不存在"); }
        return row;
    }

    private static LabBusinessException invalid(String message) { return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, message); }
    private static LabBusinessException conflict() { return new LabBusinessException(LabErrorCode.LAB_DUPLICATE_OPERATION, "规则已被修改或当前状态不允许操作，请刷新"); }

    public record GlobalLimits(int minLeadMinutes, int maxAdvanceDays, int minDurationMinutes, int maxDurationMinutes) { }
    public record Snapshot(ReservationRuleVo rule, GlobalLimits global) { }
}
