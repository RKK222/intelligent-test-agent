package com.enterprise.testagent.xxljob;

import com.enterprise.testagent.xxljob.admin.PlatformXxlJobAdminApplication;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.stereotype.Component;

/** 使用高优先级属性源启动与平台 WebFlux 完全独立的 Servlet/Tomcat Admin 上下文。 */
@Component
public class DefaultXxlJobAdminContextLauncher implements XxlJobAdminContextLauncher {

    private final XxlJobProperties properties;

    public DefaultXxlJobAdminContextLauncher(XxlJobProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public ConfigurableApplicationContext launch(XxlJobAdminBridge bridge) {
        Map<String, Object> childProperties = childProperties();
        return new SpringApplicationBuilder(PlatformXxlJobAdminApplication.class)
                .web(WebApplicationType.SERVLET)
                .profiles("xxl-admin-child")
                .initializers(context -> {
                    context.getEnvironment().getPropertySources()
                            .addFirst(new MapPropertySource("platformXxlJobAdmin", childProperties));
                    context.getBeanFactory().registerSingleton("xxlJobAdminBridge", bridge);
                })
                .run();
    }

    Map<String, Object> childProperties() {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("spring.application.name", "test-agent-xxl-job-admin");
        values.put("spring.main.web-application-type", "servlet");
        values.put("spring.main.banner-mode", "off");
        values.put("server.port", properties.getAdmin().getPort());
        values.put("server.servlet.context-path", properties.getAdmin().getContextPath());
        values.put("server.shutdown", "graceful");
        values.put("spring.datasource.type", "com.zaxxer.hikari.HikariDataSource");
        values.put("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        values.put("spring.datasource.url", properties.getMysql().getUrl());
        values.put("spring.datasource.username", properties.getMysql().getUsername());
        values.put("spring.datasource.password", properties.getMysql().getPassword());
        values.put("spring.flyway.enabled", true);
        values.put("spring.flyway.locations", "classpath:xxl-job/db/migration");
        values.put("spring.flyway.validate-migration-naming", true);
        values.put("mybatis.mapper-locations", "classpath*:/mapper/**/*.xml,classpath*:/mybatis/xxl-job/*.xml");
        values.put("xxl.job.accessToken", properties.getAccessToken());
        values.put("xxl.job.i18n", "zh_CN");
        values.put("xxl.job.logretentiondays", 30);
        values.put("xxl-sso.token.key", PlatformXxlSsoConfigurationTokenKey.VALUE);
        values.put("xxl-sso.token.timeout", 86_400_000L);
        values.put("xxl-sso.client.excluded.paths", "/static/**,/platform-sso/**,/error,/actuator/**");
        values.put("xxl-sso.client.login.path", "/platform-sso/required");
        values.put("management.health.mail.enabled", false);
        values.put("spring.jackson.time-zone", "Asia/Shanghai");
        values.put("spring.autoconfigure.exclude", String.join(",",
                "org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration",
                "org.springframework.boot.security.autoconfigure.web.servlet.ServletWebSecurityAutoConfiguration",
                "org.springframework.boot.security.autoconfigure.actuate.web.servlet.ManagementWebSecurityAutoConfiguration",
                "org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration"));
        return Map.copyOf(values);
    }

    /** 避免 launcher 反向依赖 child 配置的包级常量可见性。 */
    private static final class PlatformXxlSsoConfigurationTokenKey {
        private static final String VALUE = "test_agent_xxl_login";
    }
}
