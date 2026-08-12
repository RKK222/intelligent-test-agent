package com.enterprise.testagent.system.management.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRevoker;
import com.enterprise.testagent.domain.localclient.LocalClientCredential;
import com.enterprise.testagent.domain.localclient.LocalClientCredentialRepository;
import com.enterprise.testagent.domain.localclient.LocalClientCredentialStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.system.management.externalapi.ExternalApiCredentialCipher;
import com.enterprise.testagent.system.management.externalapi.ExternalApiKeyGenerator;
import com.enterprise.testagent.system.management.externalapi.ExternalApiKeyMaterial;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 每用户唯一 client key 的创建、复制、轮换、撤销和摘要认证入口。 */
@Service
public class LocalClientCredentialApplicationService {

    public static final String CLIENT_KEY_PREFIX = "tack_v1_";

    private final LocalClientCredentialRepository repository;
    private final ExternalApiCredentialCipher cipher;
    private final ExternalApiKeyGenerator keyGenerator;
    private final LocalClientConnectionRevoker connectionRevoker;
    private final LocalClientCredentialAuditLogger auditLogger;
    private final Clock clock;

    @Autowired
    public LocalClientCredentialApplicationService(
            LocalClientCredentialRepository repository,
            ExternalApiCredentialCipher cipher,
            ExternalApiKeyGenerator keyGenerator,
            LocalClientConnectionRevoker connectionRevoker,
            LocalClientCredentialAuditLogger auditLogger) {
        this(repository, cipher, keyGenerator, connectionRevoker, auditLogger, Clock.systemUTC());
    }

