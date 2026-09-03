package com.ruoyi.lab.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.lab.domain.LabAttachment;
import org.apache.ibatis.annotations.Param;

/**
 * Persistence operations for attachment metadata.
 */
public interface LabAttachmentMapper extends BaseMapper<LabAttachment>
{
    LabAttachment selectByIdActive(@Param("attachmentId") Long attachmentId);

    LabAttachment selectByIdForUpdate(@Param("attachmentId") Long attachmentId);

    int countActiveByObject(@Param("businessType") String businessType,
            @Param("businessId") Long businessId);

    int markDeleted(@Param("attachmentId") Long attachmentId);
}
