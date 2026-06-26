package com.icbc.testagent.persistence;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.common.pagination.PageResponse;
import com.icbc.testagent.domain.configuration.ApplicationDefinition;
import com.icbc.testagent.domain.configuration.ApplicationId;
import com.icbc.testagent.domain.configuration.ApplicationMember;
import com.icbc.testagent.domain.configuration.ApplicationWorkspace;
import com.icbc.testagent.domain.configuration.ApplicationWorkspaceId;
import com.icbc.testagent.domain.configuration.CodeRepository;
import com.icbc.testagent.domain.configuration.CodeRepositoryId;
import com.icbc.testagent.domain.configuration.ConfigurationManagementRepository;
import com.icbc.testagent.domain.configuration.SshKeyId;
import com.icbc.testagent.domain.configuration.UserSshKey;
import com.icbc.testagent.domain.user.UserId;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 配置管理 JDBC Repository，集中映射应用、代码库、工作空间和个人 SSH key 配置表。
 */
@Repository
public class JdbcConfigurationManagementRepository extends JdbcRepositorySupport implements ConfigurationManagementRepository {

    private final JdbcClient jdbcClient;

    private final RowMapper<ApplicationDefinition> applicationMapper = (rs, rowNum) -> new ApplicationDefinition(
            new ApplicationId(rs.getString("app_id")),
            rs.getString("app_name"),
            rs.getBoolean("enabled"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    private final RowMapper<ApplicationMember> memberMapper = (rs, rowNum) -> new ApplicationMember(
            new ApplicationId(rs.getString("app_id")),
            new UserId(rs.getString("user_id")),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            instant(rs, "deleted_at"));

    private final RowMapper<CodeRepository> repositoryMapper = (rs, rowNum) -> new CodeRepository(
            new CodeRepositoryId(rs.getString("repository_id")),
            rs.getString("git_url"),
            rs.getString("name"),
            rs.getString("english_name"),
            rs.getBoolean("standard"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    private final RowMapper<ApplicationWorkspace> workspaceMapper = (rs, rowNum) -> new ApplicationWorkspace(
            new ApplicationWorkspaceId(rs.getString("workspace_id")),
            new ApplicationId(rs.getString("app_id")),
            new CodeRepositoryId(rs.getString("repository_id")),
            rs.getString("branch"),
            rs.getString("directory_path"),
            rs.getString("workspace_name"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    private final RowMapper<UserSshKey> sshKeyMapper = (rs, rowNum) -> new UserSshKey(
            new SshKeyId(rs.getString("ssh_key_id")),
            new UserId(rs.getString("user_id")),
            rs.getString("name"),
            rs.getString("fingerprint"),
            rs.getString("encrypted_private_key"),
            rs.getString("encrypted_aes_key"),
            rs.getString("encryption_nonce"),
            instant(rs, "created_at"));

    public JdbcConfigurationManagementRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public List<ApplicationDefinition> findApplications(Boolean enabledOnly) {
        if (Boolean.TRUE.equals(enabledOnly)) {
            return jdbcClient.sql("""
                            select app_id, app_name, enabled, created_at, updated_at
                            from applications
                            where enabled = true
                            order by app_name, app_id
                            """)
                    .query(applicationMapper)
                    .list();
        }
        return jdbcClient.sql("""
                        select app_id, app_name, enabled, created_at, updated_at
                        from applications
                        order by app_name, app_id
                        """)
                .query(applicationMapper)
                .list();
    }

    @Override
    public Optional<ApplicationDefinition> findApplication(ApplicationId appId) {
        return jdbcClient.sql("""
                        select app_id, app_name, enabled, created_at, updated_at
                        from applications
                        where app_id = :appId
                        """)
                .param("appId", appId.value())
                .query(applicationMapper)
                .optional();
    }

    @Override
    public List<ApplicationDefinition> findApplicationsByMember(UserId userId) {
        return jdbcClient.sql("""
                        select a.app_id, a.app_name, a.enabled, a.created_at, a.updated_at
                        from applications a
                        join application_members m on m.app_id = a.app_id
                        where m.user_id = :userId  and a.enabled = true
                        order by a.app_name, a.app_id
                        """)
                .param("userId", userId.value())
                .query(applicationMapper)
                .list();
    }

    @Override
    public boolean isActiveMember(ApplicationId appId, UserId userId) {
        Long count = jdbcClient.sql("""
                        select count(*)
                        from application_members
                        where app_id = :appId and user_id = :userId and deleted_at is null
                        """)
                .param("appId", appId.value())
                .param("userId", userId.value())
                .query(Long.class)
                .single();
        return count > 0;
    }

    @Override
    public List<ApplicationMember> findActiveMembers(ApplicationId appId) {
        return jdbcClient.sql("""
                        select app_id, user_id, created_at, updated_at, deleted_at
                        from application_members
                        where app_id = :appId and deleted_at is null
                        order by created_at, user_id
                        """)
                .param("appId", appId.value())
                .query(memberMapper)
                .list();
    }

    @Override
    public void saveMember(ApplicationMember member) {
        long existing = jdbcClient.sql("""
                        select count(*) from application_members
                        where app_id = :appId and user_id = :userId
                        """)
                .param("appId", member.appId().value())
                .param("userId", member.userId().value())
                .query(Long.class)
                .single();
        if (existing > 0) {
            jdbcClient.sql("""
                            update application_members
                            set updated_at = :updatedAt, deleted_at = null
                            where app_id = :appId and user_id = :userId
                            """)
                    .param("appId", member.appId().value())
                    .param("userId", member.userId().value())
                    .param("updatedAt", timestamp(member.updatedAt()))
                    .update();
            return;
        }
        jdbcClient.sql("""
                        insert into application_members(app_id, user_id, created_at, updated_at, deleted_at)
                        values (:appId, :userId, :createdAt, :updatedAt, :deletedAt)
                        """)
                .param("appId", member.appId().value())
                .param("userId", member.userId().value())
                .param("createdAt", timestamp(member.createdAt()))
                .param("updatedAt", timestamp(member.updatedAt()))
                .param("deletedAt", member.deletedAt() == null ? null : timestamp(member.deletedAt()))
                .update();
    }

    @Override
    public void deleteMember(ApplicationId appId, UserId userId) {
        jdbcClient.sql("""
                        update application_members
                        set deleted_at = current_timestamp, updated_at = current_timestamp
                        where app_id = :appId and user_id = :userId and deleted_at is null
                        """)
                .param("appId", appId.value())
                .param("userId", userId.value())
                .update();
    }

    @Override
    public PageResponse<CodeRepository> findRepositories(PageRequest pageRequest) {
        List<CodeRepository> items = jdbcClient.sql("""
                        select repository_id, git_url, name, english_name, standard, created_at, updated_at
                        from code_repositories
                        order by updated_at desc, repository_id
                        limit :limit offset :offset
                        """)
                .param("limit", pageRequest.size())
                .param("offset", pageRequest.offset())
                .query(repositoryMapper)
                .list();
        long total = jdbcClient.sql("select count(*) from code_repositories").query(Long.class).single();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), total);
    }

    @Override
    public Optional<CodeRepository> findRepository(CodeRepositoryId repositoryId) {
        return jdbcClient.sql("""
                        select repository_id, git_url, name, english_name, standard, created_at, updated_at
                        from code_repositories
                        where repository_id = :repositoryId
                        """)
                .param("repositoryId", repositoryId.value())
                .query(repositoryMapper)
                .optional();
    }

    @Override
    public Optional<CodeRepository> findRepositoryByGitUrl(String gitUrl) {
        return jdbcClient.sql("""
                        select repository_id, git_url, name, english_name, standard, created_at, updated_at
                        from code_repositories
                        where git_url = :gitUrl
                        """)
                .param("gitUrl", gitUrl)
                .query(repositoryMapper)
                .optional();
    }

    @Override
    public Optional<CodeRepository> findRepositoryByEnglishName(String englishName) {
        return jdbcClient.sql("""
                        select repository_id, git_url, name, english_name, standard, created_at, updated_at
                        from code_repositories
                        where english_name = :englishName
                        """)
                .param("englishName", englishName)
                .query(repositoryMapper)
                .optional();
    }

    @Override
    public CodeRepository saveRepository(CodeRepository repository) {
        jdbcClient.sql("""
                        insert into code_repositories(repository_id, git_url, name, english_name, standard, created_at, updated_at)
                        values (:repositoryId, :gitUrl, :name, :englishName, :standard, :createdAt, :updatedAt)
                        """)
                .param("repositoryId", repository.repositoryId().value())
                .param("gitUrl", repository.gitUrl())
                .param("name", repository.name())
                .param("englishName", repository.englishName())
                .param("standard", repository.standard())
                .param("createdAt", timestamp(repository.createdAt()))
                .param("updatedAt", timestamp(repository.updatedAt()))
                .update();
        return repository;
    }

    @Override
    public CodeRepository updateRepositoryMetadata(CodeRepository repository) {
        jdbcClient.sql("""
                        update code_repositories
                        set name = :name, english_name = :englishName, standard = :standard, updated_at = :updatedAt
                        where repository_id = :repositoryId
                        """)
                .param("repositoryId", repository.repositoryId().value())
                .param("name", repository.name())
                .param("englishName", repository.englishName())
                .param("standard", repository.standard())
                .param("updatedAt", timestamp(repository.updatedAt()))
                .update();
        return repository;
    }

    @Override
    public List<CodeRepository> findRepositoriesByApplication(ApplicationId appId) {
        return jdbcClient.sql("""
                        select r.repository_id, r.git_url, r.name, r.english_name, r.standard, r.created_at, r.updated_at
                        from code_repositories r
                        join application_repository_links l on l.repository_id = r.repository_id
                        where l.app_id = :appId
                        order by r.name, r.repository_id
                        """)
                .param("appId", appId.value())
                .query(repositoryMapper)
                .list();
    }

    @Override
    public List<ApplicationDefinition> findApplicationsByRepository(CodeRepositoryId repositoryId) {
        return jdbcClient.sql("""
                        select a.app_id, a.app_name, a.enabled, a.created_at, a.updated_at
                        from applications a
                        join application_repository_links l on l.app_id = a.app_id
                        where l.repository_id = :repositoryId
                        order by a.app_name, a.app_id
                        """)
                .param("repositoryId", repositoryId.value())
                .query(applicationMapper)
                .list();
    }

    @Override
    public void linkRepository(ApplicationId appId, CodeRepositoryId repositoryId) {
        long existing = jdbcClient.sql("""
                        select count(*) from application_repository_links
                        where app_id = :appId and repository_id = :repositoryId
                        """)
                .param("appId", appId.value())
                .param("repositoryId", repositoryId.value())
                .query(Long.class)
                .single();
        if (existing > 0) {
            return;
        }
        jdbcClient.sql("""
                        insert into application_repository_links(app_id, repository_id, created_at)
                        values (:appId, :repositoryId, current_timestamp)
                        """)
                .param("appId", appId.value())
                .param("repositoryId", repositoryId.value())
                .update();
    }

    @Override
    public void unlinkRepository(ApplicationId appId, CodeRepositoryId repositoryId) {
        jdbcClient.sql("""
                        delete from application_repository_links
                        where app_id = :appId and repository_id = :repositoryId
                        """)
                .param("appId", appId.value())
                .param("repositoryId", repositoryId.value())
                .update();
    }

    @Override
    public List<ApplicationWorkspace> findWorkspaces(ApplicationId appId) {
        return jdbcClient.sql("""
                        select workspace_id, app_id, repository_id, branch, directory_path, workspace_name, created_at, updated_at
                        from application_workspaces
                        where app_id = :appId
                        order by updated_at desc, workspace_id
                        """)
                .param("appId", appId.value())
                .query(workspaceMapper)
                .list();
    }

    @Override
    public Optional<ApplicationWorkspace> findWorkspace(ApplicationWorkspaceId workspaceId) {
        return jdbcClient.sql("""
                        select workspace_id, app_id, repository_id, branch, directory_path, workspace_name, created_at, updated_at
                        from application_workspaces
                        where workspace_id = :workspaceId
                        """)
                .param("workspaceId", workspaceId.value())
                .query(workspaceMapper)
                .optional();
    }

    @Override
    public Optional<ApplicationWorkspace> findWorkspaceByLocation(ApplicationId appId, CodeRepositoryId repositoryId, String branch, String directoryPath) {
        return jdbcClient.sql("""
                        select workspace_id, app_id, repository_id, branch, directory_path, workspace_name, created_at, updated_at
                        from application_workspaces
                        where app_id = :appId and repository_id = :repositoryId and branch = :branch and directory_path = :directoryPath
                        """)
                .param("appId", appId.value())
                .param("repositoryId", repositoryId.value())
                .param("branch", branch)
                .param("directoryPath", directoryPath)
                .query(workspaceMapper)
                .optional();
    }

    @Override
    public ApplicationWorkspace saveWorkspace(ApplicationWorkspace workspace) {
        jdbcClient.sql("""
                        insert into application_workspaces(
                            workspace_id, app_id, repository_id, branch, directory_path, workspace_name, created_at, updated_at
                        )
                        values (
                            :workspaceId, :appId, :repositoryId, :branch, :directoryPath, :workspaceName, :createdAt, :updatedAt
                        )
                        """)
                .param("workspaceId", workspace.workspaceId().value())
                .param("appId", workspace.appId().value())
                .param("repositoryId", workspace.repositoryId().value())
                .param("branch", workspace.branch())
                .param("directoryPath", workspace.directoryPath())
                .param("workspaceName", workspace.workspaceName())
                .param("createdAt", timestamp(workspace.createdAt()))
                .param("updatedAt", timestamp(workspace.updatedAt()))
                .update();
        return workspace;
    }

    @Override
    public ApplicationWorkspace updateWorkspace(ApplicationWorkspace workspace) {
        jdbcClient.sql("""
                        update application_workspaces
                        set workspace_name = :workspaceName, updated_at = :updatedAt
                        where workspace_id = :workspaceId
                        """)
                .param("workspaceId", workspace.workspaceId().value())
                .param("workspaceName", workspace.workspaceName())
                .param("updatedAt", timestamp(workspace.updatedAt()))
                .update();
        return workspace;
    }

    @Override
    public void deleteWorkspace(ApplicationWorkspaceId workspaceId) {
        jdbcClient.sql("delete from application_workspaces where workspace_id = :workspaceId")
                .param("workspaceId", workspaceId.value())
                .update();
    }

    @Override
    public List<UserSshKey> findSshKeys(UserId userId) {
        return jdbcClient.sql("""
                        select ssh_key_id, user_id, name, fingerprint, encrypted_private_key, encrypted_aes_key, encryption_nonce, created_at
                        from user_ssh_keys
                        where user_id = :userId
                        order by created_at desc
                        """)
                .param("userId", userId.value())
                .query(sshKeyMapper)
                .list();
    }

    @Override
    public Optional<UserSshKey> findSshKey(UserId userId, SshKeyId sshKeyId) {
        return jdbcClient.sql("""
                        select ssh_key_id, user_id, name, fingerprint, encrypted_private_key, encrypted_aes_key, encryption_nonce, created_at
                        from user_ssh_keys
                        where user_id = :userId and ssh_key_id = :sshKeyId
                        """)
                .param("userId", userId.value())
                .param("sshKeyId", sshKeyId.value())
                .query(sshKeyMapper)
                .optional();
    }

    @Override
    public UserSshKey saveSshKey(UserSshKey sshKey) {
        jdbcClient.sql("""
                        insert into user_ssh_keys(
                            ssh_key_id, user_id, name, fingerprint, encrypted_private_key, encrypted_aes_key, encryption_nonce, created_at
                        )
                        values (
                            :sshKeyId, :userId, :name, :fingerprint, :encryptedPrivateKey, :encryptedAesKey, :encryptionNonce, :createdAt
                        )
                        """)
                .param("sshKeyId", sshKey.sshKeyId().value())
                .param("userId", sshKey.userId().value())
                .param("name", sshKey.name())
                .param("fingerprint", sshKey.fingerprint())
                .param("encryptedPrivateKey", sshKey.encryptedPrivateKey())
                .param("encryptedAesKey", sshKey.encryptedAesKey())
                .param("encryptionNonce", sshKey.encryptionNonce())
                .param("createdAt", timestamp(sshKey.createdAt()))
                .update();
        return sshKey;
    }

    @Override
    public void deleteSshKey(UserId userId, SshKeyId sshKeyId) {
        jdbcClient.sql("delete from user_ssh_keys where user_id = :userId and ssh_key_id = :sshKeyId")
                .param("userId", userId.value())
                .param("sshKeyId", sshKeyId.value())
                .update();
    }
}
