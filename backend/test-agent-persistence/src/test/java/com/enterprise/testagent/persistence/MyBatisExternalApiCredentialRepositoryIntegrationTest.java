package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredential;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredentialId;
import com.enterprise.testagent.domain.externalapi.ExternalApiScope;
import com.enterprise.testagent.persistence.mybatis.ExternalApiCredentialMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisExternalApiCredentialRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
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

/** 验证外部凭据与 scope 全部通过 MyBatis XML 原子读写。 */
class MyBatisExternalApiCredentialRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-09T04:00:00Z");
    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbcClient;
    private MyBatisExternalApiCredentialRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_external_api_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        new ResourceDatabasePopulator(new ClassPathResource(
                "db/migration/V20260809110000__create_external_api_credentials.sql")).execute(dataSource);
        jdbcClient = JdbcClient.create(dataSource);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        ExternalApiCredentialMapper mapper = new SqlSessionTemplate(sessionFactory)
                .getMapper(ExternalApiCredentialMapper.class);
        repository = new MyBatisExternalApiCredentialRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void insertsPagesAndLoadsCredentialScopes() {
        ExternalApiCredential credential = credential("eac_one", "deploy.bot", "部署工具", true);

        repository.insert(credential);

        assertThat(repository.findAll()).containsExactly(credential);
        assertThat(repository.findPage("部署", true, new PageRequest(1, 20)).items())
                .containsExactly(credential);
        assertThat(repository.findPage("missing", null, new PageRequest(1, 20)).total()).isZero();
        assertThat(jdbcClient.sql("select encrypted_api_key from external_api_credentials")
                .query(String.class).single()).isEqualTo("rsa-ciphertext-eac_one");
    }

    @Test
    void replacesDetailsAndScopesWithoutChangingToolCodeOrCiphertext() {
        ExternalApiCredential original = credential("eac_one", "deploy.bot", "部署工具", true);
        repository.insert(original);
        ExternalApiCredential updated = new ExternalApiCredential(
                original.credentialId(), original.toolCode(), "发布平台", original.encryptedApiKey(),
                original.apiKeyFingerprint(), original.keyHint(), false,
                Set.of(ExternalApiScope.USER_SSH_KEY_READ), original.createdAt(), NOW.plusSeconds(10));

        repository.updateDetails(updated);

        assertThat(repository.findById(original.credentialId())).contains(updated);
    }

    @Test
    void deleteCascadesScopes() {
        ExternalApiCredential credential = credential("eac_one", "deploy.bot", "部署工具", true);
        repository.insert(credential);

        assertThat(repository.delete(credential.credentialId())).isTrue();

        assertThat(repository.findAll()).isEmpty();
        assertThat(jdbcClient.sql("select count(*) from external_api_credential_scopes")
                .query(Long.class).single()).isZero();
    }

    private static ExternalApiCredential credential(
            String id, String toolCode, String toolName, boolean enabled) {
        return new ExternalApiCredential(
                new ExternalApiCredentialId(id), toolCode, toolName, "rsa-ciphertext-" + id,
                "a".repeat(64), "taak_v1_ABC...WXYZ", enabled,
                Set.of(ExternalApiScope.USER_SSH_KEY_READ), NOW, NOW);
    }
}
