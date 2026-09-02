package com.ruoyi.web.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springdoc.core.models.GroupedOpenApi;
import com.ruoyi.common.config.RuoYiConfig;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * 实验室 OpenAPI 接口配置
 * 
 * @author ruoyi
 */
@Configuration
@Profile("!prod")
public class SwaggerConfig
{
    public static final String SECURITY_SCHEME_NAME = "BearerAuth";

    /** 系统基础配置 */
    @Autowired
    private RuoYiConfig ruoyiConfig;
    
    /**
     * 自定义的 OpenAPI 对象
     */
    @Bean
    public OpenAPI customOpenApi()
    {
        return new OpenAPI().components(new Components()
            // 设置认证的请求头
            .addSecuritySchemes(SECURITY_SCHEME_NAME, securityScheme()))
            .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
            .info(getApiInfo());
    }

    /**
     * 仅发布实验室控制器及 /lab 路径。
     */
    @Bean
    public GroupedOpenApi labOpenApi()
    {
        return GroupedOpenApi.builder()
            .group("lab")
            .packagesToScan("com.ruoyi.web.controller.lab")
            .pathsToMatch("/lab/**")
            .build();
    }

    @Bean
    public SecurityScheme securityScheme()
    {
        return new SecurityScheme()
            .type(SecurityScheme.Type.HTTP)
            .scheme("bearer")
            .bearerFormat("JWT");
    }
    
    /**
     * 添加摘要信息
     */
    public Info getApiInfo()
    {
        return new Info()
            // 设置标题
            .title("实验室管理系统 API")
            // 描述
            .description("错误响应统一使用 ErrorResponse：401 表示未认证或登录凭据无效，403 表示无权访问；"
                + "业务时间统一使用 Asia/Shanghai（+08:00）。")
            // 作者信息
            .contact(new Contact().name(ruoyiConfig.getName()))
            // 版本
            .version(ruoyiConfig.getVersion());
    }
}
