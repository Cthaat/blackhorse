package com.ruoyi.lab.service;

import com.ruoyi.lab.dto.ReviewRectificationCommand;
import com.ruoyi.lab.dto.SubmitRectificationCommand;

public interface RectificationService
{
    void start(Long hazardId, Long actorId, String actorName);
    Long submit(Long hazardId, SubmitRectificationCommand command, Long actorId,
            String actorName);
    void review(Long hazardId, Long rectificationId, ReviewRectificationCommand command,
            Long reviewerId, String reviewerName);
}
