package com.ruoyi.lab.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.lab.domain.LabUsageRecord;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabReservationTraceMapper;
import com.ruoyi.lab.mapper.LabRepairOrderMapper;
import com.ruoyi.lab.mapper.LabUsageRecordMapper;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.security.LabStatusHistoryObjectAuthorizer;
import com.ruoyi.lab.vo.*;
import com.ruoyi.lab.vo.ReservationTraceVo.*;
import org.springframework.stereotype.Service;

@Service
public class ReservationTraceService
{
    private static final int PAGE_SIZE = 20;
    private final ReservationQueryService reservations;
    private final UsageQueryService usages;
    private final LabRepairOrderMapper repairs;
    private final LabUsageRecordMapper usageMapper;
    private final LabReservationTraceMapper mapper;
    private final LabDataScopeService scopes;
    private final LabStatusHistoryObjectAuthorizer authorizer;
    private final Clock clock;

    public ReservationTraceService(ReservationQueryService reservations, UsageQueryService usages,
            LabRepairOrderMapper repairs, LabUsageRecordMapper usageMapper,
            LabReservationTraceMapper mapper, LabDataScopeService scopes,
            LabStatusHistoryObjectAuthorizer authorizer, Clock clock)
    {
        this.reservations = reservations; this.usages = usages; this.repairs = repairs;
        this.usageMapper = usageMapper; this.mapper = mapper; this.scopes = scopes;
        this.authorizer = authorizer; this.clock = clock;
    }

    public ReservationTraceVo trace(Long id, Long userId, boolean managementView)
    {
        ReservationVo reservation = reservations.getById(id, userId, managementView);
        UsageRecordDetailVo usage = visible(() -> usage(id, userId));
        RepairOrderVo repair = usage == null || usage.repairOrderId() == null
                || !permitted("lab:repair:query") ? null
                : visible(() -> repair(usage.repairOrderId(), userId));
        Long repairId = repair == null ? null : repair.id();
        Qualification qualification = visible(() -> qualification(reservation, userId));
        List<Node> hazards = permitted("lab:hazard:list")
                ? mapper.hazards(reservation.deviceId(), userId, scopes.resolveCurrentScope(), PAGE_SIZE + 1)
                : List.of();
        List<Node> notifications = permitted("lab:notification:list")
                ? mapper.notifications(id, usage == null ? null : usage.id(), repairId, userId, PAGE_SIZE + 1)
                : List.of();
        // Root was authorized above. Usage transitions live on reservation; USAGE_RECORD history is unsupported.
        authorizer.assertReadable("RESERVATION", id, userId);
        List<StatusHistoryVo> history = mapper.history(id, repairId, PAGE_SIZE + 1);
        return new ReservationTraceVo(reservation, usageNode(usage), repairNode(repair),
                qualification, slice(hazards), slice(notifications), slice(history));
    }

    private UsageRecordDetailVo usage(Long id, Long userId)
    {
        if (!permitted("lab:usage:query")) return null;
        LabUsageRecord row = usageMapper.selectByReservationId(id);
        return row == null ? null : usages.detail(row.getId(), userId);
    }

    private Qualification qualification(ReservationVo reservation, Long userId)
    {
        boolean own = Objects.equals(reservation.applicantId(), userId);
        if (!(permitted("lab:qualification:query") || own && permitted("lab:qualification:mine"))) return null;
        LocalDateTime now = LocalDateTime.now(clock);
        LabDataScope scope = own ? null : scopes.resolveCurrentScope();
        Integer count = mapper.qualificationCount(reservation.deviceId(), reservation.applicantId(), userId, scope, now);
        return count == null ? null : new Qualification("CURRENT_MATCHING_RECORDS", count, now);
    }

    private RepairOrderVo repair(Long id, Long userId)
    {
        // Same scoped projection and history authorization as RepairQueryService.detail,
        // without eagerly loading its complete history and attachments.
        RepairOrderVo repair = repairs.selectScopedDetail(id, userId, scopes.resolveCurrentScope());
        if (repair != null) authorizer.assertReadable("REPAIR_ORDER", id, userId);
        return repair;
    }

    private static Node usageNode(UsageRecordDetailVo usage)
    {
        return usage == null ? null : new Node(usage.id(), "设备领用与归还",
                usage.returnedAt() == null ? "CHECKED_OUT" : "RETURNED",
                usage.returnedAt() == null ? usage.checkedOutAt() : usage.returnedAt(),
                usage.returnedAt() == null ? usage.checkoutNote() : usage.returnNote(), "DIRECT_RELATION");
    }

    private static Node repairNode(RepairOrderVo repair)
    {
        if (repair == null) return null;
        return new Node(repair.id(), repair.repairNo(), repair.status().name(), repair.createTime(),
                repair.faultDescription(), "DIRECT_RELATION");
    }

    private static boolean permitted(String permission) { return SecurityUtils.hasPermi(permission); }

    private static <T> T visible(Supplier<T> read)
    {
        try { return read.get(); }
        catch (LabBusinessException error)
        {
            if (error.getErrorCode() == LabErrorCode.ACCESS_DENIED
                    || error.getErrorCode() == LabErrorCode.LAB_OUT_OF_DATA_SCOPE
                    || error.getErrorCode() == LabErrorCode.RESOURCE_NOT_FOUND) return null;
            throw error;
        }
    }

    private static <T> Slice<T> slice(List<T> items)
    {
        return new Slice<>(items.subList(0, Math.min(items.size(), PAGE_SIZE)), items.size() > PAGE_SIZE);
    }
}
