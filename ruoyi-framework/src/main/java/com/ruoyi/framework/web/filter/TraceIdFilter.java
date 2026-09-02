package com.ruoyi.framework.web.filter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes one canonical trace identifier for every HTTP request.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter
{
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    public static final String TRACE_ID_ATTRIBUTE = "traceId";

    public static final String MDC_KEY = "traceId";

    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "\\A[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\z");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException
    {
        String traceId = resolveTraceId(request);
        request.setAttribute(TRACE_ID_ATTRIBUTE, traceId);
        MDC.put(MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try
        {
            filterChain.doFilter(request, response);
        }
        finally
        {
            MDC.remove(MDC_KEY);
        }
    }

    private static String resolveTraceId(HttpServletRequest request)
    {
        List<String> values = Collections.list(request.getHeaders(TRACE_ID_HEADER));
        if (values.size() == 1 && isCanonicalUuid(values.get(0)))
        {
            return values.get(0);
        }
        return UUID.randomUUID().toString();
    }

    private static boolean isCanonicalUuid(String value)
    {
        if (value == null || !CANONICAL_UUID.matcher(value).matches())
        {
            return false;
        }
        try
        {
            return UUID.fromString(value).toString().equals(value);
        }
        catch (IllegalArgumentException ignored)
        {
            return false;
        }
    }
}
