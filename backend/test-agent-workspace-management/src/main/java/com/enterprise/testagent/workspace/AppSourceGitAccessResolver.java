package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.SshKeyEncryptionService;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.configuration.UserSshKey;
import com.enterprise.testagent.domain.user.User;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.user.UserRepository;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** 在异步副本执行时按原操作人重新解析 Git 地址和个人 SSH 凭据。 */
@Component
public class AppSourceGitAccessResolver {

    private final ConfigurationManagementRepository configuration;
    private final UserRepository users;
    private final SshKeyEncryptionService encryption;

    public AppSourceGitAccessResolver(
            ConfigurationManagementRepository configuration,
            UserRepository users,
            SshKeyEncryptionService encryption) {
        this.configuration = Objects.requireNonNull(configuration);
        this.users = Objects.requireNonNull(users);
        this.encryption = Objects.requireNonNull(encryption);
    }

    /** 返回仅限当前调用栈使用的明文凭据；结果不得进入数据库、广播、索引或日志。 */
    public GitAccess resolve(CodeRepository repository, UserId actorUserId) {
        Objects.requireNonNull(repository, "repository must not be null");
        Objects.requireNonNull(actorUserId, "actorUserId must not be null");
        String privateKey = null;
        if (repository.internalDeployment() || requiresSshKey(repository.gitUrl())) {
            UserSshKey key = configuration.findSshKeys(actorUserId).stream()
                    .findFirst()
                    .orElseThrow(() -> new PlatformException(ErrorCode.FORBIDDEN, "源码操作人未配置 SSH key"));
            if (key.encryptedAesKey() == null || key.encryptedAesKey().isBlank()) {
                throw new PlatformException(ErrorCode.INTERNAL_ERROR, "SSH key 使用旧版加密格式，请重新添加");
            }
            privateKey = encryption.decrypt(
                    key.encryptedPrivateKey(), key.encryptedAesKey(), key.encryptionNonce());
        }
        if (!repository.internalDeployment()) {
            return new GitAccess(repository.gitUrl(), privateKey);
        }
        User user = users.findByUserId(actorUserId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "源码操作凭据用户不存在"));
        return new GitAccess(repository.effectiveGitUrl(user.unifiedAuthId()), privateKey);
    }

    private boolean requiresSshKey(String url) {
        return url != null && (url.startsWith("ssh://") || url.contains("@") && url.contains(":"));
    }

    /** 明文 privateKey 仅传给 Git 子进程，禁止输出或持久化。 */
    public record GitAccess(String gitUrl, String privateKey) {
        public GitAccess {
            if (gitUrl == null || gitUrl.isBlank()) {
                throw new IllegalArgumentException("gitUrl must not be blank");
            }
        }
    }
}
