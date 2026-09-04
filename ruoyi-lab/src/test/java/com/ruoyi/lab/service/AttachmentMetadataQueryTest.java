package com.ruoyi.lab.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.lab.domain.LabAttachment;
import com.ruoyi.lab.mapper.LabAttachmentMapper;
import com.ruoyi.lab.service.impl.AttachmentServiceImpl;
import com.ruoyi.lab.storage.AttachmentPolicy;
import com.ruoyi.lab.storage.LabAttachmentObjectAuthorizer;
import com.ruoyi.lab.storage.StorageService;
import com.ruoyi.lab.vo.AttachmentVo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AttachmentMetadataQueryTest
{
    @Mock
    private LabAttachmentMapper attachmentMapper;
    @Mock
    private LabAttachmentObjectAuthorizer objectAuthorizer;
    @Mock
    private AttachmentPolicy attachmentPolicy;
    @Mock
    private StorageService storageService;

    @Test
    void listsOnlyClientSafeMetadataAfterObjectAuthorization()
    {
        AttachmentService service = new AttachmentServiceImpl(attachmentMapper,
                objectAuthorizer, attachmentPolicy, storageService);
        LabAttachment attachment = attachment();
        when(objectAuthorizer.normalizeBusinessType("device")).thenReturn("DEVICE");
        when(attachmentMapper.selectListByObject("DEVICE", 17L))
                .thenReturn(List.of(attachment));

        List<AttachmentVo> result = service.list("device", 17L);

        assertThat(result).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(31L);
            assertThat(item.businessType()).isEqualTo("DEVICE");
            assertThat(item.businessId()).isEqualTo(17L);
            assertThat(item.originalName()).isEqualTo("inspection.pdf");
            assertThat(item.toString()).doesNotContain("objects/private-value", "random.bin");
        });
        verify(objectAuthorizer).assertReadable("DEVICE", 17L);
    }

    private static LabAttachment attachment()
    {
        LabAttachment attachment = new LabAttachment();
        attachment.setId(31L);
        attachment.setBusinessType("DEVICE");
        attachment.setBusinessId(17L);
        attachment.setOriginalName("inspection.pdf");
        attachment.setStoredName("random.bin");
        attachment.setStorageKey("objects/private-value");
        attachment.setMimeType("application/pdf");
        attachment.setSize(128L);
        attachment.setSha256("abc123");
        attachment.setCreateBy("manager");
        attachment.setCreateTime(LocalDateTime.of(2026, 9, 3, 10, 0));
        attachment.setDelFlag("0");
        return attachment;
    }
}
