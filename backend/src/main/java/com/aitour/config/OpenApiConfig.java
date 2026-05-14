/*
 * @author myoung
 */
package com.aitour.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * Swagger/OpenAPI 文档配置，通过注解声明项目基础信息和 JWT Bearer 鉴权方案。
 *
 * @author myoung
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "AiTourAssistant RESTful API",
                version = "0.0.1",
                description = "AI 旅游助手后端接口测试文档，覆盖认证、用户、行程、工具状态和流式行程生成接口。",
                contact = @Contact(name = "myoung")
        ),
        security = @SecurityRequirement(name = "bearerAuth")
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class OpenApiConfig {
}
