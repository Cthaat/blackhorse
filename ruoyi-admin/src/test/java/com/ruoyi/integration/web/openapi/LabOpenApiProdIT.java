package com.ruoyi.integration.web.openapi;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.framework.config.properties.PermitAllUrlProperties;
import com.ruoyi.framework.web.filter.TraceIdFilter;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.web.core.config.SwaggerConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = LabOpenApiTestApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("prod")
class LabOpenApiProdIT
{
    private static final String TRACE_ID = "223e4567-e89b-12d3-a456-426614174000";

    @Autowired
    private MockMvc mockMvc;

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
    void authenticatedUsersReceiveNotFoundForEveryDocumentationEntryPoint() throws Exception
    {
        for (String path : new String[]{"/doc.html", "/swagger-ui.html", "/v3/api-docs", "/v3/api-docs/lab"})
        {
            mockMvc.perform(get(path).with(user("operator")))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void productionStillServesTheExplicitProfileResourceHandler() throws Exception
    {
        assertThat(RuoYiConfig.getProfile()).isEqualTo(LabOpenApiTestApplication.profileRoot());
        Path profileFile = Path.of(LabOpenApiTestApplication.profileRoot(), "task7-profile-probe.txt");
        Files.createDirectories(profileFile.getParent());
        Files.writeString(profileFile, "profile-handler-ok", StandardCharsets.UTF_8);
        try
        {
            mockMvc.perform(get("/profile/task7-profile-probe.txt").with(user("operator")))
                    .andExpect(status().isOk())
                    .andExpect(content().string("profile-handler-ok"));
        }
        finally
        {
            Files.deleteIfExists(profileFile);
        }
    }

    @Test
    void anonymousProbeStillUsesUnifiedUnauthenticatedContract() throws Exception
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
    void authenticatedProbeRemainsAvailableWithoutResponseBody() throws Exception
    {
        mockMvc.perform(get("/lab/security-probe").with(user("operator")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    @Test
    void productionDoesNotRegisterSwaggerOrGroupedOpenApiBeans()
    {
        assertThat(applicationContext.getBeansOfType(SwaggerConfig.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(GroupedOpenApi.class)).isEmpty();
    }

    @Test
    void productionConfigurationExplicitlyDisablesAllDocumentationProviders()
    {
        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean.class)).isFalse();
        assertThat(environment.getProperty("knife4j.enable", Boolean.class)).isFalse();
    }
}
