package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.team.SystemAdminTeamMember;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.MyBatisSystemAdminTeamRepository;
import com.enterprise.testagent.persistence.mybatis.SystemAdminTeamMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;

/** 使用固定 .env.test PostgreSQL 在隔离 schema 验证空库迁移和团队关系 MyBatis SQL。 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SystemAdminTeamPostgresqlIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-21T08:00:00Z");
    private PGSimpleDataSource administrativeDataSource;
    private PGSimpleDataSource schemaDataSource;
    private String schema;
    private MyBatisSystemAdminTeamRepository repository;

    @BeforeAll
    void setUp() throws Exception {
        String host = System.getenv("TEST_AGENT_TEST_DB_HOST");
        Assumptions.assumeTrue(host != null && !host.isBlank(),
                "需要从仓库根目录 .env.test 导出真实 PostgreSQL 变量");
        administrativeDataSource = dataSource(null);
        schema = "test_system_admin_team_" + UUID.randomUUID().toString().replace("-", "");
        JdbcClient.create(administrativeDataSource).sql("create schema " + schema).update();
        schemaDataSource = dataSource(schema);
        Flyway.configure()
                .dataSource(schemaDataSource)
                .defaultSchema(schema)
                .schemas(schema)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        JdbcClient jdbc = JdbcClient.create(schemaDataSource);
        jdbc.sql("""
                insert into users(user_id, unified_auth_id, username, password_hash,
                                  organization, department, status, created_at, updated_at)
                values ('team-owner', 'owner-auth', '系统管理员', 'hash', '研发中心', '测试平台',
                        'ACTIVE', :now, :now),
                       ('team-member', 'member-auth', '团队成员', 'hash', '研发中心', '测试平台',
                        'ACTIVE', :now, :now)
                """).param("now", Timestamp.from(NOW)).update();
        repository = repository();
    }

    @AfterAll
    void tearDown() {
        if (administrativeDataSource != null && schema != null) {
            JdbcClient.create(administrativeDataSource)
                    .sql("drop schema if exists " + schema + " cascade").update();
        }
    }

    @Test
    void migratesEmptySchemaAndExecutesPostgresqlUpsertAndDepartmentSearch() {
        UserId owner = new UserId("team-owner");
        UserId member = new UserId("team-member");
        repository.save(new SystemAdminTeamMember(owner, member, owner, NOW, NOW, null));

        assertThat(repository.findMembers(owner, "测试平台", new PageRequest(1, 20)).items())
                .singleElement()
                .satisfies(view -> assertThat(view.user().userId()).isEqualTo(member));
        repository.deactivate(owner, member, NOW.plusSeconds(1));
        repository.save(new SystemAdminTeamMember(owner, member, owner, NOW, NOW.plusSeconds(2), null));
        assertThat(repository.find(owner, member)).get().satisfies(saved -> {
            assertThat(saved.createdAt()).isEqualTo(NOW);
            assertThat(saved.deletedAt()).isNull();
        });

        JdbcClient jdbc = JdbcClient.create(schemaDataSource);
        assertThat(jdbc.sql("""
                select count(*) from information_schema.columns
                where table_schema=:schema and table_name='team_workspace_export_items'
                  and column_name in ('lease_owner','lease_token','lease_expires_at')
                """).param("schema", schema).query(Integer.class).single()).isEqualTo(3);
        assertThat(jdbc.sql("""
                select count(*) from information_schema.constraint_column_usage
                where table_schema=:schema and table_name='support_access_audit_events'
                  and constraint_name='ck_support_access_audit_kind'
                """).param("schema", schema).query(Integer.class).single()).isPositive();
    }

    @Test
    void upgradesFixedAcceptanceDatabaseBaselineToCurrentHead() {
        String upgradeSchema = "test_system_admin_team_upgrade_"
                + UUID.randomUUID().toString().replace("-", "");
        JdbcClient.create(administrativeDataSource).sql("create schema " + upgradeSchema).update();
        try {
            PGSimpleDataSource upgradeDataSource = dataSource(upgradeSchema);
            Flyway.configure()
                    .dataSource(upgradeDataSource)
                    .defaultSchema(upgradeSchema)
                    .schemas(upgradeSchema)
                    .locations("classpath:db/migration")
                    .target("20260912123831")
                    .load()
                    .migrate();
            assertThat(JdbcClient.create(upgradeDataSource).sql("""
                    select count(*) from information_schema.tables
                    where table_schema=:schema and table_name='system_admin_team_members'
                    """).param("schema", upgradeSchema).query(Integer.class).single()).isZero();

            Flyway.configure()
                    .dataSource(upgradeDataSource)
                    .defaultSchema(upgradeSchema)
                    .schemas(upgradeSchema)
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            assertThat(JdbcClient.create(upgradeDataSource).sql("""
                    select count(*) from information_schema.tables
                    where table_schema=:schema and table_name in (
                        'system_admin_team_members','team_workspace_export_jobs','team_workspace_export_items')
                    """).param("schema", upgradeSchema).query(Integer.class).single()).isEqualTo(3);
        } finally {
            JdbcClient.create(administrativeDataSource)
                    .sql("drop schema if exists " + upgradeSchema + " cascade").update();
        }
    }

    private MyBatisSystemAdminTeamRepository repository() throws Exception {
        VendorDatabaseIdProvider provider = new VendorDatabaseIdProvider();
        Properties ids = new Properties();
        ids.setProperty("PostgreSQL", "postgresql");
        provider.setProperties(ids);
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(schemaDataSource);
        factoryBean.setDatabaseIdProvider(provider);
        factoryBean.setMapperLocations(new ClassPathResource("mybatis/SystemAdminTeamMapper.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        SystemAdminTeamMapper mapper = new SqlSessionTemplate(factory).getMapper(SystemAdminTeamMapper.class);
        return new MyBatisSystemAdminTeamRepository(mapper);
    }

    private PGSimpleDataSource dataSource(String currentSchema) {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setServerNames(new String[] {required("TEST_AGENT_TEST_DB_HOST")});
        dataSource.setPortNumbers(new int[] {Integer.parseInt(required("TEST_AGENT_TEST_DB_PORT"))});
        dataSource.setDatabaseName(required("TEST_AGENT_TEST_DB_NAME"));
        dataSource.setUser(required("TEST_AGENT_TEST_DB_USERNAME"));
        dataSource.setPassword(required("TEST_AGENT_TEST_DB_PASSWORD"));
        if (currentSchema != null) dataSource.setCurrentSchema(currentSchema);
        return dataSource;
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " 未通过 .env.test 提供");
        return value;
    }
}
