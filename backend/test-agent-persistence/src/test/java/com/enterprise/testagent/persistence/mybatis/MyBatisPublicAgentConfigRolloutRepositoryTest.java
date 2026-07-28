package com.enterprise.testagent.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.configuration.AgentConfigRolloutScope;
import java.io.Reader;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 公共配置 rollout 仓储的租约 fencing 与用户快照映射测试。 */
class MyBatisPublicAgentConfigRolloutRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-07-17T12:00:00Z");

    @Test
    void constructorMappingsKeepPrimitiveIntegerTypes() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mybatis/PublicAgentConfigRolloutMapper.xml";
        try (Reader reader = Resources.getResourceAsReader(resource)) {
            new XMLMapperBuilder(reader, configuration, resource, configuration.getSqlFragments()).parse();
        }

        ResultMap syncRow = configuration.getResultMap(
                "com.enterprise.testagent.persistence.mybatis.PublicAgentConfigRolloutMapper.SyncRowMap");
        ResultMap targetRow = configuration.getResultMap(
                "com.enterprise.testagent.persistence.mybatis.PublicAgentConfigRolloutMapper.TargetRowMap");
        ResultMap worktreeRow = configuration.getResultMap(
                "com.enterprise.testagent.persistence.mybatis.PublicAgentConfigRolloutMapper.WorktreeRowMap");
        ResultMap publicWorktreeRow = configuration.getResultMap(
                "com.enterprise.testagent.persistence.mybatis.PublicAgentConfigRolloutMapper.PublicWorktreeRowMap");
        ResultMap serverStatusRow = configuration.getResultMap(
                "com.enterprise.testagent.persistence.mybatis.PublicAgentConfigRolloutMapper.RolloutServerStatusRowMap");

        assertThat(syncRow.getResultMappings())
                .filteredOn(mapping -> "retry_count".equals(mapping.getColumn()))
                .singleElement()
                .extracting(mapping -> mapping.getJavaType())
                .isEqualTo(int.class);
        assertThat(targetRow.getResultMappings())
                .filteredOn(mapping -> List.of("port", "retry_count").contains(mapping.getColumn()))
                .allSatisfy(mapping -> assertThat(mapping.getJavaType()).isEqualTo(int.class));
        assertThat(worktreeRow.getResultMappings())
                .filteredOn(mapping -> "retry_count".equals(mapping.getColumn()))
                .singleElement()
                .extracting(mapping -> mapping.getJavaType())
                .isEqualTo(int.class);
        assertThat(publicWorktreeRow.getResultMappings())
                .filteredOn(mapping -> "retry_count".equals(mapping.getColumn()))
                .singleElement()
                .extracting(mapping -> mapping.getJavaType())
                .isEqualTo(int.class);
        assertThat(serverStatusRow.getResultMappings())
                .filteredOn(mapping -> "retry_count".equals(mapping.getColumn()))
                .singleElement()
                .extracting(mapping -> mapping.getJavaType())
                .isEqualTo(int.class);
        assertThat(serverStatusRow.getResultMappings())
                .filteredOn(mapping -> mapping.getColumn().startsWith("target_"))
                .allSatisfy(mapping -> assertThat(mapping.getJavaType()).isEqualTo(long.class));
    }

    @Test
    void blockingRolloutQueryUsesPersonalWorkspaceVersionForeignKey() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mybatis/PublicAgentConfigRolloutMapper.xml";
        try (Reader reader = Resources.getResourceAsReader(resource)) {
            new XMLMapperBuilder(reader, configuration, resource, configuration.getSqlFragments()).parse();
        }

        // 个人工作空间通过 app_workspace_version_id 关联应用版本，禁止误用不存在的 version_id 列。
        String sql = configuration
                .getMappedStatement(
                        "com.enterprise.testagent.persistence.mybatis.PublicAgentConfigRolloutMapper.findBlockingRolloutId")
                .getBoundSql(Map.of("userId", "usr-1"))
                .getSql()
                .replaceAll("\\s+", " ")
                .trim();

        assertThat(sql).contains("pw.app_workspace_version_id = v.version_id");
        assertThat(sql).doesNotContain("pw.version_id");
        assertThat(sql).contains("r.config_scope = 'PERSONAL_APPLICATION'");
        assertThat(sql).contains("r.initiated_by_user_id = ?");
    }

    @Test
    void claimAssignsUniqueLeaseTokenAndCarriesUserAndTrace() {
        PublicAgentConfigRolloutMapper mapper = mock(PublicAgentConfigRolloutMapper.class);
        MyBatisPublicAgentConfigRolloutRepository repository = new MyBatisPublicAgentConfigRolloutRepository(mapper);
        PublicAgentConfigRolloutTargetRow row = new PublicAgentConfigRolloutTargetRow(
                "act_target",
                "acr_rollout",
                "PUBLIC",
                "usr_1",
                "linux-1",
                "container-1",
                4096,
                123L,
                NOW.minusSeconds(30),
                "http://127.0.0.1:4096",
                2,
                null,
                null,
                "trace_rollout");
        when(mapper.findClaimableTargets("linux-1", NOW, 1)).thenReturn(List.of(row));
        when(mapper.markTargetProcessing(eq("act_target"), any(), any(), eq(NOW))).thenReturn(1);

        var targets = repository.claimTargets("linux-1", NOW, NOW.plusSeconds(60), 1);

        assertThat(targets).hasSize(1);
        assertThat(targets.get(0).userId()).isEqualTo("usr_1");
        assertThat(targets.get(0).configScope()).isEqualTo(AgentConfigRolloutScope.PUBLIC);
        assertThat(targets.get(0).traceId()).isEqualTo("trace_rollout");
        assertThat(targets.get(0).leaseToken()).startsWith("acl_");
        assertThat(targets.get(0).processPid()).isEqualTo(123L);
    }

    @Test
    void serverSyncClaimUsesDatabaseLeaseToken() {
        PublicAgentConfigRolloutMapper mapper = mock(PublicAgentConfigRolloutMapper.class);
        MyBatisPublicAgentConfigRolloutRepository repository = new MyBatisPublicAgentConfigRolloutRepository(mapper);
        PublicAgentConfigRolloutSyncRow row = new PublicAgentConfigRolloutSyncRow(
                "acr_rollout", "APPLICATION", "app-1", "main", "abc123", false, "usr-admin", "trace-rollout",
                2, null, null);
        when(mapper.findClaimableServerSyncs("linux-1", "APPLICATION", NOW, 1)).thenReturn(List.of(row));
        when(mapper.markServerSyncProcessing(eq("acr_rollout"), eq("linux-1"), any(), any(), eq(NOW)))
                .thenReturn(1);

        var claim = repository.claimPendingSync(
                "linux-1", AgentConfigRolloutScope.APPLICATION, NOW, NOW.plusSeconds(180));

        assertThat(claim).isPresent();
        assertThat(claim.get().scope()).isEqualTo(AgentConfigRolloutScope.APPLICATION);
        assertThat(claim.get().scopeKey()).isEqualTo("app-1");
        assertThat(claim.get().retryCount()).isEqualTo(2);
        assertThat(claim.get().leaseToken()).startsWith("acl_");
    }

    @Test
    void latestPublicStatusMapsServerSyncDrainAndLastError() {
        PublicAgentConfigRolloutMapper mapper = mock(PublicAgentConfigRolloutMapper.class);
        MyBatisPublicAgentConfigRolloutRepository repository = new MyBatisPublicAgentConfigRolloutRepository(mapper);
        when(mapper.findLatestRolloutStatus("PUBLIC", null)).thenReturn(new PublicAgentConfigRolloutStatusRow(
                "acr_rollout",
                "DRAINING",
                "main",
                "abc123",
                null,
                NOW,
                NOW.plusSeconds(5),
                null));
        when(mapper.findRolloutServerStatuses("acr_rollout")).thenReturn(List.of(
                new PublicAgentConfigRolloutServerStatusRow(
                        "linux-1",
                        "RETRY_WAIT",
                        2,
                        3,
                        1,
                        2,
                        0,
                        4,
                        1,
                        3,
                        "公共 Agent 运行副本存在未提交变更",
                        null,
                        NOW.plusSeconds(5))));

        var status = repository.findLatestRolloutStatus(AgentConfigRolloutScope.PUBLIC, null).orElseThrow();
        var servers = repository.findRolloutServerStatuses(status.rolloutId());

        assertThat(status.active()).isTrue();
        assertThat(servers).singleElement().satisfies(server -> {
            assertThat(server.linuxServerId()).isEqualTo("linux-1");
            assertThat(server.targetPending()).isEqualTo(1);
            assertThat(server.worktreePending()).isEqualTo(1);
            assertThat(server.worktreeSynced()).isEqualTo(3);
            assertThat(server.lastError()).contains("未提交变更");
        });
    }

    @Test
    void publicWorktreeClaimCarriesExactWorktreeAndUsesLeaseFencing() {
        PublicAgentConfigRolloutMapper mapper = mock(PublicAgentConfigRolloutMapper.class);
        MyBatisPublicAgentConfigRolloutRepository repository = new MyBatisPublicAgentConfigRolloutRepository(mapper);
        PublicAgentConfigWorktreeRow row = new PublicAgentConfigWorktreeRow(
                "acr_rollout",
                "agw_public",
                "usr_1",
                "linux-1",
                "abc123",
                "trace-rollout",
                3,
                null,
                null);
        when(mapper.findClaimablePublicWorktrees("linux-1", NOW, 1)).thenReturn(List.of(row));
        when(mapper.markPublicWorktreeProcessing(
                eq("acr_rollout"), eq("agw_public"), any(), any(), eq(NOW)))
                .thenReturn(1);

        var claim = repository.claimPendingPublicWorktree("linux-1", NOW, NOW.plusSeconds(180));

        assertThat(claim).isPresent();
        assertThat(claim.orElseThrow()).satisfies(value -> {
            assertThat(value.worktreeId()).isEqualTo("agw_public");
            assertThat(value.userId()).isEqualTo("usr_1");
            assertThat(value.targetCommit()).isEqualTo("abc123");
            assertThat(value.retryCount()).isEqualTo(3);
            assertThat(value.leaseToken()).startsWith("acl_");
        });
    }

    @Test
    void terminalUpdatesAreFencedByTheSameLeaseToken() {
        PublicAgentConfigRolloutMapper mapper = mock(PublicAgentConfigRolloutMapper.class);
        MyBatisPublicAgentConfigRolloutRepository repository = new MyBatisPublicAgentConfigRolloutRepository(mapper);
        when(mapper.markTargetDisposed("act_target", "acl_current", NOW)).thenReturn(0);

        assertThat(repository.markTargetDisposed("act_target", "acl_current", NOW)).isFalse();

        ArgumentCaptor<String> token = ArgumentCaptor.forClass(String.class);
        verify(mapper).markTargetDisposed(eq("act_target"), token.capture(), eq(NOW));
        assertThat(token.getValue()).isEqualTo("acl_current");
    }

    @Test
    void applicationWorktreeClaimCarriesExactWorkspaceAndTargetCommit() {
        PublicAgentConfigRolloutMapper mapper = mock(PublicAgentConfigRolloutMapper.class);
        MyBatisPublicAgentConfigRolloutRepository repository = new MyBatisPublicAgentConfigRolloutRepository(mapper);
        AgentConfigRolloutWorktreeRow row = new AgentConfigRolloutWorktreeRow(
                "acr_rollout",
                "awv_version",
                "pws_personal",
                "usr_1",
                "linux-1",
                "abc123",
                "trace-rollout",
                7,
                null,
                null);
        when(mapper.findClaimableApplicationWorktrees("linux-1", NOW, 1)).thenReturn(List.of(row));
        when(mapper.markApplicationWorktreeProcessing(
                eq("acr_rollout"), eq("pws_personal"), any(), any(), eq(NOW)))
                .thenReturn(1);

        var claim = repository.claimPendingApplicationWorktree("linux-1", NOW, NOW.plusSeconds(180));

        assertThat(claim).isPresent();
        assertThat(claim.get().personalWorkspaceId()).isEqualTo("pws_personal");
        assertThat(claim.get().targetCommit()).isEqualTo("abc123");
        assertThat(claim.get().retryCount()).isEqualTo(7);
        assertThat(claim.get().leaseToken()).startsWith("acl_");
    }
}
