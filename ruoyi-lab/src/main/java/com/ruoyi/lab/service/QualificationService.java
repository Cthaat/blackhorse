package com.ruoyi.lab.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.domain.QualificationComputedStatus;
import com.ruoyi.lab.domain.QualificationScopeType;
import com.ruoyi.lab.dto.QualificationCreateDto;
import com.ruoyi.lab.dto.QualificationRevokeDto;
import com.ruoyi.lab.dto.QualificationUpdateDto;
import com.ruoyi.lab.vo.QualificationVo;

/** Qualification management and current-user query operations. */
public interface QualificationService
{
    List<QualificationVo> list(Long userId, QualificationScopeType scopeType,
            String sortBy, String sortDirection);

    QualificationVo getById(Long qualificationId);

    QualificationVo getMineById(Long qualificationId);

    QualificationVo create(QualificationCreateDto input, String username, Long actorId);

    QualificationVo update(Long qualificationId, QualificationUpdateDto input,
            String username, Long actorId);

    QualificationVo revoke(Long qualificationId, QualificationRevokeDto input,
            String username, Long actorId);

    List<QualificationVo> listMine(String sortBy, String sortDirection);

    QualificationComputedStatus computeStatus(LocalDateTime validFrom, LocalDateTime validUntil,
            LocalDateTime revokedAt, Clock clock);
}
