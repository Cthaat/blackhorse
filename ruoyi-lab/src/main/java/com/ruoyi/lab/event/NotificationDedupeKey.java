package com.ruoyi.lab.event;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/** Builds stable keys from persisted business facts, never from wall-clock time. */
public final class NotificationDedupeKey
{
    private static final Pattern TOKEN = Pattern.compile("[A-Z0-9_]{1,32}");

    private NotificationDedupeKey()
    {
    }

    public static String forHistory(long historyId, String notificationType, long receiverId)
    {
        return "history:" + positive(historyId, "historyId") + ":"
                + token(notificationType, "notificationType") + ":"
                + positive(receiverId, "receiverId");
    }

    public static String forOverdue(String businessType, long objectId,
            long overdueEventVersion, long receiverId)
    {
        return "overdue:" + token(businessType, "businessType").toLowerCase(Locale.ROOT)
                + ":" + positive(objectId, "objectId") + ":"
                + positive(overdueEventVersion, "overdueEventVersion") + ":"
                + positive(receiverId, "receiverId");
    }

    private static String token(String value, String field)
    {
        String normalized = Objects.requireNonNull(value, field).trim().toUpperCase(Locale.ROOT);
        if (!TOKEN.matcher(normalized).matches())
        {
            throw new IllegalArgumentException(field + " must match " + TOKEN.pattern());
        }
        return normalized;
    }

    private static long positive(long value, String field)
    {
        if (value <= 0)
        {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }
}
