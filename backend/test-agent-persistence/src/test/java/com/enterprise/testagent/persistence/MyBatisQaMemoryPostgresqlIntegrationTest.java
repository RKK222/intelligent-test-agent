package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.memory.MemoryId;
import com.enterprise.testagent.domain.memory.MemoryScope;
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
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = JdbcClient.create(dataSource);
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
        assertThat(repository.countWhitelist()).isZero();

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
    }

    private static void seedParents() {
        jdbc.sql("""
                insert into users(user_id, unified_auth_id, username, password_hash, status, created_at, updated_at)
                values ('usr_memory_pg', 'U_MEMORY_PG', 'memory-pg', 'x', 'ACTIVE', :now, :now)
                """).param("now", Timestamp.from(NOW)).update();
    }
}
