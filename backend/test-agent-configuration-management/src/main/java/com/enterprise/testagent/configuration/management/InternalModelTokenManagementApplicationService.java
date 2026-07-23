package com.enterprise.testagent.configuration.management;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.InternalModelProvidersUpdatedEvent;
import com.enterprise.testagent.domain.configuration.InternalModelToken;
import com.enterprise.testagent.domain.configuration.InternalModelTokenRepository;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 可复用内部模型 Token 定义管理服务。外部 Token 由管理员提供，平台只负责安全记录与关联。
 */
@Service
public class InternalModelTokenManagementApplicationService {

    private final InternalModelTokenRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public InternalModelTokenManagementApplicationService(
            InternalModelTokenRepository repository,
            ApplicationEventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    public List<InternalModelToken> list() {
        return repository.findAll();
    }

    public InternalModelToken create(String name, String authToken, String traceId) {
        String normalizedName = requireText(name, "Token 名称");
        String tokenValue = requireTokenValue(authToken);
        if (repository.findByName(normalizedName).isPresent()) {
            throw new PlatformException(ErrorCode.CONFLICT, "内部模型 Token 名称已存在");
        }
        InternalModelToken created = repository.create(normalizedName, tokenValue, Instant.now());
        publishRefresh(traceId);
        return created;
    }

    public InternalModelToken update(long tokenId, String name, String authToken, String traceId) {
        InternalModelToken existing = requireToken(tokenId);
        String normalizedName = name == null ? existing.name() : requireText(name, "Token 名称");
        String tokenValue = authToken == null || authToken.isBlank() ? null : authToken;
        repository.findByName(normalizedName)
                .filter(found -> found.tokenId() != tokenId)
                .ifPresent(ignored -> {
                    throw new PlatformException(ErrorCode.CONFLICT, "内部模型 Token 名称已存在");
                });
        InternalModelToken updated = repository.update(tokenId, normalizedName, tokenValue, Instant.now())
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "内部模型 Token 不存在"));
        publishRefresh(traceId);
        return updated;
    }

    public void delete(long tokenId, String traceId) {
        InternalModelToken existing = requireToken(tokenId);
        if (existing.referencedProviderCount() > 0) {
            throw new PlatformException(ErrorCode.CONFLICT, "内部模型 Token 仍被供应商引用，不能删除");
        }
        if (!repository.deleteIfUnreferenced(tokenId)) {
            throw new PlatformException(ErrorCode.CONFLICT, "内部模型 Token 已被并发引用或删除");
        }
        publishRefresh(traceId);
    }

    private InternalModelToken requireToken(long tokenId) {
        if (tokenId <= 0) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "tokenId 必须为正数");
        }
        return repository.findById(tokenId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "内部模型 Token 不存在"));
    }

    private void publishRefresh(String traceId) {
        eventPublisher.publishEvent(new InternalModelProvidersUpdatedEvent(traceId));
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, fieldName + "不能为空");
        }
        return value.trim();
    }

    private String requireTokenValue(String value) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "Token 值不能为空");
        }
        // Token 来自外部系统，平台只校验非空并原样记录，避免改变具有边界空白的合法凭据。
        return value;
    }
}
