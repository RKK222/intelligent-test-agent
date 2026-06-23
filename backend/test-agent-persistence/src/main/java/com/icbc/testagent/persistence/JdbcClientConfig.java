package com.icbc.testagent.persistence;

import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * JdbcClient 的 Spring 配置。
 *
 * <p>Spring Boot 4.x 的 {@code spring-boot-starter-data-jdbc} 不自动引入
 * {@code spring-boot-autoconfigure}，导致 {@code JdbcClientAutoConfiguration}
 * 不会运行。显式在此创建 JdbcClient bean，保证所有 Jdbc*Repository 实现可以正常注入。
 */
@Configuration
public class JdbcClientConfig {

    /**
     * 从已有的 DataSource 创建 JdbcClient bean。
     * 如果 JdbcClientAutoConfiguration 已创建（本模块之外），则跳过。
     */
    @Bean
    @ConditionalOnMissingBean(JdbcClient.class)
    public JdbcClient jdbcClient(DataSource dataSource) {
        return JdbcClient.create(dataSource);
    }
}
