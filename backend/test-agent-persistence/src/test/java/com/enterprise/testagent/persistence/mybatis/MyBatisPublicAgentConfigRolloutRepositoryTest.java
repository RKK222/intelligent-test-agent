package com.enterprise.testagent.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Reader;
import java.time.Instant;
import java.util.List;
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

        assertThat(syncRow.getResultMappings())
                .filteredOn(mapping -> "retry_count".equals(mapping.getColumn()))
                .singleElement()
                .extracting(mapping -> mapping.getJavaType())
                .isEqualTo(int.class);
        assertThat(targetRow.getResultMappings())
                .filteredOn(mapping -> List.of("port", "retry_count").contains(mapping.getColumn()))
                .allSatisfy(mapping -> assertThat(mapping.getJavaType()).isEqualTo(int.class));
    }

    @Test
    void claimAssignsUniqueLeaseTokenAndCarriesUserAndTrace() {
        PublicAgentConfigRolloutMapper mapper = mock(PublicAgentConfigRolloutMapper.class);
        MyBatisPublicAgentConfigRolloutRepository repository = new MyBatisPublicAgentConfigRolloutRepository(mapper);
        PublicAgentConfigRolloutTargetRow row = new PublicAgentConfigRolloutTargetRow(
                "act_target",
                "acr_rollout",
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
        assertThat(targets.get(0).traceId()).isEqualTo("trace_rollout");
        assertThat(targets.get(0).leaseToken()).startsWith("acl_");
        assertThat(targets.get(0).processPid()).isEqualTo(123L);
    }

    @Test
    void serverSyncClaimUsesDatabaseLeaseToken() {
        PublicAgentConfigRolloutMapper mapper = mock(PublicAgentConfigRolloutMapper.class);
        MyBatisPublicAgentConfigRolloutRepository repository = new MyBatisPublicAgentConfigRolloutRepository(mapper);
        PublicAgentConfigRolloutSyncRow row = new PublicAgentConfigRolloutSyncRow(
                "acr_rollout", "main", "abc123", "usr-admin", "trace-rollout",
                2, null, null);
        when(mapper.findClaimableServerSyncs("linux-1", NOW, 1)).thenReturn(List.of(row));
        when(mapper.markServerSyncProcessing(eq("acr_rollout"), eq("linux-1"), any(), any(), eq(NOW)))
                .thenReturn(1);

        var claim = repository.claimPendingSync("linux-1", NOW, NOW.plusSeconds(180));

        assertThat(claim).isPresent();
        assertThat(claim.get().retryCount()).isEqualTo(2);
        assertThat(claim.get().leaseToken()).startsWith("acl_");
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
}
