package com.ruoyi.integration.security;

import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.common.exception.user.UserPasswordNotMatchException;
import com.ruoyi.framework.web.service.UserDetailsServiceImpl;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.framework.web.filter.TraceIdFilter;
import com.ruoyi.quartz.service.ISysJobService;
import com.ruoyi.system.service.ISysConfigService;
import com.ruoyi.system.service.ISysDictTypeService;
import com.ruoyi.system.service.ISysLogininforService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(LabSystemOperatorLoginIT.SafeLabDatabaseCondition.class)
class LabSystemOperatorLoginIT
{
    private static final String TRACE_ID = "123e4567-e89b-12d3-a456-426614174000";
    private static final long SYSTEM_OPERATOR_USER_ID = 9000L;
    private static final String SYSTEM_OPERATOR_USERNAME = "__lab_system_operator__";
    private static final List<String> PASSWORD_ATTEMPTS = List.of(
            "!NO_LOGIN!", "Wrong#42A", "Admin#42Pass");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @MockitoBean
    private ISysConfigService configService;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private RedisCache redisCache;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private ISysDictTypeService dictTypeService;

    @MockitoBean
    private ISysJobService jobService;

    @MockitoBean
    private ISysLogininforService logininforService;

    @Test
    void oversizedSystemOperatorUsernameFailsBeforeAuthenticationAndRemainsUnchanged()
            throws Exception
    {
        when(configService.selectCaptchaEnabled()).thenReturn(false);
        clearInvocations(configService, authenticationManager, redisCache, tokenService);
        assertThat(jdbcTemplate.queryForObject(
                "select count(*) from sys_user where user_id = ? or user_name = ?",
                Long.class, SYSTEM_OPERATOR_USER_ID, SYSTEM_OPERATOR_USERNAME)).isEqualTo(1L);
        OperatorSecurityState before = operatorSecurityState();
        assertThat(before.userId()).isEqualTo(SYSTEM_OPERATOR_USER_ID);
        assertThat(before.username()).isEqualTo(SYSTEM_OPERATOR_USERNAME);
        assertThat(before.password()).isEqualTo("!NO_LOGIN!");
        assertThat(before.status()).isEqualTo("1");
        assertThat(before.delFlag()).isEqualTo("0");
        assertThat(before.roleIds()).isEmpty();
        assertThat(before.postIds()).isEmpty();
        // Defense in depth: the reserved 23-character name is rejected by loginPreCheck
        // before black-list lookup or AuthenticationManager can inspect the disabled account.
        assertThat(SYSTEM_OPERATOR_USERNAME.length()).isGreaterThan(UserConstants.USERNAME_MAX_LENGTH);
        assertThat(PASSWORD_ATTEMPTS).allSatisfy(password ->
                assertThat(password.length()).isBetween(
                        UserConstants.PASSWORD_MIN_LENGTH, UserConstants.PASSWORD_MAX_LENGTH));

        for (String password : PASSWORD_ATTEMPTS)
        {
            String request = objectMapper.writeValueAsString(new LoginAttempt(SYSTEM_OPERATOR_USERNAME, password));
            mockMvc.perform(post("/login")
                            .header(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                    .andExpect(jsonPath("$.code").value(401))
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
                    .andExpect(jsonPath("$.msg").value("用户不存在/密码错误"))
                    .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                    .andExpect(jsonPath("$.timestamp").exists())
                    .andExpect(jsonPath("$.data").doesNotExist())
                    .andExpect(jsonPath("$.token").doesNotExist());
            assertThat(operatorSecurityState()).isEqualTo(before);
        }

        verify(configService, times(PASSWORD_ATTEMPTS.size())).selectCaptchaEnabled();
        verify(configService, never()).selectConfigByKey("sys.login.blackIPList");
        verifyNoInteractions(authenticationManager);
        verify(tokenService, never()).createToken(any());
        verify(tokenService, never()).verifyToken(any());
        verify(tokenService, never()).refreshToken(any());
        verify(redisCache, never()).getCacheObject(anyString());
        verify(redisCache, never()).deleteObject(anyString());
        verify(redisCache, never()).setCacheObject(
                anyString(), any(), anyInt(), any(TimeUnit.class));
    }

    @Test
    void disabledOperatorIsRejectedByRealUserDetailsAndSentinelIsNotBcryptCredential()
    {
        OperatorSecurityState operator = operatorSecurityState();
        assertThat(operator.status()).isEqualTo("1");
        assertThat(operator.password()).isEqualTo("!NO_LOGIN!");

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(SYSTEM_OPERATOR_USERNAME))
                .isInstanceOf(DisabledException.class)
                .hasMessage("用户不存在/密码错误")
                .hasMessageNotContaining(SYSTEM_OPERATOR_USERNAME);
        assertThat(passwordEncoder.matches("!NO_LOGIN!", operator.password())).isFalse();
    }

    @Test
    void wrappedPasswordFailureUsesUnified401WithoutCreatingToken() throws Exception
    {
        when(configService.selectCaptchaEnabled()).thenReturn(false);
        when(authenticationManager.authenticate(any())).thenThrow(
                new InternalAuthenticationServiceException(
                        "wrapped authentication failure",
                        new UserPasswordNotMatchException()));

        String response = login("valid_user", "Wrong#42A", TRACE_ID, 401);

        assertThat(response)
                .contains("\"errorCode\":\"UNAUTHENTICATED\"")
                .contains("\"msg\":\"用户不存在/密码错误\"")
                .contains("\"traceId\":\"" + TRACE_ID + "\"")
                .doesNotContain("wrapped authentication failure")
                .doesNotContain("token");
        verify(authenticationManager).authenticate(any());
        verify(tokenService, never()).createToken(any());
    }

    @Test
    void authenticationInfrastructureFailureRemainsSafe500() throws Exception
    {
        when(configService.selectCaptchaEnabled()).thenReturn(false);
        when(authenticationManager.authenticate(any())).thenThrow(
                new InternalAuthenticationServiceException(
                        "SQL SELECT secret FROM sys_user",
                        new IllegalStateException("database unavailable")));

        String response = login("valid_user", "Wrong#42A", TRACE_ID, 500);

        assertThat(response)
                .contains("\"errorCode\":\"INTERNAL_ERROR\"")
                .contains("\"msg\":\"系统内部错误\"")
                .contains("\"traceId\":\"" + TRACE_ID + "\"")
                .doesNotContainIgnoringCase("sql")
                .doesNotContain("database unavailable")
                .doesNotContain("token");
        verify(authenticationManager).authenticate(any());
        verify(tokenService, never()).createToken(any());
    }

    private String login(String username, String password, String traceId, int expectedStatus) throws Exception
    {
        String request = objectMapper.writeValueAsString(new LoginAttempt(username, password));
        return mockMvc.perform(post("/login")
                        .header(TraceIdFilter.TRACE_ID_HEADER, traceId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(expectedStatus))
                .andExpect(jsonPath("$.traceId").value(traceId))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.data").doesNotExist())
                .andExpect(jsonPath("$.token").doesNotExist())
                .andReturn().getResponse().getContentAsString();
    }

    private OperatorSecurityState operatorSecurityState()
    {
        OperatorRow operator = jdbcTemplate.queryForObject(
                "select user_id, user_name, password, status, del_flag, login_ip, login_date, pwd_update_date "
                        + "from sys_user where user_id = ?",
                (resultSet, rowNumber) -> new OperatorRow(
                        resultSet.getLong("user_id"),
                        resultSet.getString("user_name"),
                        resultSet.getString("password"),
                        resultSet.getString("status"),
                        resultSet.getString("del_flag"),
                        resultSet.getString("login_ip"),
                        resultSet.getTimestamp("login_date"),
                        resultSet.getTimestamp("pwd_update_date")),
                SYSTEM_OPERATOR_USER_ID);
        List<Long> roleIds = jdbcTemplate.queryForList(
                "select role_id from sys_user_role where user_id = ? order by role_id",
                Long.class, SYSTEM_OPERATOR_USER_ID);
        List<Long> postIds = jdbcTemplate.queryForList(
                "select post_id from sys_user_post where user_id = ? order by post_id",
                Long.class, SYSTEM_OPERATOR_USER_ID);
        return new OperatorSecurityState(
                operator.userId(), operator.username(), operator.password(), operator.status(), operator.delFlag(),
                operator.loginIp(), operator.loginDate(), operator.passwordUpdatedAt(), roleIds, postIds);
    }

    private record LoginAttempt(String username, String password)
    {
    }

    private record OperatorRow(long userId, String username, String password, String status, String delFlag,
            String loginIp, Timestamp loginDate, Timestamp passwordUpdatedAt)
    {
    }

    private record OperatorSecurityState(long userId, String username, String password, String status, String delFlag,
            String loginIp, Timestamp loginDate, Timestamp passwordUpdatedAt,
            List<Long> roleIds, List<Long> postIds)
    {
    }

    static final class SafeLabDatabaseCondition implements ExecutionCondition
    {
        private static final Pattern SAFE_URL = Pattern.compile(
                "\\Ajdbc:mysql://(?<host>localhost|127\\.0\\.0\\.1):(?<port>[0-9]{1,5})/"
                        + "(?<database>lab_test_[A-Za-z0-9_]+)(?:\\?[^\\s#]*)?\\z");

        @Override
        public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context)
        {
            String wrapperMarker = System.getenv("LAB_TEST_WRAPPER_ACTIVE");
            if (wrapperMarker == null || wrapperMarker.trim().isEmpty())
            {
                return ConditionEvaluationResult.disabled("real database tests require the safety wrapper");
            }
            require("true".equals(wrapperMarker),
                    "LAB_TEST_WRAPPER_ACTIVE must be exactly true");

            Matcher matcher = SAFE_URL.matcher(environment("LAB_TEST_DB_URL"));
            require(matcher.matches(),
                    "LAB_TEST_DB_URL must identify an isolated loopback lab_test database");
            int port = Integer.parseInt(matcher.group("port"));
            require(port >= 1 && port <= 65535,
                    "LAB_TEST_DB_URL must contain a valid TCP port");
            require(!isBlank(environment("LAB_TEST_DB_USERNAME"))
                            && !isBlank(environment("LAB_TEST_DB_PASSWORD")),
                    "LAB_TEST_DB credentials must be configured");
            require("true".equals(environment("LAB_TEST_FLYWAY_ENABLED")),
                    "LAB_TEST_FLYWAY_ENABLED must be exactly true");
            return ConditionEvaluationResult.enabled("isolated lab test database is configured");
        }

        private static String environment(String name)
        {
            String value = System.getenv(name);
            return value == null ? "" : value;
        }

        private static void require(boolean valid, String reason)
        {
            if (!valid)
            {
                throw new IllegalStateException(reason);
            }
        }

        private static boolean isBlank(String value)
        {
            return value.trim().isEmpty();
        }
    }
}
