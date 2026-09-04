package com.ruoyi.web.core.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class DruidStatCredentialsConfigTest
{
    private static final String INVALID_ALLOW_MESSAGE =
            "Druid stat allow list must contain only specific IP addresses.";
    private static final String PROFILE_REQUIRED_MESSAGE =
            "Druid stat monitoring requires the druid-stat profile.";

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withPropertyValues(
                    "spring.profiles.active=druid-stat",
                    "spring.datasource.druid.web-stat-filter.enabled=true",
                    "spring.datasource.druid.stat-view-servlet.enabled=true",
                    "spring.datasource.druid.filter.stat.enabled=true",
                    "spring.datasource.druid.stat-view-servlet.login-username=${LAB_DRUID_STAT_USERNAME:}",
                    "spring.datasource.druid.stat-view-servlet.login-password=${LAB_DRUID_STAT_PASSWORD:}",
                    "spring.datasource.druid.stat-view-servlet.allow=${LAB_DRUID_STAT_ALLOW:127.0.0.1}")
            .withUserConfiguration(DruidStatCredentialsConfig.class);

    @Test
    void rejectsMissingCredentials()
    {
        assertRejected(contextRunner);
    }

    @Test
    void rejectsMissingRawCredentialsBeforeResolvingRequiredPlaceholders()
    {
        new ApplicationContextRunner()
                .withPropertyValues(
                        "spring.profiles.active=druid-stat",
                        "spring.datasource.druid.web-stat-filter.enabled=true",
                        "spring.datasource.druid.stat-view-servlet.enabled=true",
                        "spring.datasource.druid.filter.stat.enabled=true",
                        "spring.datasource.druid.stat-view-servlet.login-username=${LAB_DRUID_STAT_USERNAME}",
                        "spring.datasource.druid.stat-view-servlet.login-password=${LAB_DRUID_STAT_PASSWORD}",
                        "spring.datasource.druid.stat-view-servlet.allow=127.0.0.1")
                .withUserConfiguration(DruidStatCredentialsConfig.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(BeanInitializationException.class)
                            .hasMessage(DruidStatCredentialsConfig.INVALID_CREDENTIALS_MESSAGE);
                });
    }

    @Test
    void rejectsEmptyCredentials()
    {
        assertRejected(contextRunner.withPropertyValues(
                "LAB_DRUID_STAT_USERNAME=",
                "LAB_DRUID_STAT_PASSWORD="));
    }

    @Test
    void rejectsWhitespaceCredentials()
    {
        assertRejected(contextRunner.withPropertyValues(
                "LAB_DRUID_STAT_USERNAME=   ",
                "LAB_DRUID_STAT_PASSWORD=\t"));
    }

    @Test
    void activeProfileStartsWithCompleteCredentials()
    {
        contextRunner
                .withPropertyValues(
                        "LAB_DRUID_STAT_USERNAME=operator",
                        "LAB_DRUID_STAT_PASSWORD=test-value")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void rejectsEmptyOrWhitespaceAllowList()
    {
        assertAllowRejected("");
        assertAllowRejected("   ");
    }

    @Test
    void rejectsObviouslyBroadAllowEntries()
    {
        assertAllowRejected("*");
        assertAllowRejected("0.0.0.0/0");
        assertAllowRejected("::/0");
    }

    @Test
    void acceptsSpecificIpAllowEntries()
    {
        contextRunner
                .withPropertyValues(
                        "LAB_DRUID_STAT_USERNAME=operator",
                        "LAB_DRUID_STAT_PASSWORD=test-value",
                        "LAB_DRUID_STAT_ALLOW=127.0.0.1,::1")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void rejectsMonitoringEnabledOutsideDruidStatProfile()
    {
        new ApplicationContextRunner()
                .withUserConfiguration(DruidStatCredentialsConfig.class)
                .withPropertyValues("spring.datasource.druid.stat-view-servlet.enabled=true")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(BeanInitializationException.class)
                            .hasMessage(PROFILE_REQUIRED_MESSAGE);
                });
    }

    @Test
    void rejectsDisabledMonitoringComponentUnderDruidStatProfile()
    {
        contextRunner
                .withPropertyValues(
                        "LAB_DRUID_STAT_USERNAME=operator",
                        "LAB_DRUID_STAT_PASSWORD=test-value",
                        "spring.datasource.druid.web-stat-filter.enabled=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(BeanInitializationException.class)
                            .hasMessage(DruidStatCredentialsConfig.COMPONENTS_REQUIRED_MESSAGE);
                });
    }

    @Test
    void rejectsMalformedMonitoringEnabledValue()
    {
        contextRunner
                .withPropertyValues(
                        "LAB_DRUID_STAT_USERNAME=operator",
                        "LAB_DRUID_STAT_PASSWORD=test-value",
                        "spring.datasource.druid.stat-view-servlet.enabled=not-a-boolean")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(BeanInitializationException.class)
                            .hasMessage(DruidStatCredentialsConfig.INVALID_CONFIGURATION_MESSAGE);
                });
    }

    @Test
    void rejectsEffectiveCredentialOverrideEvenWhenLabCredentialsAreValid()
    {
        contextRunner
                .withPropertyValues(
                        "LAB_DRUID_STAT_USERNAME=operator",
                        "LAB_DRUID_STAT_PASSWORD=test-value",
                        "spring.datasource.druid.stat-view-servlet.login-username=",
                        "spring.datasource.druid.stat-view-servlet.login-password=test-value")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(BeanInitializationException.class)
                            .hasMessage(DruidStatCredentialsConfig.INVALID_CREDENTIALS_MESSAGE);
                });
    }

    @Test
    void rejectsEffectiveAllowOverrideEvenWhenLabAllowIsSafe()
    {
        contextRunner
                .withPropertyValues(
                        "LAB_DRUID_STAT_USERNAME=operator",
                        "LAB_DRUID_STAT_PASSWORD=test-value",
                        "LAB_DRUID_STAT_ALLOW=127.0.0.1",
                        "spring.datasource.druid.stat-view-servlet.allow=*")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(BeanInitializationException.class)
                            .hasMessage(INVALID_ALLOW_MESSAGE);
                });
    }

    private static void assertRejected(ApplicationContextRunner runner)
    {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure())
                    .isInstanceOf(BeanInitializationException.class)
                    .hasMessage(DruidStatCredentialsConfig.INVALID_CREDENTIALS_MESSAGE);
        });
    }

    private void assertAllowRejected(String allow)
    {
        contextRunner
                .withPropertyValues(
                        "LAB_DRUID_STAT_USERNAME=operator",
                        "LAB_DRUID_STAT_PASSWORD=test-value",
                        "LAB_DRUID_STAT_ALLOW=" + allow)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .isInstanceOf(BeanInitializationException.class)
                            .hasMessage(INVALID_ALLOW_MESSAGE);
                });
    }
}
