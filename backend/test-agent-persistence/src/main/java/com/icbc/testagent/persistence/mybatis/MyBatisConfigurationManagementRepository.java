package com.icbc.testagent.persistence.mybatis;

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
import org.springframework.stereotype.Repository;

/**
 * 配置管理 MyBatis Repository，实现应用、版本库、工作空间和 SSH key 领域端口。
 */
@Repository
public class MyBatisConfigurationManagementRepository implements ConfigurationManagementRepository {

    private final ConfigurationManagementMapper mapper;

    /**
     * 注入 MyBatis XML mapper；连接、事务和 SQL 执行由 MyBatis-Spring 管理。
     */
    public MyBatisConfigurationManagementRepository(ConfigurationManagementMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ApplicationDefinition> findApplications(Boolean enabledOnly) {
        return mapper.findApplications(enabledOnly).stream().map(this::toApplication).toList();
    }

    @Override
    public Optional<ApplicationDefinition> findApplication(ApplicationId appId) {
        return Optional.ofNullable(mapper.findApplication(appId.value())).map(this::toApplication);
    }

    @Override
    public List<ApplicationDefinition> findApplicationsByMember(UserId userId) {
        return mapper.findApplicationsByMember(userId.value()).stream().map(this::toApplication).toList();
    }

    @Override
    public boolean isActiveMember(ApplicationId appId, UserId userId) {
        return mapper.countActiveMember(appId.value(), userId.value()) > 0;
    }

    @Override
    public List<ApplicationMember> findActiveMembers(ApplicationId appId) {
        return mapper.findActiveMembers(appId.value()).stream().map(this::toMember).toList();
    }

    @Override
    public void saveMember(ApplicationMember member) {
        if (mapper.countMember(member.appId().value(), member.userId().value()) > 0) {
            mapper.reactivateMember(member.appId().value(), member.userId().value(), member.updatedAt());
            return;
        }
        mapper.insertMember(
                member.appId().value(),
                member.userId().value(),
                member.createdAt(),
                member.updatedAt(),
                member.deletedAt());
    }

    @Override
    public void deleteMember(ApplicationId appId, UserId userId) {
        mapper.deleteMember(appId.value(), userId.value());
    }

    @Override
    public PageResponse<CodeRepository> findRepositories(PageRequest pageRequest) {
        List<CodeRepository> items = mapper.findRepositories(pageRequest.size(), pageRequest.offset()).stream()
                .map(this::toRepository)
                .toList();
        return new PageResponse<>(items, pageRequest.page(), pageRequest.size(), mapper.countRepositories());
    }

    @Override
    public Optional<CodeRepository> findRepository(CodeRepositoryId repositoryId) {
        return Optional.ofNullable(mapper.findRepository(repositoryId.value())).map(this::toRepository);
    }

    @Override
    public Optional<CodeRepository> findRepositoryByGitUrl(String gitUrl) {
        return Optional.ofNullable(mapper.findRepositoryByGitUrl(gitUrl)).map(this::toRepository);
    }

    @Override
    public Optional<CodeRepository> findRepositoryByEnglishName(String englishName) {
        return Optional.ofNullable(mapper.findRepositoryByEnglishName(englishName)).map(this::toRepository);
    }

    @Override
    public CodeRepository saveRepository(CodeRepository repository) {
        mapper.insertRepository(toRow(repository));
        return repository;
    }

    @Override
    public CodeRepository updateRepositoryMetadata(CodeRepository repository) {
        mapper.updateRepositoryMetadata(toRow(repository));
        return repository;
    }

    @Override
    public List<CodeRepository> findRepositoriesByApplication(ApplicationId appId) {
        return mapper.findRepositoriesByApplication(appId.value()).stream().map(this::toRepository).toList();
    }

    @Override
    public List<ApplicationDefinition> findApplicationsByRepository(CodeRepositoryId repositoryId) {
        return mapper.findApplicationsByRepository(repositoryId.value()).stream().map(this::toApplication).toList();
    }

    @Override
    public void linkRepository(ApplicationId appId, CodeRepositoryId repositoryId) {
        if (mapper.countRepositoryLink(appId.value(), repositoryId.value()) == 0) {
            mapper.insertRepositoryLink(appId.value(), repositoryId.value());
        }
    }

    @Override
    public void unlinkRepository(ApplicationId appId, CodeRepositoryId repositoryId) {
        mapper.deleteRepositoryLink(appId.value(), repositoryId.value());
    }

    @Override
    public List<ApplicationWorkspace> findWorkspaces(ApplicationId appId) {
        return mapper.findWorkspaces(appId.value()).stream().map(this::toWorkspace).toList();
    }

    @Override
    public Optional<ApplicationWorkspace> findWorkspace(ApplicationWorkspaceId workspaceId) {
        return Optional.ofNullable(mapper.findWorkspace(workspaceId.value())).map(this::toWorkspace);
    }

    @Override
    public Optional<ApplicationWorkspace> findWorkspaceByLocation(
            ApplicationId appId,
            CodeRepositoryId repositoryId,
            String branch,
            String directoryPath) {
        return Optional.ofNullable(mapper.findWorkspaceByLocation(
                        appId.value(),
                        repositoryId.value(),
                        branch,
                        directoryPath))
                .map(this::toWorkspace);
    }

    @Override
    public Optional<ApplicationWorkspace> findWorkspaceByName(ApplicationId appId, String workspaceName) {
        return Optional.ofNullable(mapper.findWorkspaceByName(appId.value(), workspaceName))
                .map(this::toWorkspace);
    }

    @Override
    public ApplicationWorkspace saveWorkspace(ApplicationWorkspace workspace) {
        mapper.insertWorkspace(toRow(workspace));
        return workspace;
    }

    @Override
    public ApplicationWorkspace updateWorkspace(ApplicationWorkspace workspace) {
        mapper.updateWorkspace(toRow(workspace));
        return workspace;
    }

    @Override
    public void deleteWorkspace(ApplicationWorkspaceId workspaceId) {
        mapper.deleteWorkspace(workspaceId.value());
    }

    @Override
    public List<UserSshKey> findSshKeys(UserId userId) {
        return mapper.findSshKeys(userId.value()).stream().map(this::toSshKey).toList();
    }

    @Override
    public Optional<UserSshKey> findSshKey(UserId userId, SshKeyId sshKeyId) {
        return Optional.ofNullable(mapper.findSshKey(userId.value(), sshKeyId.value())).map(this::toSshKey);
    }

    @Override
    public UserSshKey saveSshKey(UserSshKey sshKey) {
        mapper.insertSshKey(toRow(sshKey));
        return sshKey;
    }

    @Override
    public void deleteSshKey(UserId userId, SshKeyId sshKeyId) {
        mapper.deleteSshKey(userId.value(), sshKeyId.value());
    }

    private ApplicationDefinition toApplication(ApplicationDefinitionRow row) {
        return new ApplicationDefinition(
                new ApplicationId(row.appId()),
                row.appName(),
                row.enabled(),
                row.createdAt(),
                row.updatedAt());
    }

    private ApplicationMember toMember(ApplicationMemberRow row) {
        return new ApplicationMember(
                new ApplicationId(row.appId()),
                new UserId(row.userId()),
                row.createdAt(),
                row.updatedAt(),
                row.deletedAt());
    }

    private CodeRepository toRepository(CodeRepositoryRow row) {
        return new CodeRepository(
                new CodeRepositoryId(row.repositoryId()),
                row.gitUrl(),
                row.name(),
                row.englishName(),
                row.repositoryType(),
                row.deploymentMode(),
                row.standard(),
                row.createdAt(),
                row.updatedAt());
    }

    private CodeRepositoryRow toRow(CodeRepository repository) {
        return new CodeRepositoryRow(
                repository.repositoryId().value(),
                repository.gitUrl(),
                repository.name(),
                repository.englishName(),
                repository.repositoryType(),
                repository.deploymentMode(),
                repository.standard(),
                repository.createdAt(),
                repository.updatedAt());
    }

    private ApplicationWorkspace toWorkspace(ApplicationWorkspaceRow row) {
        return new ApplicationWorkspace(
                new ApplicationWorkspaceId(row.workspaceId()),
                new ApplicationId(row.appId()),
                new CodeRepositoryId(row.repositoryId()),
                row.branch(),
                row.directoryPath(),
                row.workspaceName(),
                row.createdAt(),
                row.updatedAt());
    }

    private ApplicationWorkspaceRow toRow(ApplicationWorkspace workspace) {
        return new ApplicationWorkspaceRow(
                workspace.workspaceId().value(),
                workspace.appId().value(),
                workspace.repositoryId().value(),
                workspace.branch(),
                workspace.directoryPath(),
                workspace.workspaceName(),
                workspace.createdAt(),
                workspace.updatedAt());
    }

    private UserSshKey toSshKey(UserSshKeyRow row) {
        return new UserSshKey(
                new SshKeyId(row.sshKeyId()),
                new UserId(row.userId()),
                row.name(),
                row.fingerprint(),
                row.encryptedPrivateKey(),
                row.encryptedAesKey(),
                row.encryptionNonce(),
                row.createdAt());
    }

    private UserSshKeyRow toRow(UserSshKey sshKey) {
        return new UserSshKeyRow(
                sshKey.sshKeyId().value(),
                sshKey.userId().value(),
                sshKey.name(),
                sshKey.fingerprint(),
                sshKey.encryptedPrivateKey(),
                sshKey.encryptedAesKey(),
                sshKey.encryptionNonce(),
                sshKey.createdAt());
    }
}
