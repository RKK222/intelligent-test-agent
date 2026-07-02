package com.icbc.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.git.GitRemoteService;
import com.icbc.testagent.common.git.SshKeyEncryptionService;
import com.icbc.testagent.domain.configuration.CodeRepositoryType;
import com.icbc.testagent.domain.configuration.CodeRepository;
import com.icbc.testagent.domain.configuration.CodeRepositoryDeploymentMode;
import com.icbc.testagent.domain.configuration.CodeRepositoryId;
import com.icbc.testagent.domain.configuration.ConfigurationManagementRepository;
import com.icbc.testagent.domain.configuration.SshKeyId;
import com.icbc.testagent.domain.configuration.UserSshKey;
import com.icbc.testagent.domain.dictionary.DictId;
import com.icbc.testagent.domain.dictionary.Dictionary;
import com.icbc.testagent.domain.dictionary.DictionaryRepository;
import com.icbc.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.icbc.testagent.domain.user.User;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.user.UserRepository;
import com.icbc.testagent.domain.user.UserStatus;
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
        String storedGitUrl = "scm-share.sdc.cs.icbc:29418/hzefficiencytools/interfaceplatform";
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
                "scm-share.sdc.cs.icbc:29418/hzefficiencytools/interfaceplatform",
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
                eq("ssh://001177621@scm-share.sdc.cs.icbc:29418/hzefficiencytools/interfaceplatform"),
                eq(PRIVATE_KEY))).thenReturn(List.of("main"));

        ConfigurationManagementApplicationService service =
                new ConfigurationManagementApplicationService(repository, repositoryTypeDictionaryRepository(), userRepository, gitRemoteService, gitCloneCacheService, encryptionService, org.mockito.Mockito.mock(ManagedWorkspaceRepository.class));

        assertThat(service.listBranches("repo_123", userId)).containsExactly("main");
        verify(gitRemoteService).listBranches(
                "ssh://001177621@scm-share.sdc.cs.icbc:29418/hzefficiencytools/interfaceplatform",
                PRIVATE_KEY);
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
