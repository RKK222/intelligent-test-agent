package com.enterprise.testagent.app.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.scanner.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.stereotype.Component;

/**
 * 在 Spring Boot 创建唯一 Flyway Bean 时解析历史迁移分叉。
 *
 * <p>工具盒子迁移形成过三个已部署历史：早期测试库执行了旧版本，企业库执行了当前版本的
 * 原始字节，少量环境执行了被误改成幂等 SQL 的当前版本。兼容脚本只能按已应用历史选择，
 * 未知 checksum 必须继续由 Flyway 拒绝启动。
 */
@Component
public final class DatabaseMigrationCompatibilityCustomizer implements FlywayConfigurationCustomizer {

    static final String LEGACY_TOOLBOX_MIGRATION_VERSION = "20260727203500";
    static final String LEGACY_TOOLBOX_MIGRATION_LOCATION =
            "classpath:db/migration-compat/toolbox";
    static final String CURRENT_TOOLBOX_MIGRATION_VERSION = "20260728160800";
    static final int CURRENT_TOOLBOX_ENTERPRISE_CHECKSUM = -1966404877;
    static final int CURRENT_TOOLBOX_IDEMPOTENT_CHECKSUM = -74327385;
    static final String CURRENT_TOOLBOX_IDEMPOTENT_LOCATION =
            "classpath:db/migration-compat/toolbox-current-idempotent";

    private static final String CURRENT_TOOLBOX_MIGRATION_FILE =
            "V20260728160800__create_toolbox_click_tracking.sql";
    private static final String CURRENT_TOOLBOX_MAIN_RESOURCE =
            "db/migration/" + CURRENT_TOOLBOX_MIGRATION_FILE;

    private static final Logger LOGGER =
            LoggerFactory.getLogger(DatabaseMigrationCompatibilityCustomizer.class);

    /**
     * 在 Flyway validate/migrate 之前按已应用版本追加隔离兼容路径。
     */
    @Override
    public void customize(FluentConfiguration configuration) {
        MigrationInfo[] appliedMigrations = appliedMigrations(configuration);
        boolean legacyMigrationApplied = isMigrationApplied(
                appliedMigrations, LEGACY_TOOLBOX_MIGRATION_VERSION);
        Integer currentMigrationChecksum = appliedChecksum(
                appliedMigrations, CURRENT_TOOLBOX_MIGRATION_VERSION);

        List<String> locations = new ArrayList<>(Arrays.stream(configuration.getLocations())
                .map(location -> location.getDescriptor())
                .toList());
        if (legacyMigrationApplied) {
            addLocationIfAbsent(locations, LEGACY_TOOLBOX_MIGRATION_LOCATION);
        }
        boolean idempotentCurrentMigrationApplied = Objects.equals(
                currentMigrationChecksum, CURRENT_TOOLBOX_IDEMPOTENT_CHECKSUM);
        if (idempotentCurrentMigrationApplied) {
            addLocationIfAbsent(locations, CURRENT_TOOLBOX_IDEMPOTENT_LOCATION);
        }
        configuration.locations(locations.toArray(String[]::new));

        boolean legacyWithoutCurrentMigration = legacyMigrationApplied
                && currentMigrationChecksum == null;
        if (legacyWithoutCurrentMigration || idempotentCurrentMigrationApplied) {
            // Flyway 没有公开“排除单个 classpath migration”的配置入口；这里包装唯一默认扫描器，
            // 只隐藏 checksum 与当前历史不匹配的主目录副本，其余 SQL 和 Java migration 保持原样。
            ResourceProvider defaultProvider = new Scanner<>(
                    JavaMigration.class, configuration, configuration.getLocations());
            configuration.resourceProvider(
                    new CurrentToolboxMigrationFilteringResourceProvider(defaultProvider));
        }

        if (legacyMigrationApplied) {
            LOGGER.info("检测到已执行的旧工具盒子迁移，启用兼容解析路径: version={}",
                    LEGACY_TOOLBOX_MIGRATION_VERSION);
        }
        if (idempotentCurrentMigrationApplied) {
            LOGGER.warn("检测到已执行的工具盒子幂等迁移变体，按原 checksum 启用隔离兼容路径: version={}, checksum={}",
                    CURRENT_TOOLBOX_MIGRATION_VERSION, currentMigrationChecksum);
        }
    }

    /**
     * 复用 Boot 已组装的数据源、schema、history 表和主 migration 配置读取历史。
     */
    private MigrationInfo[] appliedMigrations(FluentConfiguration configuration) {
        return Flyway.configure(configuration.getClassLoader())
                .configuration(configuration)
                .load()
                .info()
                .applied();
    }

    private boolean isMigrationApplied(MigrationInfo[] appliedMigrations, String version) {
        return Arrays.stream(appliedMigrations)
                .map(MigrationInfo::getVersion)
                .filter(appliedVersion -> appliedVersion != null)
                .anyMatch(appliedVersion -> version.equals(appliedVersion.getVersion()));
    }

    private Integer appliedChecksum(MigrationInfo[] appliedMigrations, String version) {
        return Arrays.stream(appliedMigrations)
                .filter(migration -> migration.getVersion() != null)
                .filter(migration -> version.equals(migration.getVersion().getVersion()))
                .map(MigrationInfo::getAppliedChecksum)
                .findFirst()
                .orElse(null);
    }

    private void addLocationIfAbsent(List<String> locations, String location) {
        if (!locations.contains(location)) {
            locations.add(location);
        }
    }

    /** 过滤主目录中的当前迁移，保留按数据库历史选中的隔离副本。 */
    private static final class CurrentToolboxMigrationFilteringResourceProvider
            implements ResourceProvider {

        private final ResourceProvider delegate;

        private CurrentToolboxMigrationFilteringResourceProvider(ResourceProvider delegate) {
            this.delegate = delegate;
        }

        @Override
        public LoadableResource getResource(String name) {
            LoadableResource resource = delegate.getResource(name);
            return resource != null && isMainCurrentToolboxMigration(resource) ? null : resource;
        }

        @Override
        public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
            return delegate.getResources(prefix, suffixes).stream()
                    .filter(resource -> !isMainCurrentToolboxMigration(resource))
                    .toList();
        }

        private static boolean isMainCurrentToolboxMigration(LoadableResource resource) {
            String relativePath = normalize(resource.getRelativePath());
            String absolutePath = normalize(resource.getAbsolutePath());
            String absolutePathOnDisk = normalize(resource.getAbsolutePathOnDisk());
            return CURRENT_TOOLBOX_MAIN_RESOURCE.equals(relativePath)
                    || absolutePath.endsWith("/" + CURRENT_TOOLBOX_MAIN_RESOURCE)
                    || absolutePath.contains("!/" + CURRENT_TOOLBOX_MAIN_RESOURCE)
                    || absolutePathOnDisk.endsWith("/" + CURRENT_TOOLBOX_MAIN_RESOURCE)
                    || absolutePathOnDisk.contains("!/" + CURRENT_TOOLBOX_MAIN_RESOURCE);
        }

        private static String normalize(String path) {
            return path == null ? "" : path.replace('\\', '/');
        }
    }

}
