package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.memory.MemoryEvidence;
import com.enterprise.testagent.domain.memory.MemoryId;
import com.enterprise.testagent.domain.memory.MemoryScope;
import com.enterprise.testagent.domain.memory.MemorySettings;
import com.enterprise.testagent.domain.memory.MemorySource;
import com.enterprise.testagent.domain.memory.MemoryStatus;
import com.enterprise.testagent.domain.memory.MemoryWhitelistEntry;
import com.enterprise.testagent.domain.memory.QaMemory;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.memory.QaTaskType;
import com.enterprise.testagent.persistence.mybatis.MyBatisQaMemoryRepository;
import com.enterprise.testagent.persistence.mybatis.QaMemoryMapper;
import java.time.Instant;
import java.sql.Timestamp;
import java.util.List;
import java.util.Properties;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 在显式提供的临时 PostgreSQL 数据库上验证完整 Flyway 链与生产 databaseId SQL。 */
@EnabledIfEnvironmentVariable(named = "TEST_AGENT_MEMORY_POSTGRES_URL", matches = ".+")
class MyBatisQaMemoryPostgresqlIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-09T12:00:00Z");
    private static QaMemoryRepository repository;
    private static JdbcClient jdbc;

    @BeforeAll
    static void setUp() throws Exception {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setURL(System.getenv("TEST_AGENT_MEMORY_POSTGRES_URL"));
        dataSource.setUser(System.getenv("TEST_AGENT_MEMORY_POSTGRES_USERNAME"));
        dataSource.setPassword(System.getenv("TEST_AGENT_MEMORY_POSTGRES_PASSWORD"));
        jdbc = JdbcClient.create(dataSource);
        // 先形成已部署记忆基线，再从该真实 PostgreSQL 历史升级到当前 HEAD；不能只测空库直达。
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("20260809230000").load().migrate();
        assertThat(jdbc.sql("""
                select count(*) from information_schema.table_constraints
                where table_name = 'qa_memories'
                  and constraint_name = 'uk_qa_memories_mem0_memory_id'
                """).query(Long.class).single()).isZero();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        seedParents();

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties ids = new Properties();
        ids.setProperty("PostgreSQL", "postgresql");
        provider.setProperties(ids);
        factory.setDatabaseIdProvider(provider);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        sessionFactory.getConfiguration().setMapUnderscoreToCamelCase(true);
        repository = new MyBatisQaMemoryRepository(
                new SqlSessionTemplate(sessionFactory).getMapper(QaMemoryMapper.class));
    }

    @Test
    void completeMigrationUsesPostgresqlIdempotencyAndKeepsWhitelistEmptyByDefault() {
        QaMemory memory = new QaMemory(
                new MemoryId("mem_pg"), "mem0_pg", MemoryScope.PERSONAL_GLOBAL, "usr_memory_pg", null,
                MemoryStatus.ACTIVE, MemorySource.MANUAL, List.of(QaTaskType.RISK_ANALYSIS),
                "风险分析必须给出证据", 1.0d, 0, 1, NOW, NOW, NOW,
                null, "usr_memory_pg", 0L, "SYNCED", NOW, NOW);
        repository.insertMemory(memory);
        assertThat(repository.findById(memory.memoryId())).isPresent();
        QaMemory duplicate = new QaMemory(
                new MemoryId("mem_pg_duplicate"), "mem0_pg", MemoryScope.PERSONAL_GLOBAL,
                "usr_memory_pg", null, MemoryStatus.ACTIVE, MemorySource.MANUAL,
                List.of(QaTaskType.RISK_ANALYSIS), "重复事实不得重复建档", 1.0d,
                0, 1, NOW, NOW, NOW, null, "usr_memory_pg", 0L, "SYNCED", NOW, NOW);
        assertThat(repository.insertMemoryIfAbsentByMem0MemoryId(duplicate)).isFalse();
        assertThat(repository.countWhitelist()).isZero();

        repository.insertEvidence(new MemoryEvidence(
                "mev_pg", memory.memoryId(), "run_memory_pg", "ses_memory_pg",
                "usr_memory_pg", MemorySource.NATIVE, "只保存安全证据引用", NOW));
        assertThat(repository.listEvidence(memory.memoryId())).singleElement().satisfies(evidence -> {
            assertThat(evidence.sessionTitle()).isEqualTo("通用记忆会话");
            assertThat(evidence.sessionOwnerUserId()).isEqualTo("usr_memory_pg");
            assertThat(evidence.runId()).isEqualTo("run_memory_pg");
        });

        MemorySettings settings = repository.loadSettings();
        assertThat(settings.primaryEmbeddingModelId()).isNull();
        assertThat(settings.cpuEmbeddingModelId()).isEqualTo("memory-bge-small-zh-v1.5");
        MemorySettings updatedSettings = new MemorySettings(
                "memory-chat", "enterprise-embedding", settings.cpuEmbeddingModelId(),
                settings.version() + 1, "usr_memory_pg", NOW.plusSeconds(1));
        assertThat(repository.updateSettings(updatedSettings, settings.version())).isTrue();
        assertThat(repository.loadSettings()).isEqualTo(updatedSettings);

        MemoryWhitelistEntry entry = new MemoryWhitelistEntry(
                "usr_memory_pg", true, "usr_memory_pg", NOW, NOW);
        repository.upsertWhitelist(entry);
        repository.upsertWhitelist(new MemoryWhitelistEntry(
                "usr_memory_pg", false, "usr_memory_pg", NOW, NOW.plusSeconds(1)));
        assertThat(repository.isWhitelisted("usr_memory_pg")).isFalse();

        String checksum = jdbc.sql("""
                select checksum::text from flyway_schema_history where version = '20260809120000'
                """).query(String.class).single();
        assertThat(checksum).isNotBlank();
        assertThat(jdbc.sql("""
                select checksum::text from flyway_schema_history where version = '20260809230000'
                """).query(String.class).single()).isNotBlank();
        assertThat(jdbc.sql("""
                select count(*) from information_schema.columns
                where table_name = 'internal_model_provider_models'
                  and column_name = 'embedding_dimension'
                """).query(Long.class).single()).isEqualTo(1L);
        assertThat(jdbc.sql("""
                select count(*) from information_schema.table_constraints
                where table_name = 'qa_memories'
                  and constraint_name = 'uk_qa_memories_mem0_memory_id'
                  and constraint_type = 'UNIQUE'
                """).query(Long.class).single()).isEqualTo(1L);
    }

    private static void seedParents() {
        jdbc.sql("""
                insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                values ('usr_memory_pg', 'U_MEMORY_PG', 'memory-pg', 'x', 'ACTIVE', :now, :now)
                """).param("now", Timestamp.from(NOW)).update();
        jdbc.sql("""
                insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                values ('wks_memory_pg', '通用记忆工作区', '/tmp/memory-pg', 'ACTIVE',
                        'trace_memory_pg', :now, :now)
                """).param("now", Timestamp.from(NOW)).update();
        jdbc.sql("""
                insert into sessions(
                    session_id, workspace_id, title, status, trace_id,
                    created_by_user_id, created_at, updated_at
                ) values (
                    'ses_memory_pg', 'wks_memory_pg', '通用记忆会话', 'ACTIVE',
                    'trace_memory_pg', 'usr_memory_pg', :now, :now
                )
                """).param("now", Timestamp.from(NOW)).update();
        jdbc.sql("""
                insert into runs(
                    run_id, session_id, workspace_id, status, trace_id,
                    triggered_by_user_id, created_at, updated_at
                ) values (
                    'run_memory_pg', 'ses_memory_pg', 'wks_memory_pg', 'SUCCEEDED',
                    'trace_memory_pg', 'usr_memory_pg', :now, :now
                )
                """).param("now", Timestamp.from(NOW)).update();
    }
}
