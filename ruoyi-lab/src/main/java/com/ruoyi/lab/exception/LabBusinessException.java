package com.ruoyi.lab.exception;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Laboratory business exception containing client-safe data only.
 */
public final class LabBusinessException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private static final Pattern SAFE_MESSAGE = Pattern.compile(
            "\\A[\\p{IsHan}][\\p{IsHan}\\p{N}，。！？、：；（）《》“”‘’—…％%+\\- ]{0,199}\\z");

    private static final Pattern SAFE_DETAIL_KEY = Pattern.compile("\\A[A-Za-z][A-Za-z0-9]{0,63}\\z");

    private static final Pattern UNSAFE_CONTENT = Pattern.compile(
            "(?i)(?:sql|select|insert|update|delete|drop|alter|create|exception|stack|traceback|java\\.|jdbc|password|secret|"
                    + "数据库|异常|堆栈|栈跟踪|密码|密钥)");

    private final LabErrorCode errorCode;

    private final Map<String, String> details;

    public LabBusinessException(LabErrorCode errorCode, String message)
    {
        this(errorCode, message, null);
    }

    public LabBusinessException(LabErrorCode errorCode, String message, Map<String, String> details)
    {
        super(requireSafeMessage(message), null, false, false);
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode must not be null");
        this.details = immutableSafeDetails(details);
    }

    private static String requireSafeMessage(String message)
    {
        if (message == null || !SAFE_MESSAGE.matcher(message).matches()
                || UNSAFE_CONTENT.matcher(message).find())
        {
            throw new IllegalArgumentException("message must be a safe Chinese client message");
        }
        return message;
    }

    private static Map<String, String> immutableSafeDetails(Map<String, String> details)
    {
        if (details == null || details.isEmpty())
        {
            return Collections.emptyMap();
        }
        LinkedHashMap<String, String> copy = new LinkedHashMap<>();
        details.forEach((key, value) -> {
            if (key == null || !SAFE_DETAIL_KEY.matcher(key).matches())
            {
                throw new IllegalArgumentException("detail key is not safe");
            }
            if (value == null || value.isEmpty() || value.length() > 200
                    || value.chars().anyMatch(Character::isISOControl)
                    || UNSAFE_CONTENT.matcher(value).find())
            {
                throw new IllegalArgumentException("detail value is not safe");
            }
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }

    public LabErrorCode getErrorCode()
    {
        return errorCode;
    }

    public Map<String, String> getDetails()
    {
        return details;
    }
}
