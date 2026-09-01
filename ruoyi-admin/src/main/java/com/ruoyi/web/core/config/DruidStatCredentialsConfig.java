package com.ruoyi.web.core.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Pattern;
import org.springframework.beans.factory.BeanInitializationException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.util.StringUtils;

/**
 * Fails closed when the optional Druid monitoring profile is activated without
 * complete credentials.
 */
@Configuration(proxyBeanMethods = false)
public class DruidStatCredentialsConfig
{
    static final String INVALID_CREDENTIALS_MESSAGE = "Druid stat credentials must be non-blank.";
    static final String INVALID_ALLOW_MESSAGE =
            "Druid stat allow list must contain only specific IP addresses.";
    static final String PROFILE_REQUIRED_MESSAGE =
            "Druid stat monitoring requires the druid-stat profile.";
    static final String COMPONENTS_REQUIRED_MESSAGE =
            "Druid stat profile requires all monitoring components to be enabled.";
    static final String INVALID_CONFIGURATION_MESSAGE = "Druid stat configuration is invalid.";
    private static final Pattern IP_ADDRESS_CHARACTERS = Pattern.compile("[0-9A-Fa-f:.]+");

    @Bean
    static BeanFactoryPostProcessor druidStatCredentialsValidator(Environment environment)
    {
        return beanFactory -> validateEffectiveConfiguration(environment);
    }

    static void validateEffectiveConfiguration(Environment environment)
    {
        Binder binder = Binder.get(environment);
        boolean webFilterEnabled = bindBoolean(
                binder,
                "spring.datasource.druid.web-stat-filter.enabled");
        boolean statViewEnabled = bindBoolean(
                binder,
                "spring.datasource.druid.stat-view-servlet.enabled");
        boolean statFilterEnabled = bindBoolean(
                binder,
                "spring.datasource.druid.filter.stat.enabled");
        boolean profileActive = environment.acceptsProfiles(Profiles.of("druid-stat"));
        boolean anyMonitoringEnabled = webFilterEnabled || statViewEnabled || statFilterEnabled;

        if (!profileActive)
        {
            if (anyMonitoringEnabled)
            {
                throw new BeanInitializationException(PROFILE_REQUIRED_MESSAGE);
            }
            return;
        }
        if (!webFilterEnabled || !statViewEnabled || !statFilterEnabled)
        {
            throw new BeanInitializationException(COMPONENTS_REQUIRED_MESSAGE);
        }

        validateCredentials(
                environment.getProperty("LAB_DRUID_STAT_USERNAME"),
                environment.getProperty("LAB_DRUID_STAT_PASSWORD"));
        validateCredentials(
                bindString(binder, "spring.datasource.druid.stat-view-servlet.login-username"),
                bindString(binder, "spring.datasource.druid.stat-view-servlet.login-password"));
        validateAllowList(bindString(binder, "spring.datasource.druid.stat-view-servlet.allow"));
    }

    static void validateCredentials(String username, String password)
    {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password))
        {
            throw new BeanInitializationException(INVALID_CREDENTIALS_MESSAGE);
        }
    }

    static void validateAllowList(String allowList)
    {
        if (!StringUtils.hasText(allowList))
        {
            throw new BeanInitializationException(INVALID_ALLOW_MESSAGE);
        }
        for (String entry : allowList.split(",", -1))
        {
            String addressText = entry.trim();
            if (!StringUtils.hasText(addressText) || !IP_ADDRESS_CHARACTERS.matcher(addressText).matches())
            {
                throw new BeanInitializationException(INVALID_ALLOW_MESSAGE);
            }
            try
            {
                if (InetAddress.getByName(addressText).isAnyLocalAddress())
                {
                    throw new BeanInitializationException(INVALID_ALLOW_MESSAGE);
                }
            }
            catch (UnknownHostException exception)
            {
                throw new BeanInitializationException(INVALID_ALLOW_MESSAGE);
            }
        }
    }

    private static boolean bindBoolean(Binder binder, String name)
    {
        try
        {
            return binder.bind(name, Boolean.class).orElse(false);
        }
        catch (RuntimeException exception)
        {
            throw new BeanInitializationException(INVALID_CONFIGURATION_MESSAGE);
        }
    }

    private static String bindString(Binder binder, String name)
    {
        try
        {
            return binder.bind(name, String.class).orElse(null);
        }
        catch (RuntimeException exception)
        {
            throw new BeanInitializationException(INVALID_CONFIGURATION_MESSAGE);
        }
    }
}
