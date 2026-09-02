package com.ruoyi.framework.security.handle;

import java.io.IOException;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.ErrorResponse;
import com.ruoyi.framework.web.filter.TraceIdFilter;

/**
 * 认证失败处理类 返回未授权
 * 
 * @author ruoyi
 */
@Component
public class AuthenticationEntryPointImpl implements AuthenticationEntryPoint, AccessDeniedHandler, Serializable
{
    private static final long serialVersionUID = -8970718410437077606L;

    private final transient ObjectMapper objectMapper;

    private final transient Clock clock;

    public AuthenticationEntryPointImpl(ObjectMapper objectMapper, Clock clock)
    {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException e)
            throws IOException
    {
        writeError(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "未认证或登录状态已失效");
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException e)
            throws IOException
    {
        writeError(request, response, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "没有权限访问该资源");
    }

    private void writeError(HttpServletRequest request, HttpServletResponse response, int status, String errorCode,
            String message) throws IOException
    {
        String traceId = traceId(request);
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(TraceIdFilter.TRACE_ID_HEADER, traceId);
        objectMapper.writeValue(response.getWriter(),
                new ErrorResponse(status, errorCode, message, traceId, OffsetDateTime.now(clock)));
    }

    private static String traceId(HttpServletRequest request)
    {
        Object traceId = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return traceId instanceof String value && !value.isEmpty() ? value : UUID.randomUUID().toString();
    }
}
