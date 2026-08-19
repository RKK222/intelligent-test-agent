package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.AutomationWorkspaceActiveVersion;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.AutomationWorkspaceActiveVersionMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisAutomationWorkspaceActiveVersionRepository;
import java.time.Instant;
import java.util.Properties;
import java.util.UUID;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** 使用 H2 PostgreSQL 模式验证自动化代码库激活状态的 MyBatis XML 幂等读写。 */
class MyBatisAutomationWorkspaceActiveVersionRepositoryIntegrationTest {

    private static final ApplicationWorkspaceId WORKSPACE_ID = new ApplicationWorkspaceId("awp_automation");
    private static final UserId ADMIN = new UserId("usr_admin");
    private static final Instant CREATED_AT = Instant.parse("2026-08-19T06:00:00Z");

    private SingleConnectionDataSource dataSource;
    private MyBatisAutomationWorkspaceActiveVersionRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_automation_active_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        JdbcClient.create(dataSource).sql("""
                create table automation_workspace_active_versions(
                    application_workspace_id varchar(128) primary key,
                    version_id varchar(128) not null unique,
                    activated_by_user_id varchar(128),
                    activated_at timestamp with time zone not null,
                    created_at timestamp with time zone not null,
                    updated_at timestamp with time zone not null
                )
                """).update();

        VendorDatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        Properties databaseIds = new Properties();
        databaseIds.setProperty("H2", "h2");
        databaseIdProvider.setProperties(databaseIds);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setDatabaseIdProvider(databaseIdProvider);
        factory.setMapperLocations(new ClassPathResource("mybatis/AutomationWorkspaceActiveVersionMapper.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        AutomationWorkspaceActiveVersionMapper mapper = new SqlSessionTemplate(sessionFactory)
                .getMapper(AutomationWorkspaceActiveVersionMapper.class);
        repository = new MyBatisAutomationWorkspaceActiveVersionRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void firstVersionIsInitializedOnceAndExplicitActivationPreservesCreationTime() {
        AutomationWorkspaceActiveVersion first = active("awv_1", CREATED_AT);
        assertThat(repository.initializeIfAbsent(first)).isEqualTo(first);

        AutomationWorkspaceActiveVersion ignoredLaterVersion = active("awv_2", CREATED_AT.plusSeconds(30));
        assertThat(repository.initializeIfAbsent(ignoredLaterVersion).versionId().value()).isEqualTo("awv_1");

        AutomationWorkspaceActiveVersion activated = new AutomationWorkspaceActiveVersion(
                WORKSPACE_ID,
                new ApplicationWorkspaceVersionId("awv_2"),
                ADMIN,
                CREATED_AT.plusSeconds(60),
                CREATED_AT,
                CREATED_AT.plusSeconds(60));
        assertThat(repository.activate(activated)).isEqualTo(activated);
        assertThat(repository.activate(active("awv_2", CREATED_AT.plusSeconds(90)))).isEqualTo(activated);
        assertThat(repository.findByApplicationWorkspaceIds(java.util.List.of(WORKSPACE_ID, WORKSPACE_ID)))
                .containsExactly(activated);
    }

    private AutomationWorkspaceActiveVersion active(String versionId, Instant activatedAt) {
        return new AutomationWorkspaceActiveVersion(
                WORKSPACE_ID,
                new ApplicationWorkspaceVersionId(versionId),
                ADMIN,
                activatedAt,
                activatedAt,
                activatedAt);
    }
}
