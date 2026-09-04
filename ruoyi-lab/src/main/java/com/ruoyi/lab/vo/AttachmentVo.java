package com.ruoyi.lab.vo;

import java.time.LocalDateTime;
import com.ruoyi.lab.domain.LabAttachment;
import com.ruoyi.lab.serializer.LabBusinessId;
import com.ruoyi.lab.serializer.LabBusinessTime;

/** Client-safe attachment metadata. */
public record AttachmentVo(@LabBusinessId Long id, String businessType,
        @LabBusinessId Long businessId, String originalName, String mimeType, Long size,
        String sha256, String createBy, @LabBusinessTime LocalDateTime createTime)
{
    public static AttachmentVo from(LabAttachment attachment)
    {
        return new AttachmentVo(attachment.getId(), attachment.getBusinessType(),
                attachment.getBusinessId(), attachment.getOriginalName(), attachment.getMimeType(),
                attachment.getSize(), attachment.getSha256(), attachment.getCreateBy(),
                attachment.getCreateTime());
    }
}
