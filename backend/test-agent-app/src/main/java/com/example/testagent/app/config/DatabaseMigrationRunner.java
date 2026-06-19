package com.example.testagent.app.config;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 运行态数据库迁移入口，必须先于执行节点 seed 执行，避免空库启动时读取不存在的表。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DatabaseMigrationRunner implements ApplicationRunner {

    private final DataSource dataSource;
    private final boolean enabled;
    private final String[] locations;

    public DatabaseMigrationRunner(
            DataSource dataSource,
            @Value("${spring.flyway.enabled:true}") boolean enabled,
            @Value("${spring.flyway.locations:classpath:db/migration}") String[] locations) {
        this.dataSource = dataSource;
        this.enabled = enabled;
        this.locations = locations;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        Flyway.configure()
                .dataSource(dataSource)
                .locations(locations)
                .load()
                .migrate();
    }
}
