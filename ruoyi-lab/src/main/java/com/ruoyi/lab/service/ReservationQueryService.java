package com.ruoyi.lab.service;

import java.util.List;
import com.ruoyi.lab.dto.ReservationQueryDto;
import com.ruoyi.lab.vo.ReservationVo;

/** Current-user and data-scope reservation queries. */
public interface ReservationQueryService
{
    List<ReservationVo> list(ReservationQueryDto query, Long currentUserId, boolean managementView);

    ReservationVo getById(Long reservationId, Long currentUserId, boolean managementView);
}
