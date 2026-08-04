package com.enterprise.testagent.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.Reader;
import java.time.Instant;
import java.util.Map;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** 搬迁仓储的 XML 映射、错配条件和目标事务更新顺序测试。 */
class MyBatisPersonalWorkspaceRelocationRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-08-04T04:00:00Z");

    @Test
    void mapperUsesActiveBindingRunGuardAndMyBatisXmlMutations() throws Exception {
        Configuration configuration = new Configuration();
        String resource = "mybatis/PersonalWorkspaceRelocationMapper.xml";
        try (Reader reader = Resources.getResourceAsReader(resource)) {
            new XMLMapperBuilder(reader, configuration, resource, configuration.getSqlFragments()).parse();
        }
        String mismatchSql = configuration.getMappedStatement(
                        "com.enterprise.testagent.persistence.mybatis.PersonalWorkspaceRelocationMapper.findMismatches")
                .getBoundSql(Map.of("sourceLinuxServerId", "server-a", "limit", 10))
                .getSql().replaceAll("\\s+", " ");
        String completionSql = configuration.getMappedStatement(
                        "com.enterprise.testagent.persistence.mybatis.PersonalWorkspaceRelocationMapper.lockTargetCompletion")
                .getBoundSql(Map.of(
                        "relocationId", "pwr_1",
                        "sourceLinuxServerId", "server-a",
                        "targetLinuxServerId", "server-b"))
                .getSql().replaceAll("\\s+", " ");

        assertThat(mismatchSql)
                .contains("b.agent_id = 'opencode'")
                .contains("b.status = 'ACTIVE'")
                .contains("active_run.status in ('PENDING', 'RUNNING', 'CANCELLING')")
                .contains("w.linux_server_id <> b.linux_server_id");
        assertThat(completionSql)
                .contains("relocation.status = 'APPLYING'")
                .contains("runtime_workspace.linux_server_id = ?")
                .contains("binding.linux_server_id = ?")
                .contains("for update");
    }

    @Test
    void targetCompletionUpdatesWorkspacePersonalAndStateAfterFence() {
        PersonalWorkspaceRelocationMapper mapper = mock(PersonalWorkspaceRelocationMapper.class);
        MyBatisPersonalWorkspaceRelocationRepository repository =
                new MyBatisPersonalWorkspaceRelocationRepository(mapper);
        when(mapper.lockTargetCompletion("pwr_1", "server-a", "server-b")).thenReturn("pwr_1");
        when(mapper.updateRuntimeWorkspaceTarget(
                "pwr_1", "server-a", "server-b", "target/workspace", "trace_1", NOW)).thenReturn(1);
        when(mapper.updatePersonalWorkspaceTarget(
                "pwr_1", "target/repo", "target/workspace", "abc123", NOW)).thenReturn(1);
        when(mapper.markTargetComplete("pwr_1", NOW)).thenReturn(1);

        assertThat(repository.completeTarget(
                "pwr_1", "server-a", "server-b", "target/repo", "target/workspace",
                "abc123", "trace_1", NOW)).isTrue();

        InOrder order = inOrder(mapper);
        order.verify(mapper).lockTargetCompletion("pwr_1", "server-a", "server-b");
        order.verify(mapper).updateRuntimeWorkspaceTarget(
                "pwr_1", "server-a", "server-b", "target/workspace", "trace_1", NOW);
        order.verify(mapper).updatePersonalWorkspaceTarget(
                "pwr_1", "target/repo", "target/workspace", "abc123", NOW);
        order.verify(mapper).markTargetComplete("pwr_1", NOW);
    }
}