    LocalClientCredentialApplicationService(
            LocalClientCredentialRepository repository,
            ExternalApiCredentialCipher cipher,
            ExternalApiKeyGenerator keyGenerator,
            LocalClientConnectionRevoker connectionRevoker,
            LocalClientCredentialAuditLogger auditLogger,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.cipher = Objects.requireNonNull(cipher, "cipher must not be null");
        this.keyGenerator = Objects.requireNonNull(keyGenerator, "keyGenerator must not be null");
        this.connectionRevoker = Objects.requireNonNull(connectionRevoker, "connectionRevoker must not be null");
        this.auditLogger = Objects.requireNonNull(auditLogger, "auditLogger must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Transactional(readOnly = true)
    public LocalClientCredentialResponses.CredentialView get(UserId userId) {
        return repository.findByUserId(userId)
                .map(LocalClientCredentialApplicationService::view)
                .orElseGet(() -> new LocalClientCredentialResponses.CredentialView(
                        false, null, 0, null, null, null));
    }

    /** 创建或重新启用凭据；明文不从创建响应返回，必须显式调用 copy。 */
    @Transactional
    public LocalClientCredentialResponses.CredentialView create(UserId userId, String traceId) {
        try {
            repository.lockUser(userId);
            LocalClientCredential current = repository.findByUserId(userId).orElse(null);
            if (current != null && current.active()) {
                auditAfterCompletion(userId, "CREATE", traceId);
                return view(current);
            }
            LocalClientCredential created = newCredential(
                    userId,
                    current == null ? 1 : current.version() + 1,
                    current == null ? Instant.now(clock) : current.createdAt());
            repository.save(created);
            auditAfterCompletion(userId, "CREATE", traceId);
            return view(created);
        } catch (RuntimeException exception) {
            auditLogger.failure(userId, "CREATE", traceId, safeReason(exception));
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public LocalClientCredentialResponses.PlaintextKey copy(UserId userId, String traceId) {
        try {
            LocalClientCredential credential = requireActive(userId);
            String clientKey = cipher.decrypt(credential.encryptedClientKey());
            auditLogger.success(userId, "COPY", traceId);
            return new LocalClientCredentialResponses.PlaintextKey(clientKey);
        } catch (RuntimeException exception) {
            auditLogger.failure(userId, "COPY", traceId, safeReason(exception));
            throw exception;
        }
    }

    @Transactional
    public LocalClientCredentialResponses.CredentialView rotate(UserId userId, String traceId) {
        try {
            repository.lockUser(userId);
            LocalClientCredential current = requireActive(userId);
            LocalClientCredential rotated = newCredential(userId, current.version() + 1, current.createdAt());
            repository.save(rotated);
            auditAfterCompletion(userId, "ROTATE", traceId);
            revokeAfterCommit(userId, "CLIENT_KEY_ROTATED", traceId);
            return view(rotated);
        } catch (RuntimeException exception) {
            auditLogger.failure(userId, "ROTATE", traceId, safeReason(exception));
            throw exception;
        }
    }

    @Transactional
    public void revoke(UserId userId, String traceId) {
        try {
            repository.lockUser(userId);
            LocalClientCredential current = requireActive(userId);
            Instant now = Instant.now(clock);
            repository.save(new LocalClientCredential(
                    current.userId(),
                    current.encryptedClientKey(),
                    current.clientKeyFingerprint(),
                    current.keyHint(),
                    current.version() + 1,
                    LocalClientCredentialStatus.REVOKED,
                    current.createdAt(),
                    now,
                    now));
            auditAfterCompletion(userId, "REVOKE", traceId);
            revokeAfterCommit(userId, "CLIENT_KEY_REVOKED", traceId);
        } catch (RuntimeException exception) {
            auditLogger.failure(userId, "REVOKE", traceId, safeReason(exception));
            throw exception;
        }
    }

    /** WSS 注册只按 SHA-256 摘要认证，任何失败都返回同一未认证错误。 */
    @Transactional(readOnly = true)
    public LocalClientCredentialResponses.AuthenticatedCredential authenticate(String clientKey) {
        if (clientKey == null || !clientKey.startsWith(CLIENT_KEY_PREFIX) || clientKey.length() > 128) {
            throw unauthenticated();
        }
        String fingerprint = ExternalApiKeyGenerator.fingerprint(clientKey);
        LocalClientCredential credential = repository.findActiveByFingerprint(fingerprint)
                .orElseThrow(LocalClientCredentialApplicationService::unauthenticated);
        return new LocalClientCredentialResponses.AuthenticatedCredential(
                credential.userId().value(), credential.version());
    }

    private LocalClientCredential newCredential(UserId userId, long version, Instant createdAt) {
        ExternalApiKeyMaterial material = keyGenerator.generate(CLIENT_KEY_PREFIX);
        Instant now = Instant.now(clock);
        return new LocalClientCredential(
                userId,
                cipher.encrypt(material.apiKey()),
                material.fingerprint(),
                material.keyHint(),
                version,
                LocalClientCredentialStatus.ACTIVE,
                createdAt,
                now,
                null);
    }

    private LocalClientCredential requireActive(UserId userId) {
        return repository.findByUserId(userId)
                .filter(LocalClientCredential::active)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "本地客户端密钥不存在或已撤销"));
    }

    private void revokeAfterCommit(UserId userId, String reason, String traceId) {
        Runnable revocation = () -> connectionRevoker.revokeAll(userId, reason, traceId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            revocation.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                revocation.run();
            }
        });
    }

    private void auditAfterCompletion(UserId userId, String action, String traceId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            auditLogger.success(userId, action, traceId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_COMMITTED) {
                    auditLogger.success(userId, action, traceId);
                } else {
                    auditLogger.failure(userId, action, traceId, "TRANSACTION_ROLLED_BACK");
                }
            }
        });
    }

    private static LocalClientCredentialResponses.CredentialView view(LocalClientCredential credential) {
        return new LocalClientCredentialResponses.CredentialView(
                true,
                credential.keyHint(),
                credential.version(),
                credential.status(),
                credential.createdAt(),
                credential.updatedAt());
    }

    private static PlatformException unauthenticated() {
        return new PlatformException(ErrorCode.UNAUTHENTICATED, "本地客户端认证失败");
    }

    private static String safeReason(RuntimeException exception) {
        return exception instanceof PlatformException platformException
                ? platformException.errorCode().name()
                : "INTERNAL_ERROR";
    }
}
