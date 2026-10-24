package com.campus.perks.boot.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.*;

@Configuration
public class OpenApiConfiguration {
  @Bean
  OpenAPI campusOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("校园周边商家优惠聚合分享平台")
                .version("v1")
                .description("框架阶段：标记占位的接口返回 501，受保护资源需要有效 JWT。"))
        .components(
            new Components()
                .addSecuritySchemes(
                    "bearerAuth",
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")));
  }
}
