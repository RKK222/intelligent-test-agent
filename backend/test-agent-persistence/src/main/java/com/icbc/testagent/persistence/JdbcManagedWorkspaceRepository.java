package com.icbc.testagent.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.icbc.testagent.domain.configuration.ApplicationId;
import com.icbc.testagent.domain.configuration.ApplicationWorkspaceId;
import com.icbc.testagent.domain.configuration.CodeRepositoryId;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.icbc.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.icbc.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.icbc.testagent.domain.managedworkspace.ManagedWorkspaceStatus;
import com.icbc.testagent.domain.managedworkspace.PersonalWorkspace;
import com.icbc.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.icbc.testagent.domain.managedworkspace.UserWorkspaceBranchPreference;
import com.icbc.testagent.domain.managedworkspace.UserWorkspacePreference;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncDirection;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncRecord;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncRecordId;
import com.icbc.testagent.domain.managedworkspace.WorkspaceSyncStatus;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.workspace.WorkspaceId;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 托管工作区 JDBC Repository，映射版本工作区、个人工作区、最近使用和同步审计表。
 */
@Repository
public class JdbcManagedWorkspaceRepository extends JdbcRepositorySupport implements ManagedWorkspaceRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    private final RowMapper<ApplicationWorkspaceVersion> versionMapper = (rs, rowNum) -> new ApplicationWorkspaceVersion(
            new ApplicationWorkspaceVersionId(rs.getString("version_id")),
            new ApplicationWorkspaceId(rs.getString("application_workspace_id")),
            new ApplicationId(rs.getString("app_id")),
            new CodeRepositoryId(rs.getString("repository_id")),
            rs.getString("version"),
            rs.getString("branch"),
            rs.getString("repo_root_path"),
            rs.getString("workspace_root_path"),
            new WorkspaceId(rs.getString("runtime_workspace_id")),
            new UserId(rs.getString("created_by_user_id")),
            ManagedWorkspaceStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    private final RowMapper<PersonalWorkspace> personalMapper = (rs, rowNum) -> new PersonalWorkspace(
            new PersonalWorkspaceId(rs.getString("personal_workspace_id")),
            new ApplicationWorkspaceVersionId(rs.getString("app_workspace_version_id")),
            new ApplicationId(rs.getString("app_id")),
            new ApplicationWorkspaceId(rs.getString("application_workspace_id")),
            new UserId(rs.getString("user_id")),
            rs.getString("workspace_name"),
            rs.getString("branch"),
            rs.getString("repo_root_path"),
            rs.getString("workspace_root_path"),
            new WorkspaceId(rs.getString("runtime_workspace_id")),
            rs.getString("base_commit"),
            ManagedWorkspaceStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    private final RowMapper<UserWorkspacePreference> globalPreferenceMapper = (rs, rowNum) -> new UserWorkspacePreference(
            new UserId(rs.getString("user_id")),
            null,
            new WorkspaceId(rs.getString("workspace_id")),
            instant(rs, "updated_at"));

    private final RowMapper<UserWorkspacePreference> applicationPreferenceMapper = (rs, rowNum) -> new UserWorkspacePreference(
            new UserId(rs.getString("user_id")),
            new ApplicationId(rs.getString("app_id")),
            new WorkspaceId(rs.getString("workspace_id")),
            instant(rs, "updated_at"));

    private final RowMapper<UserWorkspaceBranchPreference> branchPreferenceMapper = (rs, rowNum) -> new UserWorkspaceBranchPreference(
            new UserId(rs.getString("user_id")),
            new ApplicationId(rs.getString("app_id")),
            new WorkspaceId(rs.getString("workspace_id")),
            rs.getString("branch"),
            instant(rs, "updated_at"));

    public JdbcManagedWorkspaceRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<ApplicationWorkspaceVersion> findVersions(ApplicationWorkspaceId applicationWorkspaceId) {
        return jdbcClient.sql("""
                        select version_id, application_workspace_id, app_id, repository_id, version, branch,
                               repo_root_path, workspace_root_path, runtime_workspace_id, created_by_user_id,
                               status, created_at, updated_at
                        from application_workspace_versions
                        where application_workspace_id = :applicationWorkspaceId
                        order by version desc, updated_at desc
                        """)
                .param("applicationWorkspaceId", applicationWorkspaceId.value())
                .query(versionMapper)
                .list();
    }

    @Override
    public List<ApplicationWorkspaceVersion> findVersionsByApplication(ApplicationId appId) {
        return jdbcClient.sql("""
                        select version_id, application_workspace_id, app_id, repository_id, version, branch,
                               repo_root_path, workspace_root_path, runtime_workspace_id, created_by_user_id,
                               status, created_at, updated_at
                        from application_workspace_versions
                        where app_id = :appId
                        order by updated_at desc
                        """)
                .param("appId", appId.value())
                .query(versionMapper)
                .list();
    }

    @Override
    public Optional<ApplicationWorkspaceVersion> findVersion(ApplicationWorkspaceVersionId versionId) {
        return jdbcClient.sql(versionSelect("where version_id = :versionId"))
                .param("versionId", versionId.value())
                .query(versionMapper)
                .optional();
    }

    @Override
    public Optional<ApplicationWorkspaceVersion> findVersionByTemplateAndVersion(ApplicationWorkspaceId applicationWorkspaceId, String version) {
        return jdbcClient.sql(versionSelect("where application_workspace_id = :applicationWorkspaceId and version = :version"))
                .param("applicationWorkspaceId", applicationWorkspaceId.value())
                .param("version", version)
                .query(versionMapper)
                .optional();
    }

    @Override
    public ApplicationWorkspaceVersion saveVersion(ApplicationWorkspaceVersion version) {
        jdbcClient.sql("""
                        insert into application_workspace_versions(
                            version_id, application_workspace_id, app_id, repository_id, version, branch,
                            repo_root_path, workspace_root_path, runtime_workspace_id, created_by_user_id,
                            status, created_at, updated_at
                        )
                        values (
                            :versionId, :applicationWorkspaceId, :appId, :repositoryId, :version, :branch,
                            :repoRootPath, :workspaceRootPath, :runtimeWorkspaceId, :createdByUserId,
                            :status, :createdAt, :updatedAt
                        )
                        """)
                .param("versionId", version.versionId().value())
                .param("applicationWorkspaceId", version.applicationWorkspaceId().value())
                .param("appId", version.appId().value())
                .param("repositoryId", version.repositoryId().value())
                .param("version", version.version())
                .param("branch", version.branch())
                .param("repoRootPath", version.repoRootPath())
                .param("workspaceRootPath", version.workspaceRootPath())
                .param("runtimeWorkspaceId", version.runtimeWorkspaceId().value())
                .param("createdByUserId", version.createdBy().value())
                .param("status", version.status().name())
                .param("createdAt", timestamp(version.createdAt()))
                .param("updatedAt", timestamp(version.updatedAt()))
                .update();
        return version;
    }

    @Override
    public List<PersonalWorkspace> findPersonalWorkspaces(ApplicationWorkspaceVersionId versionId, UserId userId) {
        return jdbcClient.sql(personalSelect("where app_workspace_version_id = :versionId and user_id = :userId order by updated_at desc"))
                .param("versionId", versionId.value())
                .param("userId", userId.value())
                .query(personalMapper)
                .list();
    }

    @Override
    public Optional<PersonalWorkspace> findPersonalWorkspace(PersonalWorkspaceId personalWorkspaceId) {
        return jdbcClient.sql(personalSelect("where personal_workspace_id = :personalWorkspaceId"))
                .param("personalWorkspaceId", personalWorkspaceId.value())
                .query(personalMapper)
                .optional();
    }

    @Override
    public Optional<PersonalWorkspace> findPersonalWorkspaceByRuntimeWorkspace(WorkspaceId workspaceId) {
        return jdbcClient.sql(personalSelect("where runtime_workspace_id = :workspaceId"))
                .param("workspaceId", workspaceId.value())
                .query(personalMapper)
                .optional();
    }

    @Override
    public PersonalWorkspace savePersonalWorkspace(PersonalWorkspace workspace) {
        jdbcClient.sql("""
                        insert into personal_workspaces(
                            personal_workspace_id, app_workspace_version_id, app_id, application_workspace_id,
                            user_id, workspace_name, branch, repo_root_path, workspace_root_path,
                            runtime_workspace_id, base_commit, status, created_at, updated_at
                        )
                        values (
                            :personalWorkspaceId, :versionId, :appId, :applicationWorkspaceId,
                            :userId, :workspaceName, :branch, :repoRootPath, :workspaceRootPath,
                            :runtimeWorkspaceId, :baseCommit, :status, :createdAt, :updatedAt
                        )
                        """)
                .param("personalWorkspaceId", workspace.personalWorkspaceId().value())
                .param("versionId", workspace.versionId().value())
                .param("appId", workspace.appId().value())
                .param("applicationWorkspaceId", workspace.applicationWorkspaceId().value())
                .param("userId", workspace.userId().value())
                .param("workspaceName", workspace.workspaceName())
                .param("branch", workspace.branch())
                .param("repoRootPath", workspace.repoRootPath())
                .param("workspaceRootPath", workspace.workspaceRootPath())
                .param("runtimeWorkspaceId", workspace.runtimeWorkspaceId().value())
                .param("baseCommit", workspace.baseCommit())
                .param("status", workspace.status().name())
                .param("createdAt", timestamp(workspace.createdAt()))
                .param("updatedAt", timestamp(workspace.updatedAt()))
                .update();
        return workspace;
    }

    @Override
    public Optional<ApplicationWorkspaceVersion> findVersionByRuntimeWorkspace(WorkspaceId workspaceId) {
        return jdbcClient.sql(versionSelect("where runtime_workspace_id = :workspaceId"))
                .param("workspaceId", workspaceId.value())
                .query(versionMapper)
                .optional();
    }

    @Override
    public void savePreference(UserWorkspacePreference preference) {
        if (preference.appId() == null) {
            saveGlobalPreference(preference);
        } else {
            saveApplicationPreference(preference);
        }
    }

    @Override
    public Optional<UserWorkspacePreference> findGlobalPreference(UserId userId) {
        return jdbcClient.sql("""
                        select user_id, workspace_id, updated_at
                        from user_global_workspace_preferences
                        where user_id = :userId
                        """)
                .param("userId", userId.value())
                .query(globalPreferenceMapper)
                .optional();
    }

    @Override
    public Optional<UserWorkspacePreference> findApplicationPreference(UserId userId, ApplicationId appId) {
        return jdbcClient.sql("""
                        select user_id, app_id, workspace_id, updated_at
                        from user_application_workspace_preferences
                        where user_id = :userId and app_id = :appId
                        """)
                .param("userId", userId.value())
                .param("appId", appId.value())
                .query(applicationPreferenceMapper)
                .optional();
    }

    @Override
    public void saveBranchPreference(UserWorkspaceBranchPreference preference) {
        // (user_id, app_id, workspace_id) 唯一键：命中即更新 branch 与 updated_at，未命中则插入新行。
        // 写策略与 user_application_workspace_preferences.saveApplicationPreference 保持一致。
        if (findBranchPreference(preference.userId(), preference.appId(), preference.workspaceId()).isPresent()) {
            jdbcClient.sql("""
                            update user_workspace_branch_preferences
                            set branch = :branch, updated_at = :updatedAt
                            where user_id = :userId and app_id = :appId and workspace_id = :workspaceId
                            """)
                    .param("userId", preference.userId().value())
                    .param("appId", preference.appId().value())
                    .param("workspaceId", preference.workspaceId().value())
                    .param("branch", preference.branch())
                    .param("updatedAt", timestamp(preference.updatedAt()))
                    .update();
            return;
        }
        jdbcClient.sql("""
                        insert into user_workspace_branch_preferences(
                            user_id, app_id, workspace_id, branch, updated_at
                        )
                        values (
                            :userId, :appId, :workspaceId, :branch, :updatedAt
                        )
                        """)
                .param("userId", preference.userId().value())
                .param("appId", preference.appId().value())
                .param("workspaceId", preference.workspaceId().value())
                .param("branch", preference.branch())
                .param("updatedAt", timestamp(preference.updatedAt()))
                .update();
    }

    @Override
    public Optional<UserWorkspaceBranchPreference> findBranchPreference(UserId userId, ApplicationId appId, WorkspaceId workspaceId) {
        return jdbcClient.sql("""
                        select user_id, app_id, workspace_id, branch, updated_at
                        from user_workspace_branch_preferences
                        where user_id = :userId and app_id = :appId and workspace_id = :workspaceId
                        """)
                .param("userId", userId.value())
                .param("appId", appId.value())
                .param("workspaceId", workspaceId.value())
                .query(branchPreferenceMapper)
                .optional();
    }

    @Override
    public void saveSyncRecord(WorkspaceSyncRecord record) {
        jdbcClient.sql("""
                        insert into workspace_sync_records(
                            sync_record_id, user_id, source_workspace_id, target_workspace_id,
                            direction, files_json, force, status, trace_id, created_at
                        )
                        values (
                            :syncRecordId, :userId, :sourceWorkspaceId, :targetWorkspaceId,
                            :direction, :filesJson, :force, :status, :traceId, :createdAt
                        )
                        """)
                .param("syncRecordId", record.syncRecordId().value())
                .param("userId", record.userId().value())
                .param("sourceWorkspaceId", record.sourceWorkspaceId().value())
                .param("targetWorkspaceId", record.targetWorkspaceId().value())
                .param("direction", record.direction().name())
                .param("filesJson", filesJson(record.files()))
                .param("force", record.force())
                .param("status", record.status().name())
                .param("traceId", record.traceId())
                .param("createdAt", timestamp(record.createdAt()))
                .update();
    }

    private void saveGlobalPreference(UserWorkspacePreference preference) {
        if (findGlobalPreference(preference.userId()).isPresent()) {
            jdbcClient.sql("""
                            update user_global_workspace_preferences
                            set workspace_id = :workspaceId, updated_at = :updatedAt
                            where user_id = :userId
                            """)
                    .param("userId", preference.userId().value())
                    .param("workspaceId", preference.workspaceId().value())
                    .param("updatedAt", timestamp(preference.updatedAt()))
                    .update();
            return;
        }
        jdbcClient.sql("""
                        insert into user_global_workspace_preferences(user_id, workspace_id, updated_at)
                        values (:userId, :workspaceId, :updatedAt)
                        """)
                .param("userId", preference.userId().value())
                .param("workspaceId", preference.workspaceId().value())
                .param("updatedAt", timestamp(preference.updatedAt()))
                .update();
    }

    private void saveApplicationPreference(UserWorkspacePreference preference) {
        if (findApplicationPreference(preference.userId(), preference.appId()).isPresent()) {
            jdbcClient.sql("""
                            update user_application_workspace_preferences
                            set workspace_id = :workspaceId, updated_at = :updatedAt
                            where user_id = :userId and app_id = :appId
                            """)
                    .param("userId", preference.userId().value())
                    .param("appId", preference.appId().value())
                    .param("workspaceId", preference.workspaceId().value())
                    .param("updatedAt", timestamp(preference.updatedAt()))
                    .update();
            return;
        }
        jdbcClient.sql("""
                        insert into user_application_workspace_preferences(user_id, app_id, workspace_id, updated_at)
                        values (:userId, :appId, :workspaceId, :updatedAt)
                        """)
                .param("userId", preference.userId().value())
                .param("appId", preference.appId().value())
                .param("workspaceId", preference.workspaceId().value())
                .param("updatedAt", timestamp(preference.updatedAt()))
                .update();
    }

    private String versionSelect(String whereClause) {
        return """
                select version_id, application_workspace_id, app_id, repository_id, version, branch,
                       repo_root_path, workspace_root_path, runtime_workspace_id, created_by_user_id,
                       status, created_at, updated_at
                from application_workspace_versions
                """ + whereClause;
    }

    private String personalSelect(String whereClause) {
        return """
                select personal_workspace_id, app_workspace_version_id, app_id, application_workspace_id,
                       user_id, workspace_name, branch, repo_root_path, workspace_root_path,
                       runtime_workspace_id, base_commit, status, created_at, updated_at
                from personal_workspaces
                """ + whereClause;
    }

    private String filesJson(List<String> files) {
        try {
            return objectMapper.writeValueAsString(files == null ? List.of() : files);
        } catch (Exception exception) {
            throw new IllegalArgumentException("files_json serialize failed", exception);
        }
    }

    @SuppressWarnings("unused")
    private List<String> filesFromJson(String filesJson) {
        try {
            return objectMapper.readValue(filesJson, STRING_LIST);
        } catch (Exception exception) {
            return List.of();
        }
    }
}
