package com.icbc.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.common.git.GitRemoteService;
import com.icbc.testagent.domain.configuration.CodeRepository;
import com.icbc.testagent.domain.configuration.CodeRepositoryId;
import com.icbc.testagent.domain.configuration.ConfigurationManagementRepository;
import com.icbc.testagent.domain.configuration.SshKeyId;
import com.icbc.testagent.domain.configuration.UserSshKey;
import com.icbc.testagent.domain.user.UserId;
import com.icbc.testagent.domain.user.UserRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConfigurationManagementApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-23T00:00:00Z");
    private static final String PRIVATE_KEY = "-----BEGIN OPENSSH PRIVATE KEY-----\nsecret\n-----END OPENSSH PRIVATE KEY-----\n";

    @Test
    void sshRepositoryBranchesUseCurrentUsersStoredPrivateKey() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        UserRepository userRepository = org.mockito.Mockito.mock(UserRepository.class);
        GitRemoteService gitRemoteService = org.mockito.Mockito.mock(GitRemoteService.class);
        SshKeyEncryptionService encryptionService = new SshKeyEncryptionService(SshKeyEncryptionServiceTest.base64AesKey());
        SshKeyEncryptionService.EncryptedPrivateKey encrypted = encryptionService.encrypt(PRIVATE_KEY);
        CodeRepository codeRepository = codeRepository("git@gitee.com:demo/repo.git");
        UserId userId = new UserId("usr_123");

        when(repository.findRepository(codeRepository.repositoryId())).thenReturn(java.util.Optional.of(codeRepository));
        when(repository.findSshKeys(userId)).thenReturn(List.of(new UserSshKey(
                new SshKeyId("ssh_123"),
                userId,
                "work",
                encryptionService.fingerprint(PRIVATE_KEY),
                encrypted.encryptedPrivateKey(),
                encrypted.encryptionNonce(),
                NOW)));
        when(gitRemoteService.listBranches(eq(codeRepository.gitUrl()), eq(PRIVATE_KEY))).thenReturn(List.of("main"));

        ConfigurationManagementApplicationService service =
                new ConfigurationManagementApplicationService(repository, userRepository, gitRemoteService, encryptionService);

        assertThat(service.listBranches("repo_123", userId)).containsExactly("main");
        verify(gitRemoteService).listBranches(codeRepository.gitUrl(), PRIVATE_KEY);
    }

    @Test
    void addSshKeyRejectsSecondKeyForSameUser() {
        ConfigurationManagementRepository repository = org.mockito.Mockito.mock(ConfigurationManagementRepository.class);
        UserId userId = new UserId("usr_123");
        when(repository.findSshKeys(userId)).thenReturn(List.of(new UserSshKey(
                new SshKeyId("ssh_123"),
                userId,
                "existing",
                "SHA256:abc",
                "cipher",
                "nonce",
                NOW)));
        ConfigurationManagementApplicationService service = new ConfigurationManagementApplicationService(
                repository,
                org.mockito.Mockito.mock(UserRepository.class),
                org.mockito.Mockito.mock(GitRemoteService.class),
                new SshKeyEncryptionService(SshKeyEncryptionServiceTest.base64AesKey()));

        assertThatThrownBy(() -> service.addSshKey(userId, "work", PRIVATE_KEY))
                .isInstanceOf(PlatformException.class)
                .satisfies(error -> assertThat(((PlatformException) error).errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    private static CodeRepository codeRepository(String gitUrl) {
        return new CodeRepository(new CodeRepositoryId("repo_123"), gitUrl, "Demo", false, NOW, NOW);
    }
}
