package com.ruoyi.lab.service.impl;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import com.ruoyi.lab.domain.LabReservation;
import com.ruoyi.lab.dto.ReservationQueryDto;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.lab.mapper.LabReservationMapper;
import com.ruoyi.lab.security.LabDataScope;
import com.ruoyi.lab.security.LabDataScopeService;
import com.ruoyi.lab.security.LabObjectPermissionService;
import com.ruoyi.lab.service.LabSortWhitelist;
import com.ruoyi.lab.service.ReservationQueryService;
import com.ruoyi.lab.vo.ReservationVo;
import org.springframework.stereotype.Service;

/** Enforces applicant isolation or management data scope before returning reservations. */
@Service
public class ReservationQueryServiceImpl implements ReservationQueryService
{
    private static final ZoneOffset API_OFFSET = ZoneOffset.ofHours(8);
    private final LabReservationMapper reservationMapper;
    private final LabDataScopeService dataScopeService;
    private final LabObjectPermissionService objectPermissionService;
    private final LabSortWhitelist sortWhitelist;

    public ReservationQueryServiceImpl(LabReservationMapper reservationMapper,
            LabDataScopeService dataScopeService,
            LabObjectPermissionService objectPermissionService, LabSortWhitelist sortWhitelist)
    {
        this.reservationMapper = reservationMapper;
        this.dataScopeService = dataScopeService;
        this.objectPermissionService = objectPermissionService;
        this.sortWhitelist = sortWhitelist;
    }

    @Override
    public List<ReservationVo> list(ReservationQueryDto query, Long currentUserId,
            boolean managementView)
    {
        long userId = requirePositive(currentUserId);
        ReservationQueryDto filters = query == null ? new ReservationQueryDto() : query;
        LabSortWhitelist.SortClause sort = sortWhitelist.resolve("reservation",
                defaultValue(filters.getSortBy(), "createTime"),
                defaultValue(filters.getSortDirection(), "desc"));
        LocalDateTime from = localTime(filters.getFrom());
        LocalDateTime to = localTime(filters.getTo());
        if (from != null && to != null && !from.isBefore(to))
        {
            throw validation("预约查询时间范围无效");
        }
        LabDataScope scope = managementView ? dataScopeService.resolveCurrentScope() : null;
        return reservationMapper.selectAccessible(scope, userId, managementView,
                filters.getApplicantId(),
                filters.getDeviceId(), filters.getStatus() == null ? null : filters.getStatus().name(),
                trimToNull(filters.getReservationNo()), from, to, sort)
                .stream().map(ReservationVo::from).toList();
    }

    @Override
    public ReservationVo getById(Long reservationId, Long currentUserId, boolean managementView)
    {
        long id = requirePositive(reservationId);
        long userId = requirePositive(currentUserId);
        LabReservation reservation = reservationMapper.selectActiveById(id);
        if (reservation == null)
        {
            throw new LabBusinessException(LabErrorCode.RESOURCE_NOT_FOUND, "预约不存在");
        }
        if (Objects.equals(reservation.getApplicantId(), userId))
        {
            return ReservationVo.from(reservation);
        }
        if (!managementView)
        {
            throw outOfScope();
        }
        objectPermissionService.assertDeviceManageable(reservation.getDeviceId());
        return ReservationVo.from(reservation);
    }

    private static LocalDateTime localTime(java.time.OffsetDateTime value)
    {
        if (value == null)
        {
            return null;
        }
        if (!API_OFFSET.equals(value.getOffset()))
        {
            throw validation("预约时间必须使用东八区偏移");
        }
        return value.toLocalDateTime();
    }

    private static long requirePositive(Long value)
    {
        if (value == null || value <= 0)
        {
            throw validation("对象编号无效");
        }
        return value;
    }

    private static String trimToNull(String value)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        return value.trim();
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
                "对象不在当前数据范围内");
    }
}
