package com.ruoyi.lab.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import com.ruoyi.lab.config.LabStorageProperties;
import com.ruoyi.lab.exception.LabBusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class AttachmentPolicyTest
{
    private AttachmentPolicy policy;

    @BeforeEach
    void setUp()
    {
        LabStorageProperties properties = new LabStorageProperties();
        properties.setMaxFileSize(DataSize.ofMegabytes(10));
        properties.setMaxFilesPerObject(5);
        policy = new AttachmentPolicy(properties);
    }

    @Test
    void acceptsSupportedSignatures()
    {
        assertThat(policy.validate("image.png", "image/png", png(), 0).extension()).isEqualTo("png");
        assertThat(policy.validate("photo.JPG", "image/jpeg", jpeg(), 0).mimeType()).isEqualTo("image/jpeg");
        assertThat(policy.validate("proof.pdf", "application/pdf", pdf(), 4).extension()).isEqualTo("pdf");
    }

    @Test
    void rejectsSpoofingEmptyFilesAndSixthAttachment()
    {
        assertInvalid("report.exe.pdf", "application/pdf", pdf(), 0);
        assertInvalid("report.pdf", "application/pdf", png(), 0);
        assertInvalid("report.pdf", "image/png", pdf(), 0);
        assertInvalid("report.pdf", "application/pdf", new byte[0], 0);
        assertInvalid("report.pdf", "application/pdf", pdf(), 5);
    }

    private void assertInvalid(String name, String mimeType, byte[] content, int count)
    {
        assertThatThrownBy(() -> policy.validate(name, mimeType, content, count))
                .isInstanceOf(LabBusinessException.class);
    }

    private static byte[] png()
    {
        return new byte[] { (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a, 1 };
    }

    private static byte[] jpeg()
    {
        return new byte[] { (byte) 0xff, (byte) 0xd8, (byte) 0xff, 1 };
    }

    private static byte[] pdf()
    {
        return new byte[] { 0x25, 0x50, 0x44, 0x46, 0x2d, 1 };
    }
}
