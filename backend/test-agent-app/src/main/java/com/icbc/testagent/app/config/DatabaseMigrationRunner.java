package com.icbc.testagent.app.config;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(DatabaseMigrationRunner.class);

    private final DataSource dataSource;
    private final boolean enabled;
    private final String[] locations;

    /**
     * 注入迁移数据源和 Flyway 配置，locations 默认复用 persistence 模块 migration。
     */
    public DatabaseMigrationRunner(
            DataSource dataSource,
            @Value("${spring.flyway.enabled:true}") boolean enabled,
            @Value("${spring.flyway.locations:classpath:db/migration}") String[] locations) {
        this.dataSource = dataSource;
        this.enabled = enabled;
        this.locations = locations;
    }

    /**
     * 应用启动早期执行数据库迁移；禁用 Flyway 时直接跳过。
     */
    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            return;
        }
        // 迁移前先做一次轻量连通性探测，失败时打印清晰的数据库 IP:port，便于快速定位网络问题。
        String dbAddress = resolveDatabaseAddress();
        if (!isDatabaseReachable(dbAddress)) {
            LOGGER.error("数据库连接失败，请检查数据库是否可达: {}（host={}, port={}）",
                    dbAddress, extractHost(dbAddress), extractPort(dbAddress));
        }
        try {
            Flyway.configure()
                    .dataSource(dataSource)
                    .locations(locations)
                    .outOfOrder(true)
                    .load()
                    .migrate();
        } catch (RuntimeException migrationException) {
            // 迁移失败大多是数据库连接或权限问题，补充打印数据库地址，避免只看到晦涩的 JDBC 堆栈。
            LOGGER.error("数据库迁移失败，目标数据库: {}（host={}, port={}）",
                    dbAddress, extractHost(dbAddress), extractPort(dbAddress), migrationException);
            throw migrationException;
        }
    }

    /**
     * 从 JDBC URL 中解析出 host:port 形式的数据库地址。
     */
    private String resolveDatabaseAddress() {
        try (Connection connection = dataSource.getConnection()) {
            String url = connection.getMetaData().getURL();
            return url == null ? "unknown" : url;
        } catch (SQLException sqlException) {
            // 连接失败时无法拿到 URL 元数据，从异常信息中尽力提取 JDBC URL。
            String message = sqlException.getMessage();
            LOGGER.warn("解析数据库地址失败: {}", message);
            return message != null ? message : "unknown";
        }
    }

    /**
     * 用一个短超时连接探测数据库是否可达，避免在迁移阶段长时间挂起。
     */
    private boolean isDatabaseReachable(String dbAddress) {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2);
        } catch (SQLException sqlException) {
            LOGGER.warn("数据库连通性探测失败: {}（host={}, port={}）",
                    dbAddress, extractHost(dbAddress), extractPort(dbAddress), sqlException);
            return false;
        }
    }

    /**
     * 从 JDBC URL 或异常信息中粗略提取 host，提取失败时返回 unknown。
     */
    private String extractHost(String dbAddress) {
        if (dbAddress == null) {
            return "unknown";
        }
        // 匹配 jdbc:postgresql://host:port/db 中的 host 部分
        int idx = dbAddress.indexOf("//");
        if (idx < 0) {
            return "unknown";
        }
        String rest = dbAddress.substring(idx + 2);
        int colon = rest.indexOf(':');
        int slash = rest.indexOf('/');
        int end = colon > 0 ? colon : (slash > 0 ? slash : rest.length());
        return rest.substring(0, end);
    }

    /**
     * 从 JDBC URL 或异常信息中粗略提取 port，提取失败时返回 unknown。
     */
    private String extractPort(String dbAddress) {
        if (dbAddress == null) {
            return "unknown";
        }
        int idx = dbAddress.indexOf("//");
        if (idx < 0) {
            return "unknown";
        }
        String rest = dbAddress.substring(idx + 2);
        int colon = rest.indexOf(':');
        if (colon < 0) {
            return "unknown";
        }
        String afterColon = rest.substring(colon + 1);
        StringBuilder port = new StringBuilder();
        for (int i = 0; i < afterColon.length(); i++) {
            char ch = afterColon.charAt(i);
            if (Character.isDigit(ch)) {
                port.append(ch);
            } else {
                break;
            }
        }
        return port.length() > 0 ? port.toString() : "unknown";
    }
}
