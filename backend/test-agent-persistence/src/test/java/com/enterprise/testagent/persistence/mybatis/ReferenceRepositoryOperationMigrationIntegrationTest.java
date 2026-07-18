package com.enterprise.testagent.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.reference.ReferenceRepositoryOperationType;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** 验证存量未初始化状态升级后的内部操作类型与领域兼容规则一致。 */
class ReferenceRepositoryOperationMigrationIntegrationTest {

    @Test
    void migrationBackfillsInitializingAndUninitializedAsInitialize() throws Exception {
        String url = "jdbc:h2:mem:testagent_reference_migration_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                .formatted(UUID.randomUUID().toString().replace("-", ""));
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL(url);
        dataSource.setUser("sa");
        dataSource.setPassword("");
        SingleConnectionDataSource schemaDataSource = new SingleConnectionDataSource(url, "sa", "", true);
        try {
            Flyway.configure()
                    .dataSource(schemaDataSource)
                    .locations("classpath:db/migration")
                    .target("20260715213000")
                    .load()
                    .migrate();
            new ResourceDatabasePopulator(
                    new ClassPathResource("db/migration/V20260718110000__create_reference_repository_replica_tables.sql"),
                    new ClassPathResource("fixtures/reference-repository-operation-migration.sql"),
                    new ClassPathResource("db/migration/V20260718143000__add_reference_repository_operations_and_verification.sql"))
                    .execute(schemaDataSource);

            SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
            factoryBean.setDataSource(dataSource);
            factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:mybatis/**/*.xml"));
            SqlSessionFactory sqlSessionFactory = factoryBean.getObject();
            MyBatisReferenceRepositoryRepository repository = new MyBatisReferenceRepositoryRepository(
                    new SqlSessionTemplate(sqlSessionFactory).getMapper(ReferenceRepositoryMapper.class));

            assertThat(repository.findState(new CodeRepositoryId("repo_migration_initializing")))
                    .get().extracting(state -> state.operationType())
                    .isEqualTo(ReferenceRepositoryOperationType.INITIALIZE);
            assertThat(repository.findState(new CodeRepositoryId("repo_migration_uninitialized")))
                    .get().extracting(state -> state.operationType())
                    .isEqualTo(ReferenceRepositoryOperationType.INITIALIZE);
        } finally {
            schemaDataSource.destroy();
        }
    }
}
