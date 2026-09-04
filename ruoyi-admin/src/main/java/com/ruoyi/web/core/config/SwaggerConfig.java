package com.ruoyi.web.core.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import com.ruoyi.common.config.RuoYiConfig;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
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
            .addOperationCustomizer(labOperationCustomizer())
            .build();
    }

    /**
     * Documents the authorization expression and the stable laboratory error
     * contract for every business operation without duplicating it in each
     * controller method.
     */
    @Bean
    public OperationCustomizer labOperationCustomizer()
    {
        return (operation, handlerMethod) -> {
            PreAuthorize authorization = handlerMethod.getMethodAnnotation(PreAuthorize.class);
            if (authorization != null)
            {
                String permission = "所需权限：" + authorization.value();
                String description = operation.getDescription();
                operation.setDescription(description == null || description.isBlank()
                        ? permission : description + "\n\n" + permission);
            }
            addErrorResponse(operation.getResponses(), "400", "请求参数或业务输入无效");
            addErrorResponse(operation.getResponses(), "401", "未认证或登录状态已失效");
            addErrorResponse(operation.getResponses(), "403", "无功能权限或对象不在授权范围");
            addErrorResponse(operation.getResponses(), "404", "业务对象不存在或对当前用户不可见");
            addErrorResponse(operation.getResponses(), "409", "状态冲突、重复命令或业务规则阻断");
            addErrorResponse(operation.getResponses(), "500", "未预期错误，仅返回追踪编号");
            return operation;
        };
    }

    private static void addErrorResponse(io.swagger.v3.oas.models.responses.ApiResponses responses,
            String status, String description)
    {
        if (responses.containsKey(status))
        {
            return;
        }
        Schema<?> schema = new Schema<>().$ref("#/components/schemas/ErrorResponse");
        Content content = new Content().addMediaType("application/json",
                new MediaType().schema(schema));
        responses.addApiResponse(status, new ApiResponse().description(description).content(content));
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
