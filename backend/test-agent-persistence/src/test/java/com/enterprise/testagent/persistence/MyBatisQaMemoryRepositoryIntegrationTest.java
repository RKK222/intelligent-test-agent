package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.memory.MemoryId;
import com.enterprise.testagent.domain.memory.MemoryLearningJob;
import com.enterprise.testagent.domain.memory.MemoryLearningEvidenceRepository;
import com.enterprise.testagent.domain.memory.MemoryScope;
import com.enterprise.testagent.domain.memory.MemorySkillProposal;
import com.enterprise.testagent.domain.memory.MemorySkillProposalStatus;
import com.enterprise.testagent.domain.memory.MemorySource;
import com.enterprise.testagent.domain.memory.MemoryStatus;
import com.enterprise.testagent.domain.memory.QaMemory;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.memory.QaTaskType;
import com.enterprise.testagent.persistence.mybatis.MemoryLearningEvidenceMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisMemoryLearningEvidenceRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisQaMemoryRepository;
import com.enterprise.testagent.persistence.mybatis.QaMemoryMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** 使用真实 Flyway 基线与 MyBatis XML 固化记忆治理、乐观锁和无原文 Outbox。 */
class MyBatisQaMemoryRepositoryIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private SingleConnectionDataSource dataSource;
    private QaMemoryRepository repository;
    private MemoryLearningEvidenceRepository evidenceRepository;
    private JdbcClient jdbc;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                ("jdbc:h2:mem:testagent_memory_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false;"
                        + "INIT=CREATE DOMAIN IF NOT EXISTS timestamptz AS TIMESTAMP WITH TIME ZONE")
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").target("9").load().migrate();
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V16__add_message_and_run_usage_fields.sql")).execute(dataSource);
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260809120000__create_qa_memory_governance.sql")).execute(dataSource);
        jdbc = JdbcClient.create(dataSource);
        seedParents();
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        QaMemoryMapper mapper = new SqlSessionTemplate(sessionFactory).getMapper(QaMemoryMapper.class);
        repository = new MyBatisQaMemoryRepository(mapper);
        MemoryLearningEvidenceMapper evidenceMapper = new SqlSessionTemplate(sessionFactory)
                .getMapper(MemoryLearningEvidenceMapper.class);
        evidenceRepository = new MyBatisMemoryLearningEvidenceRepository(evidenceMapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void persistsGovernanceClaimsOutboxAndUsesOptimisticVersion() {
        QaMemory memory = personalMemory(0L, MemoryStatus.ACTIVE, NOW);
        repository.insertMemory(memory);
        assertThat(repository.listPersonal("usr_memory", null, null, 0, 10)).singleElement()
                .satisfies(found -> {
                    assertThat(found.mem0MemoryId()).isEqualTo("mem0_memory");
                    assertThat(found.taskTypes()).containsExactly(QaTaskType.TEST_CASE_GENERATION);
                });

        QaMemory paused = personalMemory(1L, MemoryStatus.PAUSED, NOW.plusSeconds(1));
        assertThat(repository.updateMemory(paused, 0L)).isTrue();
        assertThat(repository.updateMemory(paused, 0L)).isFalse();

        MemoryLearningJob job = new MemoryLearningJob(
                "mlj_1", "run_memory", "ses_memory", "wks_memory", "usr_memory", "app_memory",
                "opencode", "Qwen3.6-27B", "PENDING", 0, NOW,
                null, null, null, NOW, NOW);
        assertThat(repository.enqueueLearningJob(job)).isTrue();
        assertThat(repository.enqueueLearningJob(job)).isFalse();
        assertThat(repository.claimLearningJobs("worker_1", NOW, NOW.plusSeconds(30), 10))
                .singleElement().extracting(MemoryLearningJob::status).isEqualTo("PROCESSING");
        assertThat(repository.completeLearningJob("mlj_1", "worker_1", NOW.plusSeconds(1))).isTrue();
    }

    @Test
    void schemaContainsOnlySafeEvidenceAndLocatorColumns() {
        List<String> outboxColumns = jdbc.sql("""
                select "COLUMN_NAME" from INFORMATION_SCHEMA.COLUMNS
                where lower("TABLE_NAME") = 'qa_memory_learning_outbox'
                """).query(String.class).list();
        assertThat(outboxColumns).noneMatch(column -> {
            String lower = column.toLowerCase();
            return lower.contains("prompt") || lower.contains("message_content") || lower.contains("answer");
        });
        assertThat(jdbc.sql("select count(*) from qa_memory_whitelist").query(Long.class).single()).isZero();
    }

    @Test
    void learningEvidenceReadsOnlyExistingUserAndAssistantMessagesForTheRun() {
        jdbc.sql("""
                insert into session_messages(message_id, session_id, role, content, trace_id, created_at, run_id)
                values
                  ('msg_user', 'ses_memory', 'USER', '必须覆盖异常场景', 'trace_memory', :now, 'run_memory'),
                  ('msg_assistant', 'ses_memory', 'ASSISTANT', '已覆盖异常场景', 'trace_memory', :later, 'run_memory'),
                  ('msg_tool', 'ses_memory', 'TOOL', 'secret tool output', 'trace_memory', :later, 'run_memory')
                """).param("now", NOW).param("later", NOW.plusSeconds(1)).update();

        assertThat(evidenceRepository.findByRunId("run_memory")).hasValueSatisfying(evidence -> {
            assertThat(evidence.messages()).extracting(item -> item.role())
                    .containsExactly("user", "assistant");
            assertThat(evidence.messages()).extracting(item -> item.content())
                    .containsExactly("必须覆盖异常场景", "已覆盖异常场景")
                    .doesNotContain("secret tool output");
        });
    }

    @Test
    void skillProposalStatusRoundTripsAndUsesOptimisticVersion() {
        repository.insertMemory(personalMemory(0L, MemoryStatus.ACTIVE, NOW));
        MemorySkillProposal pending = new MemorySkillProposal(
                "msp_memory", new MemoryId("mem_memory"), "app_memory", "异常边界检查", "",
                MemorySkillProposalStatus.PENDING_REVIEW, "usr_memory", null, null, 0L, NOW, NOW);
        repository.insertSkillProposal(pending);

        assertThat(repository.findSkillProposal("msp_memory")).hasValueSatisfying(found -> {
            assertThat(found.status()).isEqualTo(MemorySkillProposalStatus.PENDING_REVIEW);
            assertThat(found.skillMdDraft()).isEmpty();
        });

        MemorySkillProposal draft = new MemorySkillProposal(
                pending.proposalId(), pending.memoryId(), pending.applicationId(), pending.title(),
                "---\nname: edge-check\n---", MemorySkillProposalStatus.DRAFT,
                pending.createdByUserId(), "usr_memory", null, 1L, NOW, NOW.plusSeconds(1));
        assertThat(repository.updateSkillProposal(draft, 0L)).isTrue();
        assertThat(repository.updateSkillProposal(draft, 0L)).isFalse();
        assertThat(repository.findSkillProposal("msp_memory"))
                .get().extracting(MemorySkillProposal::status).isEqualTo(MemorySkillProposalStatus.DRAFT);
    }

    private QaMemory personalMemory(long version, MemoryStatus status, Instant updatedAt) {
        return new QaMemory(
                new MemoryId("mem_memory"), "mem0_memory", MemoryScope.PERSONAL_GLOBAL,
                "usr_memory", null, status, MemorySource.MANUAL,
                List.of(QaTaskType.TEST_CASE_GENERATION), "覆盖异常与边界", 1.0d,
                0, 1, NOW, NOW, NOW, null, "usr_memory", version, "SYNCED", NOW, updatedAt);
    }

    private void seedParents() {
        jdbc.sql("""
                insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                values ('usr_memory', 'U_MEMORY', 'memory-user', 'x', 'ACTIVE', :now, :now)
                """).param("now", NOW).update();
        jdbc.sql("""
                insert into applications(app_id, app_name, enabled, created_at, updated_at)
                values ('app_memory', '记忆测试应用', true, :now, :now)
                """).param("now", NOW).update();
        jdbc.sql("""
                insert into application_members(app_id, user_id, created_at, updated_at)
                values ('app_memory', 'usr_memory', :now, :now)
                """).param("now", NOW).update();
        jdbc.sql("""
                insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                values ('wks_memory', '记忆工作区', '/tmp/memory', 'ACTIVE', 'trace_memory', :now, :now)
                """).param("now", NOW).update();
        jdbc.sql("""
                insert into sessions(session_id, workspace_id, title, status, trace_id, created_at, updated_at)
                values ('ses_memory', 'wks_memory', '记忆会话', 'ACTIVE', 'trace_memory', :now, :now)
                """).param("now", NOW).update();
        jdbc.sql("""
                insert into runs(run_id, session_id, workspace_id, status, trace_id, created_at, updated_at)
                values ('run_memory', 'ses_memory', 'wks_memory', 'SUCCEEDED', 'trace_memory', :now, :now)
                """).param("now", NOW).update();
    }
}
