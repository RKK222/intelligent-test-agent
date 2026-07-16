package com.enterprise.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitRemoteService;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspace;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryDeploymentMode;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.SshKeyId;
import com.enterprise.testagent.domain.configuration.UserSshKey;
import com.enterprise.testagent.domain.dictionary.DictId;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.dictionary.DictionaryRepository;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import com.enterprise.testagent.domain.user.UserStatus;
import com.enterprise.testagent.domain.run.ConversationContextStore;
import com.enterprise.testagent.domain.run.ConversationContextUserMutation;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ConfigurationManagementApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-23T00:00:00Z");
    private static final String PRIVATE_KEY = "-----BEGIN OPENSSH PRIVATE KEY-----\nsecret\n-----END OPENSSH PRIVATE KEY-----\n";

    private final SshKeyTestFixtures sshKeyFixtures = new SshKeyTestFixtures();

    @Test
    void createApplicationNormalizesAndPersistsEnabledDefinition() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        ConfigurationManagementApplicationService service = new ConfigurationManagementApplicationService(
                repository,
                repositoryTypeDictionaryRepository(),
                org.mockito.Mockito.mock(UserRepository.class),
                createTestCacheService(),
                sshKeyFixtures.encryptionService(),
                org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));
        when(repository.findApplication(new ApplicationId("F-NEW"))).thenReturn(Optional.empty());
        when(repository.saveApplication(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConfigurationManagementResponses.ApplicationResponse response =
                service.createApplication("  F-NEW  ", "  新应用  ");

        assertThat(response.appId()).isEqualTo("F-NEW");
        assertThat(response.appName()).isEqualTo("新应用");
        assertThat(response.enabled()).isTrue();
        verify(repository).saveApplication(argThat(application ->
                application.enabled()
                        && "F-NEW".equals(application.appId().value())
                        && "新应用".equals(application.appName())));
    }

    @Test
    void createApplicationRejectsDuplicateId() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        ConfigurationManagementApplicationService service = new ConfigurationManagementApplicationService(
                repository,
                repositoryTypeDictionaryRepository(),
                org.mockito.Mockito.mock(UserRepository.class),
                createTestCacheService(),
                sshKeyFixtures.encryptionService(),
                org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));
        when(repository.findApplication(new ApplicationId("F-OLD"))).thenReturn(Optional.of(
                new ApplicationDefinition(new ApplicationId("F-OLD"), "已有应用", true, NOW, NOW)));

        assertThatThrownBy(() -> service.createApplication("F-OLD", "重复应用"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
                .saveApplication(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void removeMemberInvalidatesUserContextsAfterRepositoryDeleteSucceeds() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        ConversationContextStore contextStore = org.mockito.Mockito.mock(ConversationContextStore.class);
        ConversationContextUserMutation mutation = new ConversationContextUserMutation(
                new UserId("usr_1234567890abcdef"),
                "mutation-1");
        org.mockito.Mockito.when(contextStore.beginUserMutation(new UserId("usr_1234567890abcdef")))
                .thenReturn(mutation);
        ConfigurationManagementApplicationService service = new ConfigurationManagementApplicationService(
                repository,
                repositoryTypeDictionaryRepository(),
                org.mockito.Mockito.mock(UserRepository.class),
                createTestCacheService(),
                sshKeyFixtures.encryptionService(),
                org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));
        service.setConversationContextStore(contextStore);

        service.removeMember("app_1234567890abcdef", "usr_1234567890abcdef");

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(repository, contextStore);
        order.verify(contextStore).beginUserMutation(new UserId("usr_1234567890abcdef"));
        order.verify(repository).deleteMember(
                new ApplicationId("app_1234567890abcdef"),
                new UserId("usr_1234567890abcdef"));
        order.verify(contextStore).completeUserMutation(mutation);
    }

    @Test
    void removeMemberDoesNotChangeDatabaseWhenFailClosedInvalidationFails() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        ConversationContextStore contextStore = org.mockito.Mockito.mock(ConversationContextStore.class);
        org.mockito.Mockito.doThrow(new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE))
                .when(contextStore)
                .beginUserMutation(new UserId("usr_1234567890abcdef"));
        ConfigurationManagementApplicationService service = new ConfigurationManagementApplicationService(
                repository,
                repositoryTypeDictionaryRepository(),
                org.mockito.Mockito.mock(UserRepository.class),
                createTestCacheService(),
                sshKeyFixtures.encryptionService(),
                org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));
        service.setConversationContextStore(contextStore);

        assertThatThrownBy(() -> service.removeMember("app_1234567890abcdef", "usr_1234567890abcdef"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.RUNTIME_STATE_UNAVAILABLE));
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.never())
                .deleteMember(org.mockito.Mockito.any(), org.mockito.Mockito.any());
    }

    @Test
    void removeMemberReleasesOnlyItsMutationGateWhenDatabaseWriteFails() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        ConversationContextStore contextStore = org.mockito.Mockito.mock(ConversationContextStore.class);
        UserId userId = new UserId("usr_1234567890abcdef");
        ConversationContextUserMutation mutation = new ConversationContextUserMutation(userId, "mutation-rollback");
        org.mockito.Mockito.when(contextStore.beginUserMutation(userId)).thenReturn(mutation);
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
                .when(repository)
                .deleteMember(new ApplicationId("app_1234567890abcdef"), userId);
        ConfigurationManagementApplicationService service = new ConfigurationManagementApplicationService(
                repository,
                repositoryTypeDictionaryRepository(),
                org.mockito.Mockito.mock(UserRepository.class),
                createTestCacheService(),
                sshKeyFixtures.encryptionService(),
                org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));
        service.setConversationContextStore(contextStore);

        assertThatThrownBy(() -> service.removeMember("app_1234567890abcdef", userId.value()))
                .isInstanceOf(IllegalStateException.class);

        org.mockito.Mockito.verify(contextStore).abortUserMutation(mutation);
        org.mockito.Mockito.verify(contextStore, org.mockito.Mockito.never()).completeUserMutation(mutation);
    }

    @Test
    void createRepositoryNormalizesEnglishNameBeforeSaving() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        ConfigurationManagementApplicationService service = new ConfigurationManagementApplicationService(
                repository,
                repositoryTypeDictionaryRepository(),
                org.mockito.Mockito.mock(UserRepository.class),
                createTestCacheService(),
                sshKeyFixtures.encryptionService(),
                org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));
        when(repository.findRepositoryByGitUrl("https://gitee.com/demo/repo.git")).thenReturn(Optional.empty());
        when(repository.findRepositoryByEnglishName("demo")).thenReturn(Optional.empty());
        when(repository.saveRepository(argThat(saved -> "demo".equals(saved.englishName()))))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConfigurationManagementResponses.CodeRepositoryResponse response = service.createRepository(
                "https://gitee.com/demo/repo.git",
                "演示库",
                "Demo",
                true,
                null);

        assertThat(response.englishName()).isEqualTo("demo");
        assertThat(response.repositoryType()).isEqualTo(CodeRepositoryType.TEST_WORK_REPOSITORY.value());
        assertThat(response.repositoryTypeLabel()).isEqualTo("测试工作库");
        verify(repository).saveRepository(argThat(saved ->
                "demo".equals(saved.englishName())
                        && saved.standard()
                        && CodeRepositoryType.TEST_WORK_REPOSITORY.value().equals(saved.repositoryType())));
    }

    @Test
    void createRepositoryUsesRepositoryTypeAsTheSourceOfLegacyStandard() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        ConfigurationManagementApplicationService service = new ConfigurationManagementApplicationService(
                repository,
                repositoryTypeDictionaryRepository(),
                org.mockito.Mockito.mock(UserRepository.class),
                createTestCacheService(),
                sshKeyFixtures.encryptionService(),
                org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));
        when(repository.findRepositoryByGitUrl("https://gitee.com/demo/asset.git")).thenReturn(Optional.empty());
        when(repository.findRepositoryByEnglishName("asset")).thenReturn(Optional.empty());
        when(repository.saveRepository(argThat(saved -> "asset".equals(saved.englishName()))))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConfigurationManagementResponses.CodeRepositoryResponse response = service.createRepository(
                "https://gitee.com/demo/asset.git",
                "资产库",
                "Asset",
                true,
                CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value());

        assertThat(response.repositoryType()).isEqualTo(CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value());
        assertThat(response.repositoryTypeLabel()).isEqualTo("应用资产库");
        assertThat(response.standard()).isFalse();
        verify(repository).saveRepository(argThat(saved ->
                CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value().equals(saved.repositoryType())
                        && !saved.standard()));
    }

    @Test
    void createInternalRepositoryStoresSuffixAndDerivesEnglishNameFromPath() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        ConfigurationManagementApplicationService service = new ConfigurationManagementApplicationService(
                repository,
                repositoryTypeDictionaryRepository(),
                org.mockito.Mockito.mock(UserRepository.class),
                createTestCacheService(),
                sshKeyFixtures.encryptionService(),
                org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));
        String storedGitUrl = "scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform";
        when(repository.findRepositoryByGitUrl(storedGitUrl)).thenReturn(Optional.empty());
        when(repository.findRepositoryByEnglishName("hzefficiencytools-interfaceplatform")).thenReturn(Optional.empty());
        when(repository.saveRepository(argThat(saved -> CodeRepositoryDeploymentMode.INTERNAL.value().equals(saved.deploymentMode()))))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ConfigurationManagementResponses.CodeRepositoryResponse response = service.createRepository(
                storedGitUrl,
                "接口平台",
                "",
                false,
                CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(),
                CodeRepositoryDeploymentMode.INTERNAL.value());

        assertThat(response.gitUrl()).isEqualTo(storedGitUrl);
        assertThat(response.englishName()).isEqualTo("hzefficiencytools-interfaceplatform");
        assertThat(response.deploymentMode()).isEqualTo(CodeRepositoryDeploymentMode.INTERNAL.value());
        verify(repository).saveRepository(argThat(saved ->
                storedGitUrl.equals(saved.gitUrl())
                        && "hzefficiencytools-interfaceplatform".equals(saved.englishName())
                        && CodeRepositoryDeploymentMode.INTERNAL.value().equals(saved.deploymentMode())));
    }

    @Test
    void listRepositoryTypesReadsOptionsFromDictionary() {
        ConfigurationManagementApplicationService service = new ConfigurationManagementApplicationService(
                org.mockito.Mockito.mock(ConfigurationManagementRepository.class),
                repositoryTypeDictionaryRepository(),
                org.mockito.Mockito.mock(UserRepository.class),
                createTestCacheService(),
                sshKeyFixtures.encryptionService(),
                org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));

        assertThat(service.listRepositoryTypes())
                .extracting(ConfigurationManagementResponses.RepositoryTypeOptionResponse::typeCode)
                .containsExactly(
                        CodeRepositoryType.TEST_WORK_REPOSITORY.value(),
                        CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(),
                        CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value());
    }

    @Test
    void createRepositoryRejectsUnknownRepositoryType() {
        ConfigurationManagementApplicationService service = new ConfigurationManagementApplicationService(
                org.mockito.Mockito.mock(ConfigurationManagementRepository.class),
                repositoryTypeDictionaryRepository(),
                org.mockito.Mockito.mock(UserRepository.class),
                createTestCacheService(),
                sshKeyFixtures.encryptionService(),
                org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));

        assertThatThrownBy(() -> service.createRepository(
                "https://gitee.com/demo/repo.git",
                "演示库",
                "Demo",
                false,
                "UNKNOWN_TYPE"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void repositoryEnglishNameAllowsLowercaseDigitsAndHyphenButRejectsUnsafeShapes() {
        ConfigurationManagementApplicationService service = new ConfigurationManagementApplicationService(
                org.mockito.Mockito.mock(ConfigurationManagementRepository.class),
                repositoryTypeDictionaryRepository(),
                org.mockito.Mockito.mock(UserRepository.class),
                createTestCacheService(),
                sshKeyFixtures.encryptionService(),
                org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));

        assertThatThrownBy(() -> service.createRepository(
                "https://gitee.com/demo/repo.git",
                "演示库",
                "-demo",
                true,
                null))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.createRepository(
                "https://gitee.com/demo/repo.git",
                "演示库",
                "a".repeat(129),
                true,
                null))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void updateRepositoryRejectsDuplicateEnglishName() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        CodeRepository current = codeRepository("https://gitee.com/demo/current.git");
        CodeRepository duplicate = new CodeRepository(
                new CodeRepositoryId("repo_other"),
                "https://gitee.com/demo/other.git",
                "Other",
                "demo",
                false,
                NOW,
                NOW);
        when(repository.findRepository(current.repositoryId())).thenReturn(Optional.of(current));
        when(repository.findRepositoryByEnglishName("demo")).thenReturn(Optional.of(duplicate));
        ConfigurationManagementApplicationService service = new ConfigurationManagementApplicationService(
                repository,
                repositoryTypeDictionaryRepository(),
                org.mockito.Mockito.mock(UserRepository.class),
                createTestCacheService(),
                sshKeyFixtures.encryptionService(),
                org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));

        assertThatThrownBy(() -> service.updateRepository("repo_123", "演示库", "Demo", false))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void renameWorkspaceRejectsDuplicateAliasInSameApplication() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        ApplicationId appId = new ApplicationId("app_1");
        ApplicationWorkspace current = new ApplicationWorkspace(
                new ApplicationWorkspaceId("awp_1"),
                appId,
                new CodeRepositoryId("repo_1"),
                "main",
                "F-COSS/W1",
                "工作空间1",
                NOW,
                NOW);
        ApplicationWorkspace duplicate = new ApplicationWorkspace(
                new ApplicationWorkspaceId("awp_2"),
                appId,
                new CodeRepositoryId("repo_1"),
                "main",
                "F-COSS/W2",
                "ai-test",
                NOW,
                NOW);
        when(repository.findWorkspace(current.workspaceId())).thenReturn(Optional.of(current));
        when(repository.findWorkspaceByName(appId, "ai-test")).thenReturn(Optional.of(duplicate));
        ConfigurationManagementApplicationService service = new ConfigurationManagementApplicationService(
                repository,
                repositoryTypeDictionaryRepository(),
                org.mockito.Mockito.mock(UserRepository.class),
                createTestCacheService(),
                sshKeyFixtures.encryptionService(),
                org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));

        assertThatThrownBy(() -> service.renameWorkspace("app_1", "awp_1", " ai-test "))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void sshRepositoryBranchesUseCurrentUsersStoredPrivateKey() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
        GitRemoteService gitRemoteService = org.mockito.Mockito.mock(GitRemoteService.class);
        SshKeyEncryptionService encryptionService = sshKeyFixtures.encryptionService();
        GitCloneCacheService gitCloneCacheService = createTestCacheService();
        UserSshKey storedKey = sshKeyFixtures.encryptedSshKey(
                new SshKeyId("ssh_123"), new UserId("usr_123"), "work", PRIVATE_KEY, NOW);
        CodeRepository codeRepository = codeRepository("git@gitee.com:demo/repo.git");
        UserId userId = new UserId("usr_123");

        when(repository.findRepository(codeRepository.repositoryId())).thenReturn(java.util.Optional.of(codeRepository));
        when(repository.findSshKeys(userId)).thenReturn(List.of(storedKey));
        when(gitRemoteService.listBranches(eq(codeRepository.gitUrl()), eq(PRIVATE_KEY))).thenReturn(List.of("main"));

        ConfigurationManagementApplicationService service =
                new ConfigurationManagementApplicationService(repository, repositoryTypeDictionaryRepository(), userRepository, gitRemoteService, gitCloneCacheService, encryptionService, org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));

        assertThat(service.listBranches("repo_123", userId)).containsExactly("main");
        verify(gitRemoteService).listBranches(codeRepository.gitUrl(), PRIVATE_KEY);
    }

    @Test
    void internalRepositoryBranchesUseCurrentUnifiedAuthIdInRuntimeGitUrl() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
        GitRemoteService gitRemoteService = org.mockito.Mockito.mock(GitRemoteService.class);
        SshKeyEncryptionService encryptionService = sshKeyFixtures.encryptionService();
        GitCloneCacheService gitCloneCacheService = createTestCacheService();
        UserId userId = new UserId("usr_123");
        UserSshKey storedKey = sshKeyFixtures.encryptedSshKey(
                new SshKeyId("ssh_123"), userId, "work", PRIVATE_KEY, NOW);
        CodeRepository codeRepository = new CodeRepository(
                new CodeRepositoryId("repo_123"),
                "scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform",
                "接口平台",
                "hzefficiencytools-interfaceplatform",
                CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(),
                CodeRepositoryDeploymentMode.INTERNAL.value(),
                false,
                NOW,
                NOW);
        User user = new User(userId, "001177621", "dev", "hash", null, null, null, UserStatus.ACTIVE, NOW, NOW);

        when(repository.findRepository(codeRepository.repositoryId())).thenReturn(java.util.Optional.of(codeRepository));
        when(repository.findSshKeys(userId)).thenReturn(List.of(storedKey));
        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(user));
        when(gitRemoteService.listBranches(
                eq("ssh://001177621@scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform"),
                eq(PRIVATE_KEY))).thenReturn(List.of("main"));

        ConfigurationManagementApplicationService service =
                new ConfigurationManagementApplicationService(repository, repositoryTypeDictionaryRepository(), userRepository, gitRemoteService, gitCloneCacheService, encryptionService, org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));

        assertThat(service.listBranches("repo_123", userId)).containsExactly("main");
        verify(gitRemoteService).listBranches(
                "ssh://001177621@scm-share.sdc.cs.enterprise:29418/hzefficiencytools/interfaceplatform",
                PRIVATE_KEY);
    }

    @Test
    void repositoryTreeFiltersTestWorkRepositoryToCurrentAppRoot() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
        GitRemoteService gitRemoteService = org.mockito.Mockito.mock(GitRemoteService.class);
        SshKeyEncryptionService encryptionService = sshKeyFixtures.encryptionService();
        UserId userId = new UserId("usr_123");
        UserSshKey storedKey = sshKeyFixtures.encryptedSshKey(
                new SshKeyId("ssh_123"), userId, "work", PRIVATE_KEY, NOW);
        ApplicationDefinition app = new ApplicationDefinition(new ApplicationId("app_f_coss"), "F-COSS", true, NOW, NOW);
        CodeRepository codeRepository = new CodeRepository(
                new CodeRepositoryId("repo_123"),
                "git@gitee.com:demo/repo.git",
                "测试库",
                "test-repo",
                CodeRepositoryType.TEST_WORK_REPOSITORY.value(),
                true,
                NOW,
                NOW);
        List<GitRemoteService.RemoteTreeNode> remoteTree = List.of(
                new GitRemoteService.RemoteTreeNode(
                        "F-COSS",
                        "F-COSS",
                        "directory",
                        List.of(new GitRemoteService.RemoteTreeNode(
                                "W1",
                                "F-COSS/W1",
                                "directory",
                                List.of(new GitRemoteService.RemoteTreeNode(
                                        "case.md",
                                        "F-COSS/W1/case.md",
                                        "file",
                                        List.of()))))),
                new GitRemoteService.RemoteTreeNode(
                        "OTHER",
                        "OTHER",
                        "directory",
                        List.of()));

        when(repository.findApplication(app.appId())).thenReturn(Optional.of(app));
        when(repository.findRepository(codeRepository.repositoryId())).thenReturn(Optional.of(codeRepository));
        when(repository.findRepositoriesByApplication(app.appId())).thenReturn(List.of(codeRepository));
        when(repository.findSshKeys(userId)).thenReturn(List.of(storedKey));
        when(gitRemoteService.listTree(codeRepository.gitUrl(), "feature_testagent_20260707", PRIVATE_KEY)).thenReturn(remoteTree);

        ConfigurationManagementApplicationService service =
                new ConfigurationManagementApplicationService(repository, repositoryTypeDictionaryRepository(), userRepository, gitRemoteService, createTestCacheService(), encryptionService, org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));

        ConfigurationManagementResponses.RepositoryTreeResponse response =
                service.listRepositoryTree("app_f_coss", "repo_123", "feature_testagent_20260707", userId);

        assertThat(response.nodes()).singleElement().satisfies(root -> {
            assertThat(root.name()).isEqualTo("F-COSS");
            assertThat(root.children()).singleElement().satisfies(child -> {
                assertThat(child.path()).isEqualTo("F-COSS/W1");
                assertThat(child.children()).singleElement()
                        .extracting(ConfigurationManagementResponses.RepositoryTreeNodeResponse::type)
                        .isEqualTo("file");
            });
        });
    }

    @Test
    void repositoryTreeReturnsAllNodesForNonTestRepository() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
        GitRemoteService gitRemoteService = org.mockito.Mockito.mock(GitRemoteService.class);
        ApplicationDefinition app = new ApplicationDefinition(new ApplicationId("app_f_coss"), "F-COSS", true, NOW, NOW);
        CodeRepository codeRepository = codeRepository("https://gitee.com/demo/repo.git");
        List<GitRemoteService.RemoteTreeNode> remoteTree = List.of(
                new GitRemoteService.RemoteTreeNode("src", "src", "directory", List.of()),
                new GitRemoteService.RemoteTreeNode("README.md", "README.md", "file", List.of()));

        when(repository.findApplication(app.appId())).thenReturn(Optional.of(app));
        when(repository.findRepository(codeRepository.repositoryId())).thenReturn(Optional.of(codeRepository));
        when(repository.findRepositoriesByApplication(app.appId())).thenReturn(List.of(codeRepository));
        when(gitRemoteService.listTree(codeRepository.gitUrl(), "main", null)).thenReturn(remoteTree);

        ConfigurationManagementApplicationService service =
                new ConfigurationManagementApplicationService(repository, repositoryTypeDictionaryRepository(), userRepository, gitRemoteService, createTestCacheService(), sshKeyFixtures.encryptionService(), org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));

        ConfigurationManagementResponses.RepositoryTreeResponse response =
                service.listRepositoryTree("app_f_coss", "repo_123", "main", new UserId("usr_123"));

        assertThat(response.nodes()).extracting(ConfigurationManagementResponses.RepositoryTreeNodeResponse::path)
                .containsExactly("src", "README.md");
    }

    @Test
    void repositoryDeploymentOptionsUseConfiguredDefaultAndCurrentUserPrefix() {
        UserId userId = new UserId("usr_123");
        UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
        when(userRepository.findByUserId(userId)).thenReturn(Optional.of(
                new User(userId, "001177621", "dev", "hash", null, null, null, UserStatus.ACTIVE, NOW, NOW)));
        ConfigurationManagementApplicationService service = new ConfigurationManagementApplicationService(
                org.mockito.Mockito.mock(ConfigurationManagementRepository.class),
                repositoryTypeDictionaryRepository(),
                userRepository,
                org.mockito.Mockito.mock(GitRemoteService.class),
                createTestCacheService(),
                sshKeyFixtures.encryptionService(),
                org.mockito.Mockito.mock(ManagedWorkspaceRepository.class),
                "internal");

        ConfigurationManagementResponses.RepositoryDeploymentOptionsResponse response = service.repositoryDeploymentOptions(userId);

        assertThat(response.defaultDeploymentMode()).isEqualTo(CodeRepositoryDeploymentMode.INTERNAL.value());
        assertThat(response.internalSshPrefix()).isEqualTo("ssh://001177621@");
        assertThat(response.options())
                .extracting(ConfigurationManagementResponses.RepositoryDeploymentOptionResponse::mode)
                .containsExactly(CodeRepositoryDeploymentMode.EXTERNAL.value(), CodeRepositoryDeploymentMode.INTERNAL.value());
    }

    @Test
    void addSshKeyRejectsSecondKeyForSameUser() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        UserId userId = new UserId("usr_123");
        when(repository.findSshKeys(userId)).thenReturn(List.of(sshKeyFixtures.encryptedSshKey(
                new SshKeyId("ssh_123"), userId, "existing", PRIVATE_KEY, NOW)));
        ConfigurationManagementApplicationService service = new ConfigurationManagementApplicationService(
                repository,
                repositoryTypeDictionaryRepository(),
                org.mockito.Mockito.mock(UserRepository.class),
                org.mockito.Mockito.mock(GitRemoteService.class),
                createTestCacheService(),
                sshKeyFixtures.encryptionService(),
                org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));

        SshKeyTestFixtures.EncryptedPayload payload = sshKeyFixtures.encryptPayload(PRIVATE_KEY);
        assertThatThrownBy(() -> service.addSshKey(
                userId, "work", payload.encryptedPrivateKey(), payload.encryptedAesKey(),
                payload.encryptionNonce(), payload.fingerprint()))
                .isInstanceOf(PlatformException.class)
                .satisfies(error -> assertThat(((PlatformException) error).errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void addSshKeyStoresEncryptedPayloadAndVerifiesFingerprint() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        UserId userId = new UserId("usr_123");
        when(repository.findSshKeys(userId)).thenReturn(List.of());
        when(repository.saveSshKey(argThat(saved -> saved != null)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        ConfigurationManagementApplicationService service = new ConfigurationManagementApplicationService(
                repository,
                repositoryTypeDictionaryRepository(),
                org.mockito.Mockito.mock(UserRepository.class),
                org.mockito.Mockito.mock(GitRemoteService.class),
                createTestCacheService(),
                sshKeyFixtures.encryptionService(),
                org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));

        SshKeyTestFixtures.EncryptedPayload payload = sshKeyFixtures.encryptPayload(PRIVATE_KEY);
        ConfigurationManagementResponses.SshKeyResponse response = service.addSshKey(
                userId, "work", payload.encryptedPrivateKey(), payload.encryptedAesKey(),
                payload.encryptionNonce(), payload.fingerprint());

        assertThat(response.name()).isEqualTo("work");
        assertThat(response.fingerprint()).isEqualTo(payload.fingerprint());
        verify(repository).saveSshKey(argThat(saved ->
                "work".equals(saved.name())
                        && payload.encryptedPrivateKey().equals(saved.encryptedPrivateKey())
                        && payload.encryptedAesKey().equals(saved.encryptedAesKey())
                        && payload.encryptionNonce().equals(saved.encryptionNonce())));
    }

    private static GitCloneCacheService createTestCacheService() {
        // 测试用的临时缓存目录
        Path tempDir = Path.of(System.getProperty("java.io.tmpdir"), "git-cache-test-" + System.currentTimeMillis());
        return new GitCloneCacheService(tempDir, Duration.ofHours(1), Duration.ofMinutes(5));
    }

    private static DictionaryRepository repositoryTypeDictionaryRepository() {
        DictionaryRepository dictionaryRepository = org.mockito.Mockito.mock(DictionaryRepository.class);
        List<Dictionary> dictionaries = List.of(
                dictionary(CodeRepositoryType.TEST_WORK_REPOSITORY.value(), "测试工作库", 1),
                dictionary(CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(), "应用代码库", 2),
                dictionary(CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value(), "应用资产库", 3));
        when(dictionaryRepository.findByDictKey(Dictionary.DICT_KEY_REPOSITORY_TYPE)).thenReturn(dictionaries);
        for (Dictionary dictionary : dictionaries) {
            when(dictionaryRepository.findByDictKeyAndValue(Dictionary.DICT_KEY_REPOSITORY_TYPE, dictionary.dictValue()))
                    .thenReturn(Optional.of(dictionary));
        }
        return dictionaryRepository;
    }

    private static Dictionary dictionary(String value, String label, int sortOrder) {
        return new Dictionary(
                new DictId("dict_repository_type_" + value.toLowerCase()),
                "版本库类型",
                Dictionary.DICT_KEY_REPOSITORY_TYPE,
                value,
                label,
                sortOrder,
                NOW,
                NOW);
    }

    private static CodeRepository codeRepository(String gitUrl) {
        return new CodeRepository(
                new CodeRepositoryId("repo_123"),
                gitUrl,
                "Demo",
                "demo",
                CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(),
                false,
                NOW,
                NOW);
    }
}
