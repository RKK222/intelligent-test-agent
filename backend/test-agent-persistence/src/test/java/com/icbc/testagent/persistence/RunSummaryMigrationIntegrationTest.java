package com.icbc.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.sql.SQLException;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 验证 Redis 摘要模式的关系型锚点字段、索引和历史消息兼容默认值。
 */
class RunSummaryMigrationIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");

    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbcClient;

    @BeforeEach
    void setUp() {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_run_summary_migration_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbcClient = JdbcClient.create(dataSource);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void migrationAddsRunAnchorAndSummaryColumns() {
        Map<String, String> runColumns = columnTypes("runs");
        assertThat(runColumns).containsKeys(
                "storage_mode",
                "status_version",
                "client_request_id",
                "producer_linux_server_id",
                "execution_node_id_snapshot",
                "opencode_process_id_snapshot",
                "root_remote_session_id",
                "dispatch_message_id",
                "assistant_summary_message_id",
                "terminal_source",
                "terminal_reason_code",
                "safe_error_message",
                "remote_stop_confirmed",
                "last_event_seq",
                "details_expires_at",
                "diff_proposed_count",
                "diff_accepted_count",
                "diff_rejected_count",
                "last_remote_message_id",
                "last_remote_part_id");

        assertThat(columnTypes("session_messages")).containsKeys(
                "content_kind", "summary_key", "summary_version", "summary_status");
    }

    @Test
    void legacyRowsReceiveCompatibleDefaultsAndNewIndexesExist() {
        seedWorkspaceAndSession();
        jdbcClient.sql("""
                        insert into runs(run_id, session_id, workspace_id, status, trace_id, created_at, updated_at)
                        values('run_legacy_summary_default', 'ses_summary_migration', 'wrk_summary_migration',
                               'SUCCEEDED', 'trace_summary_migration', :now, :now)
                        """)
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                        insert into session_messages(message_id, session_id, role, content, trace_id, created_at)
                        values('msg_legacy_summary_default', 'ses_summary_migration', 'USER',
                               'legacy content', 'trace_summary_migration', :now)
                        """)
                .param("now", NOW)
                .update();

        assertThat(jdbcClient.sql("""
                        select storage_mode, status_version, last_event_seq,
                               diff_proposed_count, diff_accepted_count, diff_rejected_count
                        from runs where run_id = 'run_legacy_summary_default'
                        """)
                .query((rs, rowNum) -> Map.of(
                        "storageMode", rs.getString("storage_mode"),
                        "statusVersion", rs.getLong("status_version"),
                        "lastEventSeq", rs.getLong("last_event_seq"),
                        "proposed", rs.getInt("diff_proposed_count"),
                        "accepted", rs.getInt("diff_accepted_count"),
                        "rejected", rs.getInt("diff_rejected_count")))
                .single()).containsEntry("storageMode", "LEGACY_FULL")
                .containsEntry("statusVersion", 0L)
                .containsEntry("lastEventSeq", 0L);
        assertThat(jdbcClient.sql("""
                        select content_kind from session_messages
                        where message_id = 'msg_legacy_summary_default'
                        """)
                .query(String.class)
                .single()).isEqualTo("RAW_LEGACY");

        assertThat(indexNames("runs")).contains(
                "uk_runs_session_client_request", "idx_runs_summary_server_status");
        assertThat(indexNames("session_messages")).contains("uk_session_messages_summary_key");
    }

    private Map<String, String> columnTypes(String tableName) {
        try (var connection = dataSource.getConnection();
                var columns = connection.getMetaData().getColumns(null, null, tableName, null)) {
            var result = new java.util.LinkedHashMap<String, String>();
            while (columns.next()) {
                result.put(columns.getString("COLUMN_NAME").toLowerCase(), columns.getString("TYPE_NAME"));
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("读取列元数据失败: " + tableName, exception);
        }
    }

    private java.util.Set<String> indexNames(String tableName) {
        try (var connection = dataSource.getConnection();
                var indexes = connection.getMetaData().getIndexInfo(null, null, tableName, false, false)) {
            var result = new java.util.LinkedHashSet<String>();
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                if (indexName != null) {
                    result.add(indexName.toLowerCase());
                }
            }
            return result;
        } catch (SQLException exception) {
            throw new IllegalStateException("读取索引元数据失败: " + tableName, exception);
        }
    }

    private void seedWorkspaceAndSession() {
        jdbcClient.sql("""
                        insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                        values('wrk_summary_migration', 'summary workspace', '/tmp/summary', 'ACTIVE',
                               'trace_summary_migration', :now, :now)
                        """)
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                        insert into sessions(session_id, workspace_id, title, status, trace_id, created_at, updated_at)
                        values('ses_summary_migration', 'wrk_summary_migration', 'summary session', 'ACTIVE',
                               'trace_summary_migration', :now, :now)
                        """)
                .param("now", NOW)
                .update();
    }
}
