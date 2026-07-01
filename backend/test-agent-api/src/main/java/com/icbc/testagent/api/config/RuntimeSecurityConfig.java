package com.icbc.testagent.api.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsWebFilter;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

/**
 * WebFlux 安全基础配置。正式鉴权由 ApiTokenWebFilter 占位处理，Spring Security 这里只关闭默认登录入口。
 */
@Configuration
public class RuntimeSecurityConfig {

    private final List<String> corsAllowedOrigins;

    /**
     * 解析逗号分隔 CORS origin 白名单，空白项会被忽略。
     */
    public RuntimeSecurityConfig(
            @Value("${test-agent.security.cors-allowed-origins:http://localhost:3000,http://127.0.0.1:3000,http://localhost:4173,http://127.0.0.1:4173,http://localhost:4177,http://127.0.0.1:4177,http://localhost:4187,http://127.0.0.1:4187,http://localhost:5173,http://127.0.0.1:5173,http://localhost:5174,http://127.0.0.1:5174}")
            String corsAllowedOrigins) {
        this.corsAllowedOrigins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /**
     * 配置 WebFlux 安全链：禁用默认登录/Basic/CSRF，鉴权由 ApiTokenWebFilter 处理。
     */
    @Bean
    SecurityWebFilterChain securityWebFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .cors(ServerHttpSecurity.CorsSpec::disable)
                .authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
                .build();
    }

    /**
     * 配置全局高优先级 CORS 过滤器，使其在所有自定义鉴权/路由过滤器前执行，
     * 保证哪怕是拦截并直接写回的响应（如 401 或跨后端路由转发）也能携带 CORS 响应头。
     */
    @Bean
    @Order(Ordered.HIGHEST_PRECEDENCE + 5)
    CorsWebFilter corsWebFilter() {
        return new CorsWebFilter(corsConfigurationSource());
    }

    /**
     * 构造 CORS 配置，暴露 X-Trace-Id 并允许前端发送 Last-Event-ID。
     */
    @Bean
    UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        if (corsAllowedOrigins.size() == 1 && "*".equals(corsAllowedOrigins.get(0))) {
            configuration.setAllowedOriginPatterns(List.of("*"));
        } else {
            configuration.setAllowedOrigins(corsAllowedOrigins);
        }
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Trace-Id", "Last-Event-ID"));
        configuration.setExposedHeaders(List.of("X-Trace-Id"));
        configuration.setAllowCredentials(false);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
