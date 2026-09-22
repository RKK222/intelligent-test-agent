package com.enterprise.testagent.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.team.TeamMembershipState;
import com.enterprise.testagent.domain.user.UserId;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

/** 验证团队列表在指定成员后只返回该成员的当前权限和历史个人工作区。 */
class MyBatisTeamWorkspaceMemberFilterIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-09-22T03:00:00Z");
    private JdbcClient jdbc;
    private MyBatisTeamWorkspaceQueryRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        SingleConnectionDataSource dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:team_member_filter_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        new ResourceDatabasePopulator(new ByteArrayResource("""
                create table applications (
                    app_id varchar(128) primary key,
                    app_name varchar(255) not null,
                    enabled boolean not null
                );
                create table application_members (
                    app_id varchar(128) not null,
                    user_id varchar(128) not null,
                    deleted_at timestamp
                );
                create table application_workspaces (
                    workspace_id varchar(128) primary key,
                    app_id varchar(128) not null,
                    workspace_name varchar(255) not null,
                    branch varchar(255) not null,
                    directory_path varchar(255) not null,
                    enabled boolean not null
                );
                create table application_workspace_versions (
                    version_id varchar(128) primary key,
                    application_workspace_id varchar(128) not null,
                    app_id varchar(128) not null,
                    repository_id varchar(128) not null,
                    version varchar(128) not null,
                    branch varchar(255) not null,
                    repo_root_path varchar(255) not null,
                    workspace_root_path varchar(255) not null,
                    runtime_workspace_id varchar(128) not null,
                    created_by_user_id varchar(128) not null,
                    status varchar(32) not null,
                    target_commit_hash varchar(128),
                    target_commit_updated_at timestamp,
                    created_at timestamp not null,
                    updated_at timestamp not null
                );
                create table personal_workspaces (
                    personal_workspace_id varchar(128) primary key,
                    app_id varchar(128) not null,
                    user_id varchar(128) not null,
                    application_workspace_id varchar(128) not null,
                    app_workspace_version_id varchar(128) not null,
                    workspace_name varchar(255) not null
                );
                create table system_admin_team_members (
                    owner_user_id varchar(128) not null,
                    member_user_id varchar(128) not null,
                    deleted_at timestamp
                );
                """.getBytes(StandardCharsets.UTF_8))).execute(dataSource);
        jdbc = JdbcClient.create(dataSource);
        insertFixture();
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new ClassPathResource("mybatis/TeamWorkspaceQueryMapper.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        repository = new MyBatisTeamWorkspaceQueryRepository(
                new SqlSessionTemplate(factory).getMapper(TeamWorkspaceQueryMapper.class));
    }

    @Test
    void targetUserSeesCurrentAppsAndHistoricalWorkspacesWithoutOtherMembers() {
        UserId owner = new UserId("owner");
        UserId member = new UserId("member-1");

        assertThat(repository.findApplications(false, owner, null))
                .extracting(item -> item.appId())
                .containsExactlyInAnyOrder("app-current", "app-history");
        assertThat(repository.findApplications(false, owner, member))
                .extracting(item -> item.appId() + ":" + item.membershipState())
                .containsExactlyInAnyOrder("app-current:CURRENT", "app-history:HISTORICAL");

        assertThat(repository.findWorkspaceTemplates(false, owner, "app-current", member))
                .extracting(item -> item.workspaceId())
                .containsExactly("awp_current");
        assertThat(repository.findWorkspaceTemplates(false, owner, "app-history", member))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.workspaceId()).isEqualTo("awp_history");
                    assertThat(item.membershipState()).isEqualTo(TeamMembershipState.HISTORICAL);
                });

        assertThat(repository.findWorkspaceVersions(false, owner, "awp_current", member))
                .extracting(item -> item.version().versionId().value() + ":" + item.membershipState())
                .containsExactly("ver-current-new:CURRENT", "ver-current-old:CURRENT");
        assertThat(repository.findWorkspaceVersions(false, owner, "awp_history", member))
                .extracting(item -> item.version().versionId().value())
                .containsExactly("ver-history");
        assertThat(repository.findWorkspaceVersions(false, owner, "awp_current", new UserId("member-2")))
                .isEmpty();
    }

    private void insertFixture() {
        jdbc.sql("""
                insert into applications(app_id, app_name, enabled) values
                ('app-current', '当前应用', true),
                ('app-history', '历史应用', true),
                ('app-other', '其他成员应用', true)
                """).update();
        jdbc.sql("""
                insert into application_members(app_id, user_id, deleted_at) values
                ('app-current', 'member-1', null),
                ('app-other', 'member-2', null)
                """).update();
        jdbc.sql("""
                insert into system_admin_team_members(owner_user_id, member_user_id, deleted_at) values
                ('owner', 'member-1', null)
                """).update();
        jdbc.sql("""
                insert into application_workspaces(
                    workspace_id, app_id, workspace_name, branch, directory_path, enabled) values
                ('awp_current', 'app-current', '研发空间', 'release', 'repo', true),
                ('awp_history', 'app-history', '历史空间', 'release', 'repo', true)
                """).update();
        jdbc.sql("""
                insert into application_workspace_versions(
                    version_id, application_workspace_id, app_id, repository_id, version, branch,
                    repo_root_path, workspace_root_path, runtime_workspace_id, created_by_user_id, status,
                    target_commit_hash, target_commit_updated_at, created_at, updated_at) values
                ('ver-current-old', 'awp_current', 'app-current', 'repo_1', 'v1', 'release',
                 '/repo', '/repo/ws', 'wrk_runtime_1', 'owner', 'ACTIVE', 'abc', :now, :older, :now),
                ('ver-current-new', 'awp_current', 'app-current', 'repo_1', 'v2', 'release',
                 '/repo', '/repo/ws', 'wrk_runtime_2', 'owner', 'ACTIVE', 'def', :now, :now, :now),
                ('ver-history', 'awp_history', 'app-history', 'repo_1', 'v1', 'release',
                 '/repo', '/repo/ws', 'wrk_runtime_3', 'owner', 'ACTIVE', 'ghi', :now, :now, :now)
                """).param("now", NOW).param("older", NOW.minusSeconds(60)).update();
        jdbc.sql("""
                insert into personal_workspaces(
                    personal_workspace_id, app_id, user_id, application_workspace_id,
                    app_workspace_version_id, workspace_name) values
                ('pw-history', 'app-history', 'member-1', 'awp_history', 'ver-history', 'default'),
                ('pw-other', 'app-other', 'member-2', 'awp_other', 'ver-other', 'default')
                """).update();
    }
}
