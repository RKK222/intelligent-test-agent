package com.enterprise.testagent.system.management.externalapi;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.common.pagination.PageResponse;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredential;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredentialId;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredentialRepository;
import com.enterprise.testagent.domain.externalapi.ExternalApiCredentialsUpdatedEvent;
import com.enterprise.testagent.domain.externalapi.ExternalApiScope;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 超级管理员使用的外部 API 凭据应用服务。 */
@Service
public class ExternalApiCredentialApplicationService {

    private final ExternalApiCredentialRepository repository;
    private final ExternalApiCredentialCipher cipher;
    private final ExternalApiKeyGenerator keyGenerator;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;
    private final Supplier<String> idSupplier;

    public ExternalApiCredentialApplicationService(
            ExternalApiCredentialRepository repository,
            ExternalApiCredentialCipher cipher,
            ExternalApiKeyGenerator keyGenerator,
            ApplicationEventPublisher eventPublisher) {
        this(repository, cipher, keyGenerator, eventPublisher, Clock.systemUTC(),
                () -> "eac_" + UUID.randomUUID().toString().replace("-", ""));
    }

    ExternalApiCredentialApplicationService(
            ExternalApiCredentialRepository repository,
            ExternalApiCredentialCipher cipher,
            ExternalApiKeyGenerator keyGenerator,
            ApplicationEventPublisher eventPublisher,
            Clock clock,
            Supplier<String> idSupplier) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.cipher = Objects.requireNonNull(cipher, "cipher must not be null");
        this.keyGenerator = Objects.requireNonNull(keyGenerator, "keyGenerator must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.idSupplier = Objects.requireNonNull(idSupplier, "idSupplier must not be null");
    }

    public List<ExternalApiCredentialResponses.ScopeOption> listScopes() {
        return Arrays.stream(ExternalApiScope.values())
                .map(scope -> new ExternalApiCredentialResponses.ScopeOption(scope.name(), scope.description()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ExternalApiCredentialResponses.CredentialView> list(
            String keyword, Boolean enabled, PageRequest pageRequest) {
        PageResponse<ExternalApiCredential> page = repository.findPage(keyword, enabled, pageRequest);
        return new PageResponse<>(
                page.items().stream().map(ExternalApiCredentialApplicationService::view).toList(),
                page.page(), page.size(), page.total());
    }

    /** 生成一次性明文 Key，数据库事务内仅写入 RSA 密文、摘要和提示。 */
    @Transactional
    public ExternalApiCredentialResponses.Created create(
            ExternalApiCredentialResponses.CreateCommand command, String traceId) {
        String toolCode = ExternalApiCredential.requireToolCode(command.toolCode());
        if (repository.existsByToolCode(toolCode)) {
            throw new PlatformException(ErrorCode.CONFLICT, "工具编码已存在");
        }
        ExternalApiKeyMaterial material = keyGenerator.generate();
        Instant now = Instant.now(clock);
        ExternalApiCredential credential = new ExternalApiCredential(
                new ExternalApiCredentialId(idSupplier.get()),
                toolCode,
                ExternalApiCredential.requireToolName(command.toolName()),
                cipher.encrypt(material.apiKey()),
                material.fingerprint(),
                material.keyHint(),
                command.enabled(),
                command.scopes(),
                now,
                now);
        repository.insert(credential);
        publishUpdated(traceId);
        return new ExternalApiCredentialResponses.Created(view(credential), material.apiKey());
    }

    @Transactional
    public ExternalApiCredentialResponses.CredentialView update(
            String rawCredentialId,
            ExternalApiCredentialResponses.UpdateCommand command,
            String traceId) {
        ExternalApiCredential current = requireCredential(rawCredentialId);
        ExternalApiCredential updated = new ExternalApiCredential(
                current.credentialId(), current.toolCode(), command.toolName(), current.encryptedApiKey(),
                current.apiKeyFingerprint(), current.keyHint(), command.enabled(), command.scopes(),
                current.createdAt(), Instant.now(clock));
        repository.updateDetails(updated);
        publishUpdated(traceId);
        return view(updated);
    }

    @Transactional(readOnly = true)
    public ExternalApiCredentialResponses.Revealed reveal(String rawCredentialId) {
        ExternalApiCredential credential = requireCredential(rawCredentialId);
        return new ExternalApiCredentialResponses.Revealed(
                credential.credentialId().value(), credential.toolCode(), cipher.decrypt(credential.encryptedApiKey()));
    }

    /** 轮换后只保留新密文；事务提交触发全 Java 整表刷新，不提供旧 Key 宽限期。 */
    @Transactional
    public ExternalApiCredentialResponses.Revealed rotate(String rawCredentialId, String traceId) {
        ExternalApiCredential current = requireCredential(rawCredentialId);
        ExternalApiKeyMaterial material = keyGenerator.generate();
        ExternalApiCredential updated = new ExternalApiCredential(
                current.credentialId(), current.toolCode(), current.toolName(), cipher.encrypt(material.apiKey()),
                material.fingerprint(), material.keyHint(), current.enabled(), current.scopes(),
                current.createdAt(), Instant.now(clock));
        repository.updateKey(updated);
        publishUpdated(traceId);
        return new ExternalApiCredentialResponses.Revealed(
                updated.credentialId().value(), updated.toolCode(), material.apiKey());
    }

    @Transactional
    public void delete(String rawCredentialId, String traceId) {
        ExternalApiCredentialId credentialId = new ExternalApiCredentialId(rawCredentialId);
        if (!repository.delete(credentialId)) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "API Key 凭据不存在");
        }
        publishUpdated(traceId);
    }

    private ExternalApiCredential requireCredential(String rawCredentialId) {
        return repository.findById(new ExternalApiCredentialId(rawCredentialId))
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "API Key 凭据不存在"));
    }

    private void publishUpdated(String traceId) {
        eventPublisher.publishEvent(new ExternalApiCredentialsUpdatedEvent(traceId));
    }

    private static ExternalApiCredentialResponses.CredentialView view(ExternalApiCredential credential) {
        return new ExternalApiCredentialResponses.CredentialView(
                credential.credentialId().value(), credential.toolCode(), credential.toolName(),
                credential.scopes(), credential.keyHint(), credential.enabled(),
                credential.createdAt(), credential.updatedAt());
    }
}
