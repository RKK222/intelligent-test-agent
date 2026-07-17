package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.user.UserId;
import java.util.List;
import java.util.Optional;

/**
 * 配置管理持久化端口，供配置业务模块访问数据库实现。
 */
public interface ConfigurationManagementRepository {

    List<ApplicationDefinition> findApplications(Boolean enabledOnly);

    Optional<ApplicationDefinition> findApplication(ApplicationId appId);

    default ApplicationDefinition saveApplication(ApplicationDefinition application) {
        throw new UnsupportedOperationException("saveApplication is not implemented");
    }

    List<ApplicationDefinition> findApplicationsByMember(UserId userId);

    boolean isActiveMember(ApplicationId appId, UserId userId);

    List<ApplicationMember> findActiveMembers(ApplicationId appId);

    void saveMember(ApplicationMember member);

    void deleteMember(ApplicationId appId, UserId userId);

    PageResponse<CodeRepository> findRepositories(PageRequest pageRequest);

    Optional<CodeRepository> findRepository(CodeRepositoryId repositoryId);

    Optional<CodeRepository> findRepositoryByGitUrl(String gitUrl);

    Optional<CodeRepository> findRepositoryByEnglishName(String englishName);

    CodeRepository saveRepository(CodeRepository repository);

    CodeRepository updateRepositoryMetadata(CodeRepository repository);

    List<CodeRepository> findRepositoriesByApplication(ApplicationId appId);

    List<ApplicationDefinition> findApplicationsByRepository(CodeRepositoryId repositoryId);

    void linkRepository(ApplicationId appId, CodeRepositoryId repositoryId);

    void unlinkRepository(ApplicationId appId, CodeRepositoryId repositoryId);

    List<ApplicationWorkspace> findWorkspaces(ApplicationId appId);

    Optional<ApplicationWorkspace> findWorkspace(ApplicationWorkspaceId workspaceId);

    Optional<ApplicationWorkspace> findWorkspaceByLocation(ApplicationId appId, CodeRepositoryId repositoryId, String branch, String directoryPath);

    Optional<ApplicationWorkspace> findWorkspaceByName(ApplicationId appId, String workspaceName);

    ApplicationWorkspace saveWorkspace(ApplicationWorkspace workspace);

    ApplicationWorkspace updateWorkspace(ApplicationWorkspace workspace);

    void deleteWorkspace(ApplicationWorkspaceId workspaceId);

    List<UserSshKey> findSshKeys(UserId userId);

    Optional<UserSshKey> findSshKey(UserId userId, SshKeyId sshKeyId);

    UserSshKey saveSshKey(UserSshKey sshKey);

    void deleteSshKey(UserId userId, SshKeyId sshKeyId);
}
