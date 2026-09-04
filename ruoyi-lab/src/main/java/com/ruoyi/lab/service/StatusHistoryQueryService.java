package com.ruoyi.lab.service;

import java.util.List;
import com.ruoyi.lab.vo.StatusHistoryVo;

/** Authorized read access to immutable business status history. */
public interface StatusHistoryQueryService
{
    List<StatusHistoryVo> list(String objectType, Long objectId, Long currentUserId);
}
