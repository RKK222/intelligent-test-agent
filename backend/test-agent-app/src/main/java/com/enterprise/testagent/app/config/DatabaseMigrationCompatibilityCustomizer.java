package com.enterprise.testagent.app.config;

import java.util.Arrays;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.stereotype.Component;

/**
 * 在 Spring Boot 创建唯一 Flyway Bean 时解析历史迁移分叉。
 *
 * <p>旧测试库已经执行过工具盒子 V20260727203500；企业顺序基线没有执行该版本。
 * 兼容脚本只能对前一类数据库可见，否则会在正常基线上形成低版本待执行 migration。
 */
@Component
public final class DatabaseMigrationCompatibilityCustomizer implements FlywayConfigurationCustomizer {

    static final String LEGACY_TOOLBOX_MIGRATION_VERSION = "20260727203500";
    static final String LEGACY_TOOLBOX_MIGRATION_LOCATION =
            "classpath:db/migration-compat/toolbox";

    private static final Logger LOGGER =
            LoggerFactory.getLogger(DatabaseMigrationCompatibilityCustomizer.class);

    /**
     * 在 Flyway validate/migrate 之前按已应用版本追加隔离兼容路径。
     */
    @Override
    public void customize(FluentConfiguration configuration) {
        String[] locations = Arrays.stream(configuration.getLocations())
                .map(location -> location.getDescriptor())
                .toArray(String[]::new);
        if (Arrays.asList(locations).contains(LEGACY_TOOLBOX_MIGRATION_LOCATION)
                || !isMigrationApplied(configuration, LEGACY_TOOLBOX_MIGRATION_VERSION)) {
            return;
        }

        String[] resolved = Arrays.copyOf(locations, locations.length + 1);
        resolved[locations.length] = LEGACY_TOOLBOX_MIGRATION_LOCATION;
        configuration.locations(resolved);
        LOGGER.info("检测到已执行的历史数据库迁移，启用兼容解析路径: version={}",
                LEGACY_TOOLBOX_MIGRATION_VERSION);
    }

    /**
     * 复用 Boot 已组装的数据源、schema、history 表和主 migration 配置读取历史。
     */
    private boolean isMigrationApplied(FluentConfiguration configuration, String version) {
        MigrationInfo[] applied = Flyway.configure(configuration.getClassLoader())
                .configuration(configuration)
                .load()
                .info()
                .applied();
        return Arrays.stream(applied)
                .map(MigrationInfo::getVersion)
                .filter(appliedVersion -> appliedVersion != null)
                .anyMatch(appliedVersion -> version.equals(appliedVersion.getVersion()));
    }

}
