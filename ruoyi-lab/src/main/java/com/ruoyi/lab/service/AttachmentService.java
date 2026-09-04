package com.ruoyi.lab.service;

import java.util.List;
import com.ruoyi.lab.vo.AttachmentContent;
import com.ruoyi.lab.vo.AttachmentVo;

/** Authorized attachment use cases. */
public interface AttachmentService
{
    List<AttachmentVo> list(String businessType, Long businessId);

    AttachmentVo upload(String businessType, Long businessId, String originalName,
            String declaredMimeType, byte[] content, String username);

    AttachmentContent download(Long attachmentId);

    void delete(Long attachmentId);
}
