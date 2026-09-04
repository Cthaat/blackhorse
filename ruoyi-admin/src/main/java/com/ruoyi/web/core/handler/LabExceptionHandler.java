package com.ruoyi.web.core.handler;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.ErrorResponse;
import com.ruoyi.framework.web.filter.TraceIdFilter;
import com.ruoyi.lab.exception.LabBusinessException;

/**
 * Laboratory API exception contract. Kept in admin to preserve framework-to-lab dependency direction.
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class LabExceptionHandler
{
    private final Clock clock;

    public LabExceptionHandler(Clock clock)
    {
        this.clock = clock;
    }

    @ExceptionHandler(LabBusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(LabBusinessException exception,
            HttpServletRequest request)
    {
        int status = exception.getErrorCode().getHttpStatus();
        return error(status, exception.getErrorCode().name(), exception.getMessage(), exception.getDetails(), request);
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HandlerMethodValidationException.class,
            HttpMessageNotReadableException.class
    })
    public ResponseEntity<ErrorResponse> handleValidationException(Exception exception,
            HttpServletRequest request)
    {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "请求参数校验失败", null, request);
    }

    private ResponseEntity<ErrorResponse> error(int status, String errorCode, String message,
            java.util.Map<String, String> data, HttpServletRequest request)
    {
        String traceId = traceId(request);
        ErrorResponse response = new ErrorResponse(
                status, errorCode, message, traceId, OffsetDateTime.now(clock), data);
        return ResponseEntity.status(status)
                .header(TraceIdFilter.TRACE_ID_HEADER, traceId)
                .body(response);
    }

    private static String traceId(HttpServletRequest request)
    {
        Object traceId = request.getAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE);
        return traceId instanceof String value && !value.isEmpty() ? value : UUID.randomUUID().toString();
    }
}
