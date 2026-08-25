package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.localclient.LocalClientInstance;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceBinding;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.LocalClientMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisLocalClientInstanceRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisLocalClientWorkspaceRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisNightExecutionTaskRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisSessionRuntimeTargetRepository;
import com.enterprise.testagent.persistence.mybatis.NightExecutionTaskMapper;
import com.enterprise.testagent.persistence.mybatis.SessionRuntimeTargetMapper;
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
    private MyBatisLocalClientWorkspaceRepository workspaceRepository;
    private MyBatisSessionRuntimeTargetRepository sessionTargetRepository;
    private MyBatisNightExecutionTaskRepository nightTaskRepository;

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
                            last_disconnected_at timestamp with time zone,
                            unique(client_instance_id, user_id)
                        )
                        """).update();
        jdbc.sql("create table workspaces (workspace_id varchar(128) primary key)").update();
        jdbc.sql("""
                        create table local_client_workspaces (
                            workspace_id varchar(128) primary key references workspaces(workspace_id),
                            user_id varchar(128) not null references users(user_id),
                            client_instance_id varchar(128) not null,
                            normalized_root_path text not null,
                            root_digest varchar(64) not null,
                            file_system_identity varchar(512) not null,
                            created_at timestamp with time zone not null,
                            updated_at timestamp with time zone not null,
                            unique(user_id, client_instance_id, root_digest)
                        )
                        """).update();
        jdbc.sql("""
                        create table sessions (
                            session_id varchar(128) primary key,
                            workspace_id varchar(128) not null references workspaces(workspace_id),
                            runtime_kind varchar(32) not null,
                            local_client_instance_id varchar(128)
                        )
                        """).update();
        jdbc.sql("""
                        create table night_execution_tasks (
                            task_id varchar(128) primary key,
                            workspace_id varchar(128) not null references workspaces(workspace_id),
                            status varchar(32) not null,
                            target_runtime_kind varchar(32) not null,
                            target_local_client_instance_id varchar(128),
                            state_version bigint not null,
                            updated_at timestamp with time zone not null
                        )
                        """).update();
        jdbc.sql("""
                        create table local_client_instance_replacements (
                            replaced_client_instance_id varchar(128) primary key,
                            replacement_client_instance_id varchar(128) not null,
                            user_id varchar(128) not null,
                            replaced_at timestamp with time zone not null
                        )
                        """).update();
        jdbc.sql("insert into users(user_id) values (:userId)")
                .param("userId", USER_ID.value())
                .update();

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(
                new ClassPathResource("mybatis/LocalClientMapper.xml"),
                new ClassPathResource("mybatis/SessionRuntimeTargetMapper.xml"),
                new ClassPathResource("mybatis/NightExecutionTaskMapper.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        SqlSessionTemplate template = new SqlSessionTemplate(sessionFactory);
        LocalClientMapper mapper = template.getMapper(LocalClientMapper.class);
        repository = new MyBatisLocalClientInstanceRepository(mapper);
        workspaceRepository = new MyBatisLocalClientWorkspaceRepository(mapper);
        sessionTargetRepository = new MyBatisSessionRuntimeTargetRepository(
                template.getMapper(SessionRuntimeTargetMapper.class));
        nightTaskRepository = new MyBatisNightExecutionTaskRepository(
                template.getMapper(NightExecutionTaskMapper.class));
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

    @Test
    void replacedInstanceIsHiddenFromUserProjectionButRemainsAvailableForHistory() {
        LocalClientInstanceId replacementId = new LocalClientInstanceId("lci_local_replacement");
        LocalClientInstance oldInstance = instance(INSTANCE_ID, CONNECTED_AT);
        LocalClientInstance replacementInstance = instance(replacementId, CONNECTED_AT.plusSeconds(60));
        insert(oldInstance);
        insert(replacementInstance);

        repository.markReplaced(USER_ID, INSTANCE_ID, replacementId, CONNECTED_AT.plusSeconds(120));

        assertThat(repository.findByUserId(USER_ID)).containsExactly(replacementInstance);
        assertThat(repository.findById(INSTANCE_ID)).contains(oldInstance);
        assertThat(repository.findAll()).containsExactly(replacementInstance, oldInstance);

        repository.clearReplacement(INSTANCE_ID);
        assertThat(repository.findByUserId(USER_ID)).containsExactly(replacementInstance, oldInstance);
    }

    @Test
    void verifiedWorkspaceRebindUpdatesSessionAndOnlyScheduledNightTarget() {
        LocalClientInstanceId replacementId = new LocalClientInstanceId("lci_workspace_replacement");
        WorkspaceId workspaceId = new WorkspaceId("wrk_workspace_replacement");
        insert(instance(INSTANCE_ID, CONNECTED_AT));
        insert(instance(replacementId, CONNECTED_AT.plusSeconds(60)));
        jdbc.sql("insert into workspaces(workspace_id) values (:workspaceId)")
                .param("workspaceId", workspaceId.value())
                .update();
        jdbc.sql("""
                        insert into local_client_workspaces(
                            workspace_id, user_id, client_instance_id, normalized_root_path,
                            root_digest, file_system_identity, created_at, updated_at
                        ) values (
                            :workspaceId, :userId, :clientInstanceId, :rootPath,
                            :rootDigest, :fileSystemIdentity, :createdAt, :updatedAt
                        )
                        """)
                .param("workspaceId", workspaceId.value())
                .param("userId", USER_ID.value())
                .param("clientInstanceId", INSTANCE_ID.value())
                .param("rootPath", "/home/test/project")
                .param("rootDigest", "verified-root")
                .param("fileSystemIdentity", "verified-file-system")
                .param("createdAt", CONNECTED_AT)
                .param("updatedAt", CONNECTED_AT)
                .update();
        jdbc.sql("""
                        insert into sessions(session_id, workspace_id, runtime_kind, local_client_instance_id)
                        values ('ses_rebind', :workspaceId, 'LOCAL_CLIENT', :clientInstanceId)
                        """)
                .param("workspaceId", workspaceId.value())
                .param("clientInstanceId", INSTANCE_ID.value())
                .update();
        for (String status : List.of("SCHEDULED", "DISPATCHING")) {
            jdbc.sql("""
                            insert into night_execution_tasks(
                                task_id, workspace_id, status, target_runtime_kind,
                                target_local_client_instance_id, state_version, updated_at
                            ) values (
                                :taskId, :workspaceId, :status, 'LOCAL_CLIENT',
                                :clientInstanceId, 3, :updatedAt
                            )
                            """)
                    .param("taskId", "net_" + status.toLowerCase())
                    .param("workspaceId", workspaceId.value())
                    .param("status", status)
                    .param("clientInstanceId", INSTANCE_ID.value())
                    .param("updatedAt", CONNECTED_AT)
                    .update();
        }

        LocalClientWorkspaceBinding historical = workspaceRepository
                .findByOwnerRootIdentity(USER_ID, "verified-root", "verified-file-system")
                .getFirst();
        LocalClientWorkspaceBinding replacement = new LocalClientWorkspaceBinding(
                workspaceId, USER_ID, replacementId, "/home/test/project", "verified-root",
                "verified-file-system", historical.createdAt(), CONNECTED_AT.plusSeconds(120));

        assertThat(workspaceRepository.rebind(replacement, INSTANCE_ID)).isTrue();
        assertThat(sessionTargetRepository.rebindLocalClientTargets(
                workspaceId, INSTANCE_ID, replacementId)).isEqualTo(1);
        assertThat(nightTaskRepository.rebindScheduledLocalClientTargets(
                workspaceId, INSTANCE_ID, replacementId, CONNECTED_AT.plusSeconds(120))).isEqualTo(1);

        assertThat(workspaceRepository.findByWorkspaceId(workspaceId).orElseThrow().clientInstanceId())
                .isEqualTo(replacementId);
        assertThat(jdbc.sql("select local_client_instance_id from sessions where session_id='ses_rebind'")
                        .query(String.class).single())
                .isEqualTo(replacementId.value());
        assertThat(jdbc.sql("""
                                select target_local_client_instance_id
                                from night_execution_tasks where task_id='net_scheduled'
                                """).query(String.class).single())
                .isEqualTo(replacementId.value());
        assertThat(jdbc.sql("""
                                select target_local_client_instance_id
                                from night_execution_tasks where task_id='net_dispatching'
                                """).query(String.class).single())
                .isEqualTo(INSTANCE_ID.value());
    }

    private LocalClientInstance instance(LocalClientInstanceId instanceId, Instant connectedAt) {
        return new LocalClientInstance(
                instanceId,
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
                connectedAt,
                connectedAt.plusSeconds(30),
                connectedAt,
                null);
    }

    private void insert(LocalClientInstance instance) {
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
                .param("instanceId", instance.clientInstanceId().value())
                .param("userId", instance.userId().value())
                .param("clientName", instance.clientName())
                .param("platform", instance.platform())
                .param("architecture", instance.architecture())
                .param("clientVersion", instance.clientVersion())
                .param("opencodeVersion", instance.opencodeVersion())
                .param("launcherVersion", instance.launcherVersion())
                .param("capabilities", "")
                .param("selfUpdateSupported", instance.selfUpdateSupported())
                .param("createdAt", instance.createdAt())
                .param("updatedAt", instance.updatedAt())
                .param("lastConnectedAt", instance.lastConnectedAt())
                .update();
    }
}
