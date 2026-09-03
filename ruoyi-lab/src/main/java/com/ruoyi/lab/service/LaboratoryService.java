package com.ruoyi.lab.service;

import java.util.List;
import com.ruoyi.lab.domain.LaboratoryStatus;
import com.ruoyi.lab.dto.LaboratoryCreateDto;
import com.ruoyi.lab.dto.LaboratoryUpdateDto;
import com.ruoyi.lab.vo.LaboratoryVo;

/** Laboratory query, detail and explicit lifecycle operations. */
public interface LaboratoryService
{
    List<LaboratoryVo> list(LaboratoryStatus status, String keyword, String sortBy, String sortDirection);

    LaboratoryVo getById(Long laboratoryId);

    LaboratoryVo create(LaboratoryCreateDto input, String username, Long actorId);

    void update(Long laboratoryId, LaboratoryUpdateDto input, String username);

    void enable(Long laboratoryId, String reason, Long actorId);

    void disable(Long laboratoryId, String reason, Long actorId);
}
