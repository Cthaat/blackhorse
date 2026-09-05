package com.ruoyi.integration.web.openapi;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import jakarta.servlet.http.HttpServletRequest;
import com.ruoyi.common.annotation.RepeatSubmit;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.framework.config.ResourcesConfig;
import com.ruoyi.framework.config.SecurityConfig;
import com.ruoyi.framework.config.properties.PermitAllUrlProperties;
import com.ruoyi.framework.interceptor.RepeatSubmitInterceptor;
import com.ruoyi.framework.security.filter.JwtAuthenticationTokenFilter;
import com.ruoyi.framework.security.handle.AuthenticationEntryPointImpl;
import com.ruoyi.framework.security.handle.LogoutSuccessHandlerImpl;
import com.ruoyi.framework.web.filter.TraceIdFilter;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.web.core.config.SwaggerConfig;
import com.github.xiaoymin.knife4j.spring.extension.Knife4jOpenApiCustomizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LabOpenApiTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LabOpenApiNonProdIT extends LabOpenApiServiceMocks
{
    private static final String TRACE_ID = "123e4567-e89b-12d3-a456-426614174000";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private Environment environment;

    @MockitoBean
    private TokenService tokenService;

    @MockitoBean
    private PermitAllUrlProperties permitAllUrlProperties;


    @DynamicPropertySource
    static void configureProfileRoot(DynamicPropertyRegistry registry)
    {
        registry.add("ruoyi.profile", LabOpenApiTestApplication::profileRoot);
    }

    @BeforeEach
    void stubAnonymousControllerUrls()
    {
        when(permitAllUrlProperties.getUrls()).thenReturn(Collections.emptyList());
    }

    @Test
    void anonymousUsersCanOpenKnife4jAndBothOpenApiDocuments() throws Exception
    {
        mockMvc.perform(get("/doc.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));

        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));

        mockMvc.perform(get("/v3/api-docs/lab"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    void documentationAllowlistDoesNotPermitNonGetRequests() throws Exception
    {
        for (String path : new String[]{"/doc.html", "/webjars/knife4j/", "/v3/api-docs", "/swagger-ui/index.html"})
        {
            mockMvc.perform(post(path))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"));
        }
    }

    @Test
    void anonymousProbeUsesUnifiedUnauthenticatedContract() throws Exception
    {
        mockMvc.perform(get("/lab/security-probe").header(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(TraceIdFilter.TRACE_ID_HEADER, TRACE_ID))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHENTICATED"))
                .andExpect(jsonPath("$.msg").value("未认证或登录状态已失效"))
                .andExpect(jsonPath("$.traceId").value(TRACE_ID))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void authenticatedProbeReturnsNoContentAndNoBody() throws Exception
    {
        mockMvc.perform(get("/lab/security-probe").with(user("student")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void labGroupContainsOnlyLabPathsAndDocumentsBearerErrorsAndTimezone() throws Exception
    {
        String body = mockMvc.perform(get("/v3/api-docs/lab"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode document = objectMapper.readTree(body);

        List<String> paths = new ArrayList<>();
        document.path("paths").fieldNames().forEachRemaining(paths::add);
        assertThat(paths).isNotEmpty().allMatch(path -> path.startsWith("/lab/"));
        assertThat(paths).contains("/lab/security-probe", "/lab/laboratories",
                "/lab/devices", "/lab/reservations", "/lab/repair-orders",
                "/lab/inspection-plans", "/lab/hazards", "/lab/dashboard/summary");

        JsonNode securityScheme = document.path("components").path("securitySchemes").path("BearerAuth");
        assertThat(securityScheme.path("type").asText()).isEqualTo("http");
        assertThat(securityScheme.path("scheme").asText()).isEqualTo("bearer");
        assertThat(securityScheme.path("bearerFormat").asText()).isEqualTo("JWT");
        assertThat(document.path("security").get(0).has("BearerAuth")).isTrue();

        JsonNode getOperation = document.path("paths").path("/lab/security-probe").path("get");
        assertThat(getOperation.path("responses").has("401")).isTrue();
        assertThat(getOperation.path("responses").has("403")).isTrue();
        assertThat(document.path("components").path("schemas").has("ErrorResponse")).isTrue();

        JsonNode reservationPost = document.path("paths").path("/lab/reservations").path("post");
        assertThat(reservationPost.path("responses").has("400")).isTrue();
        assertThat(reservationPost.path("responses").has("409")).isTrue();
        assertThat(reservationPost.path("description").asText())
                .contains("lab:reservation:apply");

        String description = document.path("info").path("description").asText();
        assertThat(description)
                .contains("ErrorResponse")
                .contains("401")
                .contains("403")
                .contains("Asia/Shanghai")
                .contains("+08:00");
    }

    @Test
    void nonProdRegistersSwaggerConfigurationAndOnlyTheLabGroup()
    {
        assertThat(applicationContext.getBeansOfType(SwaggerConfig.class)).hasSize(1);
        assertThat(applicationContext.getBeansOfType(GroupedOpenApi.class))
                .hasSize(1)
                .allSatisfy((name, group) -> assertThat(group.getGroup()).isEqualTo("lab"));
        assertThat(environment.getProperty("knife4j.enable", Boolean.class)).isFalse();
        assertThat(applicationContext.getBeansOfType(Knife4jOpenApiCustomizer.class)).isEmpty();
    }
}

@SpringBootConfiguration(proxyBeanMethods = false)
@TestComponent
@EnableAutoConfiguration(
        exclude = {
                DataSourceAutoConfiguration.class,
                DataSourceTransactionManagerAutoConfiguration.class,
                FlywayAutoConfiguration.class,
                HibernateJpaAutoConfiguration.class,
                QuartzAutoConfiguration.class,
                RedisAutoConfiguration.class,
                RedisRepositoriesAutoConfiguration.class
        },
        excludeName = "com.alibaba.druid.spring.boot3.autoconfigure.DruidDataSourceAutoConfigure")
@ComponentScan(basePackages = "com.ruoyi.web.controller.lab")
@Import({
        SwaggerConfig.class,
        SecurityConfig.class,
        ResourcesConfig.class,
        JwtAuthenticationTokenFilter.class,
        AuthenticationEntryPointImpl.class,
        LogoutSuccessHandlerImpl.class,
        TraceIdFilter.class
})
class LabOpenApiTestApplication
{
    static
    {
        new RuoYiConfig().setProfile(profileRoot());
    }

    @Bean
    RuoYiConfig ruoyiConfig()
    {
        RuoYiConfig config = new RuoYiConfig();
        config.setName("Lab Management");
        config.setVersion("test");
        config.setProfile(profileRoot());
        return config;
    }

    static String profileRoot()
    {
        return Path.of(System.getProperty("java.io.tmpdir"), "ruoyi-task7-openapi-profile")
                .toAbsolutePath().toString().replace('\\', '/');
    }

    @Bean
    Clock clock()
    {
        return Clock.fixed(Instant.parse("2026-09-02T04:34:56Z"), ZoneId.of("Asia/Shanghai"));
    }

    @Bean
    RepeatSubmitInterceptor repeatSubmitInterceptor()
    {
        return new RepeatSubmitInterceptor()
        {
            @Override
            public boolean isRepeatSubmit(HttpServletRequest request, RepeatSubmit annotation)
            {
                return false;
            }
        };
    }
}
