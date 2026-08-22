package com.enterprise.testagent.persistence.clickhouse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.HexFormat;
import javax.sql.DataSource;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/**
 * 执行 ClickHouse 版本化迁移并锁定 SHA-256。
 *
 * <p>ClickHouse 不支持事务 DDL；所有语句必须可幂等重放，checksum 不一致则拒绝启动。
 */
public final class ClickHouseSchemaMigrator implements InitializingBean {

    static final String VERSION = "20260813150000";
    static final String DESCRIPTION = "analytics_activity_facts_create_tables";
    static final String SCRIPT = "db/clickhouse/V20260813150000__analytics_activity_facts_create_tables.sql";
    private static final java.util.List<Migration> MIGRATIONS = java.util.List.of(
            new Migration(VERSION, DESCRIPTION, SCRIPT),
            new Migration(
                    "20260822174420",
                    "analytics_trace_catalog_create_tables",
                    "db/clickhouse/V20260822174420__analytics_trace_catalog_create_tables.sql"),
            new Migration(
                    "20260822215123",
                    "analytics_trace_spans_add_dsh_metrics",
                    "db/clickhouse/V20260822215123__analytics_trace_spans_add_dsh_metrics.sql"),
            new Migration(
                    "20260823000346",
                    "analytics_capability_facts_enforce_plugin_cutover",
                    "db/clickhouse/V20260823000346__analytics_capability_facts_enforce_plugin_cutover.sql"),
            new Migration(
                    "20260823001128",
                    "analytics_trace_spans_add_cost_decode_tokens",
                    "db/clickhouse/V20260823001128__analytics_trace_spans_add_cost_decode_tokens.sql"));

    private final DataSource dataSource;

    public ClickHouseSchemaMigrator(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        migrate();
    }

    public void migrate() throws SQLException, IOException {
        try (Connection connection = dataSource.getConnection()) {
            createHistoryTable(connection);
            for (Migration migration : MIGRATIONS) {
                ClassPathResource resource = new ClassPathResource(migration.script());
                String checksum = sha256(resource.getContentAsByteArray());
                String appliedChecksum = appliedChecksum(connection, migration.version());
                if (appliedChecksum != null) {
                    if (!checksum.equals(appliedChecksum)) {
                        throw new IllegalStateException("ClickHouse migration checksum 不一致: " + migration.version());
                    }
                    continue;
                }
                ScriptUtils.executeSqlScript(connection, resource);
                recordSuccess(connection, migration, checksum);
            }
        }
    }

    private void createHistoryTable(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                    create table if not exists analytics_schema_history (
                        version String, description String, script String, checksum String,
                        installed_on DateTime64(3, 'UTC'), success UInt8
                    ) engine = ReplacingMergeTree(installed_on) order by (version)
                    """);
        }
    }

    private String appliedChecksum(Connection connection, String version) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                select checksum from analytics_schema_history final
                where version = ? and success = 1 limit 1
                """)) {
            statement.setString(1, version);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        }
    }

    private void recordSuccess(Connection connection, Migration migration, String checksum) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                insert into analytics_schema_history(
                    version, description, script, checksum, installed_on, success
                ) values (?, ?, ?, ?, ?, 1)
                """)) {
            statement.setString(1, migration.version());
            statement.setString(2, migration.description());
            statement.setString(3, migration.script());
            statement.setString(4, checksum);
            statement.setObject(5, Instant.now());
            statement.executeUpdate();
        }
    }

    private String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JRE 不支持 SHA-256", exception);
        }
    }

    private record Migration(String version, String description, String script) {
    }
}
