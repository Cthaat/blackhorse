package com.ruoyi.integration.web.exception;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.ExceptionTranslationFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import com.ruoyi.framework.config.SecurityConfig;
import com.ruoyi.framework.config.properties.PermitAllUrlProperties;
import com.ruoyi.framework.security.filter.JwtAuthenticationTokenFilter;
import com.ruoyi.framework.security.handle.AuthenticationEntryPointImpl;
import com.ruoyi.framework.security.handle.LogoutSuccessHandlerImpl;
import com.ruoyi.framework.web.exception.GlobalExceptionHandler;
import com.ruoyi.framework.web.filter.TraceIdFilter;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.common.exception.user.BlackListException;
import com.ruoyi.common.exception.user.CaptchaException;
import com.ruoyi.common.exception.user.CaptchaExpireException;
import com.ruoyi.common.exception.user.UserNotExistsException;
import com.ruoyi.common.exception.user.UserPasswordNotMatchException;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.lab.config.LabTimeConfig;
import com.ruoyi.lab.exception.LabBusinessException;
import com.ruoyi.lab.exception.LabErrorCode;
import com.ruoyi.web.core.handler.LabExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(useDefaultFilters = false)
@Import({
        LabExceptionContractTest.ContractController.class,
        SecurityConfig.class,
        JwtAuthenticationTokenFilter.class,
        AuthenticationEntryPointImpl.class,
        LogoutSuccessHandlerImpl.class,
        TraceIdFilter.class,
        SpringUtils.class,
        GlobalExceptionHandler.class,
        LabExceptionHandler.class,
        LabExceptionContractTest.ContractTestConfiguration.class
})
class LabExceptionContractTest
{
    private static final String TRACE_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final String TIMESTAMP = "2026-09-02T12:34:56+08:00";
    private static final String UUID_PATTERN =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SecurityFilterChain securityFilterChain;

    @Autowired
    private AuthenticationEntryPointImpl securityErrorHandler;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private PermitAllUrlProperties permitAllUrlProperties;

