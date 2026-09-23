package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.reference.ApplicationAssetReferenceStore;
import com.enterprise.testagent.persistence.mybatis.ApplicationAssetReferenceMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisApplicationAssetReferenceStore;
import java.time.Instant;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 验证共享资产引用 MyBatis 构造映射使用 primitive 字段类型，避免有数据后成员读取返回 500。
 */
class MyBatisApplicationAssetReferenceStoreIntegrationTest {
    private SingleConnectionDataSource dataSource;
    private ApplicationAssetReferenceStore store;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_asset_reference_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(System.nanoTime()),
                "sa", "", true);
        var connection = dataSource.getConnection();
        connection.createStatement().executeUpdate("""
                create table application_asset_references (
                    app_id varchar(128) not null,
                    repository_id varchar(128) not null,
                    directory_path varchar(1000) not null,
                    alias varchar(128) not null,
                    merge_enabled boolean not null,
                    sdd_folder_name varchar(255) not null,
                    description varchar(2000) not null,
                    version bigint not null,
                    updated_at timestamp not null,
                    primary key (app_id, repository_id, directory_path)
                )
                """);
        connection.createStatement().executeUpdate("""
                insert into application_asset_references
                    (app_id, repository_id, directory_path, alias, merge_enabled,
                     sdd_folder_name, description, version, updated_at)
                values ('app_demo', 'repo_assets', 'docs', 'docs-assets', true,
                        'docs', '产品资料', 1, timestamp '2026-09-23 10:00:00')
                """);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        ApplicationAssetReferenceMapper mapper = new SqlSessionTemplate(factory)
                .getMapper(ApplicationAssetReferenceMapper.class);
        store = new MyBatisApplicationAssetReferenceStore(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void listsPersistedReferenceWithPrimitiveFields() {
        var references = store.list(new ApplicationId("app_demo"));

        assertThat(references).singleElement().satisfies(reference -> {
            assertThat(reference.alias()).isEqualTo("docs-assets");
            assertThat(reference.merge()).isTrue();
            assertThat(reference.version()).isEqualTo(1L);
            assertThat(reference.updatedAt()).isNotNull().isInstanceOf(Instant.class);
        });
    }
}
