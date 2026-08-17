package com.enterprise.testagent.xxljob.admin;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.flywaydb.core.api.migration.JavaMigration;
import org.flywaydb.core.api.resource.LoadableResource;
import org.flywaydb.core.internal.scanner.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;

/** 按已执行的 XXL V12 checksum 选择 dev/release 原始迁移字节；仅由 Admin 子上下文显式导入。 */
public final class XxlJobMigrationCompatibilityCustomizer implements FlywayConfigurationCustomizer {

    public static final int ANALYTICS_V12_CHECKSUM = -1848714734;
    public static final int SCM_GIT_NAME_SYNC_V12_CHECKSUM = -211900485;
    static final String ANALYTICS_V12_COMPATIBILITY_LOCATION =
            "classpath:xxl-job/db/migration-compat/analytics-v12-applied";
    static final String SCM_GIT_NAME_SYNC_V12_MAIN_RESOURCE =
            "xxl-job/db/migration/V12__register_scm_git_name_sync_task.sql";

    private static final Logger LOGGER =
            LoggerFactory.getLogger(XxlJobMigrationCompatibilityCustomizer.class);

    @Override
    public void customize(FluentConfiguration configuration) {
        Integer appliedV12Checksum = appliedV12Checksum(configuration);
        if (appliedV12Checksum == null
                || appliedV12Checksum == SCM_GIT_NAME_SYNC_V12_CHECKSUM) {
            return;
        }
        if (appliedV12Checksum != ANALYTICS_V12_CHECKSUM) {
            throw new IllegalStateException(
                    "检测到未知的 XXL V12 migration checksum，拒绝自动兼容；请核对 flyway_schema_history");
        }

        List<String> locations = new ArrayList<>(Arrays.stream(configuration.getLocations())
                .map(location -> location.getDescriptor())
                .toList());
        if (!locations.contains(ANALYTICS_V12_COMPATIBILITY_LOCATION)) {
            locations.add(ANALYTICS_V12_COMPATIBILITY_LOCATION);
        }
        configuration.locations(locations.toArray(String[]::new));

        // Flyway 无法用公开配置排除单个同版本 SQL，因此只隐藏与已知 analytics 历史不匹配的主链副本。
        ResourceProvider defaultProvider = new Scanner<>(
                JavaMigration.class, configuration, configuration.getLocations());
        configuration.resourceProvider(new MigrationFilteringResourceProvider(
                defaultProvider, SCM_GIT_NAME_SYNC_V12_MAIN_RESOURCE));
        LOGGER.warn(
                "检测到已执行的 XXL analytics V12 migration，启用原始字节兼容路径并继续执行 V13 前向迁移: checksum={}",
                appliedV12Checksum);
    }

    /** 在 Flyway validate 前直接读取唯一成功的 V12 历史，空库则返回 null。 */
    private Integer appliedV12Checksum(FluentConfiguration configuration) {
        String historyTable = configuration.getTable();
        if (!historyTable.matches("[A-Za-z0-9_]+")) {
            throw new IllegalStateException("XXL Flyway history 表名不合法");
        }
        try (Connection connection = configuration.getDataSource().getConnection()) {
            if (!tableExists(connection, historyTable)) {
                return null;
            }
            String sql = "SELECT checksum FROM `" + historyTable
                    + "` WHERE version = '12' AND success = 1 ORDER BY installed_rank";
            try (PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet result = statement.executeQuery()) {
                Integer checksum = null;
                int count = 0;
                while (result.next()) {
                    count++;
                    int currentChecksum = result.getInt(1);
                    if (result.wasNull()) {
                        throw new IllegalStateException("XXL V12 migration checksum 为空，拒绝自动兼容");
                    }
                    checksum = currentChecksum;
                }
                if (count > 1) {
                    throw new IllegalStateException("XXL V12 migration 存在多条成功历史，拒绝自动兼容");
                }
                return checksum;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("读取 XXL Flyway V12 迁移历史失败", exception);
        }
    }

    private boolean tableExists(Connection connection, String historyTable) throws SQLException {
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, "%", null)) {
            while (tables.next()) {
                if (historyTable.equalsIgnoreCase(tables.getString("TABLE_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 仅过滤已形成另一套持久化历史的主目录 V12。 */
    private static final class MigrationFilteringResourceProvider implements ResourceProvider {

        private final ResourceProvider delegate;
        private final String filteredMainResource;

        private MigrationFilteringResourceProvider(
                ResourceProvider delegate,
                String filteredMainResource) {
            this.delegate = delegate;
            this.filteredMainResource = filteredMainResource;
        }

        @Override
        public LoadableResource getResource(String name) {
            LoadableResource resource = delegate.getResource(name);
            return resource != null && isFilteredMainMigration(resource) ? null : resource;
        }

        @Override
        public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
            return delegate.getResources(prefix, suffixes).stream()
                    .filter(resource -> !isFilteredMainMigration(resource))
                    .toList();
        }

        private boolean isFilteredMainMigration(LoadableResource resource) {
            String relativePath = normalize(resource.getRelativePath());
            String absolutePath = normalize(resource.getAbsolutePath());
            String absolutePathOnDisk = normalize(resource.getAbsolutePathOnDisk());
            return filteredMainResource.equals(relativePath)
                    || absolutePath.endsWith("/" + filteredMainResource)
                    || absolutePath.contains("!/" + filteredMainResource)
                    || absolutePathOnDisk.endsWith("/" + filteredMainResource)
                    || absolutePathOnDisk.contains("!/" + filteredMainResource);
        }

        private static String normalize(String path) {
            return path == null ? "" : path.replace('\\', '/');
        }
    }
}
