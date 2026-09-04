package com.ruoyi.lab.storage;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import com.ruoyi.lab.config.LabStorageProperties;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import org.springframework.stereotype.Component;

/** Validates the public name, declared media type and actual file signature. */
@Component
public class AttachmentPolicy
{
    private static final Map<String, String> MEDIA_TYPES = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "pdf", "application/pdf");

    private final LabStorageProperties properties;

    public AttachmentPolicy(LabStorageProperties properties)
    {
        this.properties = properties;
    }

    public ValidatedAttachment validate(String originalName, String declaredMimeType,
            byte[] content, int currentAttachmentCount)
    {
        String safeName = normalizeName(originalName);
        byte[] bytes = Objects.requireNonNull(content, "content");
        if (bytes.length == 0)
        {
            throw invalid("附件内容不能为空");
        }
        if (bytes.length > properties.getMaxFileSize().toBytes())
        {
            throw invalid("附件大小超过限制");
        }
        if (currentAttachmentCount >= properties.getMaxFilesPerObject())
        {
            throw invalid("业务对象附件数量超过限制");
        }

        int dot = safeName.lastIndexOf('.');
        String baseName = safeName.substring(0, dot);
        String extension = safeName.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (baseName.indexOf('.') >= 0 || !MEDIA_TYPES.containsKey(extension))
        {
            throw invalid("附件扩展名不受支持");
        }
        String expectedMimeType = MEDIA_TYPES.get(extension);
        String normalizedMimeType = normalizeMimeType(declaredMimeType);
        if (!expectedMimeType.equals(normalizedMimeType) || !matchesSignature(extension, bytes))
        {
            throw invalid("附件类型与内容不一致");
        }
        return new ValidatedAttachment(safeName, extension, expectedMimeType);
    }

    private static String normalizeName(String originalName)
    {
        if (originalName == null)
        {
            throw invalid("附件名称不能为空");
        }
        String normalized = originalName.trim();
        if (normalized.isEmpty() || normalized.length() > 255
                || normalized.indexOf('/') >= 0 || normalized.indexOf('\\') >= 0
                || normalized.chars().anyMatch(Character::isISOControl))
        {
            throw invalid("附件名称无效");
        }
        int dot = normalized.lastIndexOf('.');
        if (dot <= 0 || dot == normalized.length() - 1)
        {
            throw invalid("附件扩展名不受支持");
        }
        return normalized;
    }

    private static String normalizeMimeType(String declaredMimeType)
    {
        if (declaredMimeType == null)
        {
            return "";
        }
        int parameters = declaredMimeType.indexOf(';');
        String value = parameters >= 0 ? declaredMimeType.substring(0, parameters) : declaredMimeType;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean matchesSignature(String extension, byte[] content)
    {
        return switch (extension)
        {
            case "png" -> startsWith(content, new int[] { 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a });
            case "jpg", "jpeg" -> startsWith(content, new int[] { 0xff, 0xd8, 0xff });
            case "pdf" -> startsWith(content, new int[] { 0x25, 0x50, 0x44, 0x46, 0x2d });
            default -> false;
        };
    }

    private static boolean startsWith(byte[] content, int[] signature)
    {
        if (content.length < signature.length)
        {
            return false;
        }
        for (int i = 0; i < signature.length; i++)
        {
            if ((content[i] & 0xff) != signature[i])
            {
                return false;
            }
        }
        return true;
    }

    private static LabBusinessException invalid(String message)
    {
        return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, message);
    }

    public record ValidatedAttachment(String originalName, String extension, String mimeType)
    {
    }
}
