package com.ruoyi.lab.service;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;

/** Plain text only: no expression engine and no receiver variables. */
public final class MessageDeliveryPolicy
{
    public static final Set<String> VARIABLES = Set.of("eventType", "businessType", "businessId", "title", "content");
    private static final Pattern VARIABLE = Pattern.compile("\\$\\{([^}]+)}");
    private MessageDeliveryPolicy() { }

    public static Integer retryMinutes(int attempt)
    {
        return switch (attempt) { case 1 -> 1; case 2 -> 5; case 3 -> 15; case 4 -> 60; default -> null; };
    }

    public static boolean optional(String type) { return "INSPECTION_TASK_OVERDUE".equals(type); }

    public static void validateTemplate(String text, int max)
    {
        if (text == null || text.isBlank() || text.length() > max || text.contains("<") || text.contains(">"))
            throw invalid("模板只允许有长度限制的纯文本");
        var matcher = VARIABLE.matcher(text);
        while (matcher.find()) if (!VARIABLES.contains(matcher.group(1))) throw invalid("模板变量不在白名单内");
        String remainder = VARIABLE.matcher(text).replaceAll("");
        if (remainder.contains("${") || remainder.contains("#{")) throw invalid("模板不支持表达式");
    }

    public static String render(String text, Map<String, String> values)
    {
        var matcher = VARIABLE.matcher(text);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) matcher.appendReplacement(result, java.util.regex.Matcher.quoteReplacement(values.getOrDefault(matcher.group(1), "")));
        matcher.appendTail(result);
        return result.toString();
    }

    public static LabBusinessException invalid(String message) { return new LabBusinessException(LabErrorCode.VALIDATION_ERROR, message); }
}
