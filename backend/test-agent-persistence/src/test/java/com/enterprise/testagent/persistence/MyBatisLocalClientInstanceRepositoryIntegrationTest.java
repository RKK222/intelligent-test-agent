package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.localclient.LocalClientInstance;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.LocalClientMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisLocalClientInstanceRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/** 验证本地客户端实例查询能按 record 构造器的原始类型完整映射。 */
class MyBatisLocalClientInstanceRepositoryIntegrationTest {

    private static final UserId USER_ID = new UserId("usr_local_instance");
    private static final LocalClientInstanceId INSTANCE_ID = new LocalClientInstanceId("lci_local_instance");
    private static final Instant CONNECTED_AT = Instant.parse("2026-08-22T03:41:14Z");

    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbc;
    private MyBatisLocalClientInstanceRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_local_client_instance_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        jdbc = JdbcClient.create(dataSource);
        jdbc.sql("create table users (user_id varchar(128) primary key)").update();
        jdbc.sql("""
                        create table local_client_instances (
                            client_instance_id varchar(128) primary key,
                            user_id varchar(128) not null references users(user_id),
                            client_name varchar(256) not null,
                            platform varchar(32) not null,
                            architecture varchar(32) not null,
                            client_version varchar(64) not null,
                            opencode_version varchar(64) not null,
                            launcher_version varchar(32),
                            self_update_capabilities text not null,
                            self_update_supported boolean not null,
                            last_update_status varchar(32),
                            last_update_target_version varchar(14),
                            last_update_at timestamp with time zone,
                            created_at timestamp with time zone not null,
                            updated_at timestamp with time zone not null,
                            last_connected_at timestamp with time zone,
                            last_disconnected_at timestamp with time zone
                        )
                        """).update();
        jdbc.sql("insert into users(user_id) values (:userId)")
                .param("userId", USER_ID.value())
                .update();

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new ClassPathResource("mybatis/LocalClientMapper.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        LocalClientMapper mapper = new SqlSessionTemplate(sessionFactory).getMapper(LocalClientMapper.class);
        repository = new MyBatisLocalClientInstanceRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void loadsPrimitiveBooleanFromAllInstanceQueries() {
        jdbc.sql("""
                        insert into local_client_instances(
                            client_instance_id, user_id, client_name, platform, architecture,
                            client_version, opencode_version, launcher_version, self_update_capabilities,
                            self_update_supported, created_at, updated_at, last_connected_at
                        ) values (
                            :instanceId, :userId, :clientName, :platform, :architecture,
                            :clientVersion, :opencodeVersion, :launcherVersion, :capabilities,
                            :selfUpdateSupported, :createdAt, :updatedAt, :lastConnectedAt
                        )
                        """)
                .param("instanceId", INSTANCE_ID.value())
                .param("userId", USER_ID.value())
                .param("clientName", "kaka@mac-dev")
                .param("platform", "darwin")
                .param("architecture", "arm64")
                .param("clientVersion", "0.1.0-dev")
                .param("opencodeVersion", "1.18.4")
                .param("launcherVersion", "1")
                .param("capabilities", "")
                .param("selfUpdateSupported", false)
                .param("createdAt", CONNECTED_AT)
                .param("updatedAt", CONNECTED_AT.plusSeconds(30))
                .param("lastConnectedAt", CONNECTED_AT)
                .update();

        LocalClientInstance expected = new LocalClientInstance(
                INSTANCE_ID,
                USER_ID,
                "kaka@mac-dev",
                "darwin",
                "arm64",
                "0.1.0-dev",
                "1.18.4",
                "1",
                List.of(),
                false,
                null,
                null,
                null,
                CONNECTED_AT,
                CONNECTED_AT.plusSeconds(30),
                CONNECTED_AT,
                null);

        assertThat(repository.findById(INSTANCE_ID)).contains(expected);
        assertThat(repository.findByUserId(USER_ID)).containsExactly(expected);
        assertThat(repository.findAll()).containsExactly(expected);
    }
}