    @Test
    void mvcSliceUsesProductionSecurityWithoutDatabaseInfrastructure() throws Exception
    {
        assertThat(applicationContext.getBeansOfType(DataSource.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(Flyway.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(Clock.class)).hasSize(1);
        assertThat(OrderUtils.getOrder(TraceIdFilter.class)).isEqualTo(Ordered.HIGHEST_PRECEDENCE);

        mockMvc.perform(get("/contract/ok").with(user("student")))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedRequestUsesUnified401Contract() throws Exception
    {
        mockMvc.perform(get("/contract/ok").header(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.msg").value("未认证或登录状态已失效"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.timestamp").value(TIMESTAMP))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void invalidCredentialsUseSafeUnified401Contract() throws Exception
    {
        for (String failure : List.of("unknown-user", "wrong-password"))
        {
            mockMvc.perform(get("/contract/invalid-credentials/{failure}", failure)
                            .with(user("student"))
                            .header(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID))
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.msg").value("用户不存在/密码错误"))
                    .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                    .andExpect(jsonPath("$.timestamp").value(TIMESTAMP))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }
    }

    @Test
    void captchaFailuresUseUnified400Contract() throws Exception
    {
        Map<String, String> failures = Map.of(
                "invalid", "验证码错误",
                "expired", "验证码已失效");
        for (Map.Entry<String, String> failure : failures.entrySet())
        {
            mockMvc.perform(get("/contract/captcha/{failure}", failure.getKey())
                            .with(user("student"))
                            .header(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400))
                    .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                    .andExpect(jsonPath("$.msg").value(failure.getValue()))
                    .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                    .andExpect(jsonPath("$.timestamp").value(TIMESTAMP));
        }
    }

    @Test
    void blacklistedNetworkUsesSafeUnified403Contract() throws Exception
    {
        mockMvc.perform(get("/contract/blacklisted-network")
                        .with(user("student"))
                        .header(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.msg").value("当前网络环境不允许登录"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.timestamp").value(TIMESTAMP));
    }

    @Test
    void validationFailureUsesUnified400Contract() throws Exception
    {
        mockMvc.perform(post("/contract/validation")
                        .with(user("student"))
                        .header(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.msg").value("请求参数校验失败"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.timestamp").value(TIMESTAMP));
    }

    @Test
    void accessDeniedUsesUnified403Contract() throws Exception
    {
        mockMvc.perform(get("/contract/denied")
                        .with(user("student"))
                        .header(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.errorCode").value("ACCESS_DENIED"))
                .andExpect(jsonPath("$.msg").value("没有权限访问该资源"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.timestamp").value(TIMESTAMP));
    }

    @Test
    void securityFilterChainRegistersUnifiedAccessDeniedHandler()
    {
        ExceptionTranslationFilter exceptionTranslationFilter = securityFilterChain.getFilters().stream()
                .filter(ExceptionTranslationFilter.class::isInstance)
                .map(ExceptionTranslationFilter.class::cast)
                .findFirst()
                .orElseThrow();

        assertThat(ReflectionTestUtils.getField(exceptionTranslationFilter, "accessDeniedHandler"))
                .isSameAs(securityErrorHandler);
    }

    @Test
    void accessDeniedHandlerWritesUnified403Contract() throws Exception
    {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/contract/security-denied");
        request.setAttribute(TraceIdFilter.TRACE_ID_ATTRIBUTE, TRACE_ID);
        MockHttpServletResponse response = new MockHttpServletResponse();

        securityErrorHandler.handle(request, response,
                new AccessDeniedException("SQL java.lang.IllegalStateException at com.ruoyi.Secret:42"));

        JsonNode body = objectMapper.readTree(response.getContentAsString());
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getHeader(TraceIdFilter.TRACE_ID_HEADER)).isEqualTo(TRACE_ID);
        assertThat(MediaType.parseMediaType(response.getContentType()).isCompatibleWith(MediaType.APPLICATION_JSON))
                .isTrue();
        assertThat(body.path("code").asInt()).isEqualTo(403);
        assertThat(body.path("errorCode").asText()).isEqualTo("ACCESS_DENIED");
        assertThat(body.path("msg").asText()).isEqualTo("没有权限访问该资源");
        assertThat(body.path("traceId").asText()).isEqualTo(TRACE_ID);
        assertThat(body.path("timestamp").asText()).isEqualTo(TIMESTAMP);
        assertThat(body.has("data")).isFalse();
        assertThat(response.getContentAsString())
                .doesNotContainIgnoringCase("sql")
                .doesNotContain("IllegalStateException")
                .doesNotContain("com.ruoyi.Secret");
    }

    @Test
    void missingLabObjectUsesUnified404Contract() throws Exception
    {
        mockMvc.perform(get("/contract/business/RESOURCE_NOT_FOUND")
                        .with(user("student"))
                        .header(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.msg").value("请求的业务对象不存在"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.timestamp").value(TIMESTAMP));
    }

    @Test
    void businessConflictUsesUnified409ContractAndImmutableData() throws Exception
    {
        mockMvc.perform(get("/contract/business/LAB_DEVICE_UNAVAILABLE")
                        .with(user("student"))
                        .header(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.errorCode").value("LAB_DEVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.msg").value("该设备当前不可用"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.timestamp").value(TIMESTAMP))
                .andExpect(jsonPath("$.data.deviceId").value("42"));
    }

    @Test
    void outOfDataScopeUses403InsteadOfConflict() throws Exception
    {
        mockMvc.perform(get("/contract/business/LAB_OUT_OF_DATA_SCOPE")
                        .with(user("student"))
                        .header(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.errorCode").value("LAB_OUT_OF_DATA_SCOPE"));
    }

    @Test
    void unknownExceptionUsesSafe500Contract() throws Exception
    {
        String body = mockMvc.perform(get("/contract/internal")
                        .with(user("student"))
                        .header(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.errorCode").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.msg").value("系统内部错误"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.timestamp").value(TIMESTAMP))
                .andReturn().getResponse().getContentAsString();

        assertThat(body)
                .doesNotContainIgnoringCase("sql")
                .doesNotContain("IllegalStateException")
                .doesNotContain("java.lang")
                .doesNotContain("SELECT secret")
                .doesNotContain("at com.ruoyi");
    }

    @Test
    void traceFilterAcceptsOnlyOneStrictCanonicalUuid() throws Exception
    {
        mockMvc.perform(get("/contract/trace")
                        .with(user("student"))
                        .header(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(content().string(TRACE_ID));

        for (String invalid : Arrays.asList(
                "",
                " " + TRACE_ID,
                TRACE_ID.toUpperCase(),
                "1-1-1-1-1",
                "{" + TRACE_ID + "}"))
        {
            String generated = mockMvc.perform(get("/contract/trace")
                            .with(user("student"))
                            .header(TraceIdFilter.TRACE_ID_HEADER, invalid))
                    .andExpect(status().isOk())
                    .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER,
                            org.hamcrest.Matchers.matchesPattern(UUID_PATTERN)))
                    .andReturn().getResponse().getContentAsString();
            assertThat(generated).matches(UUID_PATTERN).isNotEqualTo(invalid);
        }

        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void traceFilterRejectsMultipleHeaderValues() throws Exception
    {
        String anotherTraceId = "223e4567-e89b-12d3-a456-426614174000";

        String generated = mockMvc.perform(get("/contract/trace")
                        .with(user("student"))
                        .header(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID, anotherTraceId))
                .andExpect(status().isOk())
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER,
                        org.hamcrest.Matchers.matchesPattern(UUID_PATTERN)))
                .andReturn().getResponse().getContentAsString();

        assertThat(generated).matches(UUID_PATTERN)
                .isNotEqualTo(TRACE_ID)
                .isNotEqualTo(anotherTraceId);
        assertThat(MDC.get(TraceIdFilter.MDC_KEY)).isNull();
    }

    @Test
    void errorCodesAreExactAndHaveStableHttpStatuses()
    {
        assertThat(LabErrorCode.values())
                .extracting(Enum::name)
                .containsExactly(
                        "VALIDATION_ERROR",
                        "UNAUTHENTICATED",
                        "ACCESS_DENIED",
                        "RESOURCE_NOT_FOUND",
                        "INTERNAL_ERROR",
                        "LAB_RESERVATION_TIME_CONFLICT",
                        "LAB_QUALIFICATION_INVALID",
                        "LAB_DEVICE_UNAVAILABLE",
                        "LAB_LABORATORY_DISABLED",
                        "LAB_MAJOR_HAZARD_BLOCKED",
                        "LAB_ILLEGAL_STATE_TRANSITION",
                        "LAB_DUPLICATE_OPERATION",
                        "LAB_OUT_OF_DATA_SCOPE",
                        "LAB_REPAIR_ALREADY_OPEN");

        assertThat(LabErrorCode.VALIDATION_ERROR.getHttpStatus()).isEqualTo(400);
        assertThat(LabErrorCode.UNAUTHENTICATED.getHttpStatus()).isEqualTo(401);
        assertThat(LabErrorCode.ACCESS_DENIED.getHttpStatus()).isEqualTo(403);
        assertThat(LabErrorCode.RESOURCE_NOT_FOUND.getHttpStatus()).isEqualTo(404);
        assertThat(LabErrorCode.INTERNAL_ERROR.getHttpStatus()).isEqualTo(500);
        assertThat(LabErrorCode.LAB_OUT_OF_DATA_SCOPE.getHttpStatus()).isEqualTo(403);
        assertThat(Arrays.stream(LabErrorCode.values())
                .filter(code -> code.name().startsWith("LAB_"))
                .filter(code -> code != LabErrorCode.LAB_OUT_OF_DATA_SCOPE))
                .allMatch(code -> code.getHttpStatus() == 409);
    }

    @Test
    void businessExceptionAllowsOnlySafeChineseMessageAndCopiesDetails()
    {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("deviceId", "42");
        LabBusinessException exception = new LabBusinessException(
                LabErrorCode.LAB_DEVICE_UNAVAILABLE, "该设备当前不可用", details);
        details.put("deviceId", "99");

        assertThat(exception.getErrorCode()).isEqualTo(LabErrorCode.LAB_DEVICE_UNAVAILABLE);
        assertThat(exception.getMessage()).isEqualTo("该设备当前不可用");
        assertThat(exception.getDetails()).containsExactly(Map.entry("deviceId", "42"));
        assertThat(exception.getCause()).isNull();
        assertThatThrownBy(() -> exception.getDetails().put("deviceId", "99"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> new LabBusinessException(
                LabErrorCode.INTERNAL_ERROR, "SQL SELECT secret FROM lab_device"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LabBusinessException(
                LabErrorCode.INTERNAL_ERROR, "数据库异常"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(Arrays.stream(LabBusinessException.class.getConstructors())
                .flatMap(constructor -> Arrays.stream(constructor.getParameterTypes())))
                .noneMatch(Throwable.class::isAssignableFrom);
    }

    @Test
    void productionClockIsTheOnlyAsiaShanghaiClockBean()
    {
        try (org.springframework.context.annotation.AnnotationConfigApplicationContext context =
                     new org.springframework.context.annotation.AnnotationConfigApplicationContext(LabTimeConfig.class))
        {
            assertThat(context.getBeansOfType(Clock.class)).hasSize(1);
            assertThat(context.getBean(Clock.class).getZone()).isEqualTo(ZoneId.of("Asia/Shanghai"));
        }
    }

    @RestController
    @RequestMapping("/contract")
    static class ContractController
    {
        @GetMapping("/ok")
        String ok()
        {
            return "ok";
        }

        @PostMapping("/validation")
        String validation(@Valid @RequestBody ValidationRequest request)
        {
            return request.name();
        }

        @GetMapping("/denied")
        @PreAuthorize("hasRole('LAB_ADMIN')")
        String denied()
        {
            throw new AccessDeniedException("sensitive role detail");
        }

        @GetMapping("/business/{errorCode}")
        String business(@PathVariable LabErrorCode errorCode)
        {
            if (errorCode == LabErrorCode.RESOURCE_NOT_FOUND)
            {
                throw new LabBusinessException(errorCode, "请求的业务对象不存在");
            }
            if (errorCode == LabErrorCode.LAB_OUT_OF_DATA_SCOPE)
            {
                throw new LabBusinessException(errorCode, "无权访问该范围内的数据");
            }
            throw new LabBusinessException(errorCode, "该设备当前不可用", Map.of("deviceId", "42"));
        }

        @GetMapping("/internal")
        String internal()
        {
            throw new IllegalStateException(
                    "SQL SELECT secret FROM lab_device failed: java.sql.SQLException at com.ruoyi.Secret:42");
        }

        @GetMapping("/invalid-credentials/{failure}")
        String invalidCredentials(@PathVariable String failure)
        {
            if ("unknown-user".equals(failure))
            {
                throw new UserNotExistsException();
            }
            throw new UserPasswordNotMatchException();
        }

        @GetMapping("/captcha/{failure}")
        String captcha(@PathVariable String failure)
        {
            if ("expired".equals(failure))
            {
                throw new CaptchaExpireException();
            }
            throw new CaptchaException();
        }

        @GetMapping("/blacklisted-network")
        String blacklistedNetwork()
        {
            throw new BlackListException();
        }

        @GetMapping("/trace")
        String trace(HttpServletRequest request)
        {
            return String.valueOf(request.getAttribute("traceId"));
        }
    }

    record ValidationRequest(@NotBlank String name)
    {
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ContractTestConfiguration
    {
        @Bean
        Clock clock()
        {
            return Clock.fixed(Instant.parse("2026-09-02T04:34:56Z"), ZoneId.of("Asia/Shanghai"));
        }

        @Bean
        CorsFilter corsFilter()
        {
            CorsConfiguration configuration = new CorsConfiguration();
            configuration.addAllowedOriginPattern("*");
            configuration.addAllowedHeader("*");
            configuration.addAllowedMethod("*");
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            source.registerCorsConfiguration("/**", configuration);
            return new CorsFilter(source);
        }
    }
}
