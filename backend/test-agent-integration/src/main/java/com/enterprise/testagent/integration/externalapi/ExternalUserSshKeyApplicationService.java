package com.enterprise.testagent.integration.externalapi;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.UserSshKey;
import com.enterprise.testagent.domain.externalapi.ExternalApiPrincipal;
import com.enterprise.testagent.domain.externalapi.ExternalApiScope;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserRepository;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 外部用户 SSH Key 查询编排，仅向 TAEK1 加密器传递方法局部明文。 */
@Service
public class ExternalUserSshKeyApplicationService {

    private final UserRepository users;
    private final ConfigurationManagementRepository configuration;
    private final SshKeyEncryptionService sshEncryption;
    private final ExternalSshKeyEnvelopeService envelopes;

    public ExternalUserSshKeyApplicationService(
            UserRepository users,
            ConfigurationManagementRepository configuration,
            SshKeyEncryptionService sshEncryption,
            ExternalSshKeyEnvelopeService envelopes) {
        this.users = Objects.requireNonNull(users, "users must not be null");
        this.configuration = Objects.requireNonNull(configuration, "configuration must not be null");
        this.sshEncryption = Objects.requireNonNull(sshEncryption, "sshEncryption must not be null");
        this.envelopes = Objects.requireNonNull(envelopes, "envelopes must not be null");
    }

    public ExternalSshKeyEnvelope get(
            String unifiedAuthId, ExternalApiPrincipal principal, String traceId) {
        if (!principal.hasScope(ExternalApiScope.USER_SSH_KEY_READ)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "外部工具没有读取用户 SSH Key 的权限");
        }
        User user = users.findByUnifiedAuthId(unifiedAuthId)
                .filter(User::canLogin)
                .orElseThrow(ExternalUserSshKeyApplicationService::notFound);
        List<UserSshKey> keys = configuration.findSshKeys(user.userId());
        UserSshKey sshKey = keys.stream().findFirst()
                .orElseThrow(ExternalUserSshKeyApplicationService::notFound);
        if (sshKey.encryptedAesKey() == null || sshKey.encryptedAesKey().isBlank()) {
            throw new PlatformException(ErrorCode.CONFLICT, "SSH Key 加密格式不受支持，请用户重新配置");
        }
        String privateKey = sshEncryption.decryptAndVerify(
                sshKey.encryptedPrivateKey(),
                sshKey.encryptedAesKey(),
                sshKey.encryptionNonce(),
                sshKey.fingerprint());
        return envelopes.encrypt(
                principal.toolCode(),
                principal.apiKey(),
                unifiedAuthId,
                sshKey.sshKeyId().value(),
                sshKey.name(),
                sshKey.fingerprint(),
                privateKey,
                traceId);
    }

    private static PlatformException notFound() {
        return new PlatformException(ErrorCode.NOT_FOUND, "用户或 SSH Key 不存在");
    }
}
