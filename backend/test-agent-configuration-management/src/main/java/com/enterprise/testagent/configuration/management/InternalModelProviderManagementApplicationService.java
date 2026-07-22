package com.enterprise.testagent.configuration.management;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.InternalModelProvider;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProvidersUpdatedEvent;
import com.enterprise.testagent.domain.configuration.InternalModelToken;
import com.enterprise.testagent.domain.configuration.InternalModelTokenRepository;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 内部模型供应商管理应用服务，维护供应商地址及其可复用 Token 定义关联。
 */
@Service
public class InternalModelProviderManagementApplicationService {

    private final InternalModelProviderRepository repository;
    private final InternalModelTokenRepository tokenRepository;
    private final ApplicationEventPublisher eventPublisher;

    public InternalModelProviderManagementApplicationService(
            InternalModelProviderRepository repository,
            InternalModelTokenRepository tokenRepository,
            ApplicationEventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.tokenRepository = Objects.requireNonNull(tokenRepository, "tokenRepository must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    public InternalModelProviderManagementResponse current() {
        List<InternalModelProvider> providers = repository.findAll();
        boolean tokenConfigured = providers.stream()
                .filter(InternalModelProvider::enabled)
                .allMatch(InternalModelProvider::tokenConfigured);
        return new InternalModelProviderManagementResponse(providers, tokenConfigured);
    }

    /**
     * 覆盖保存供应商列表。旧客户端未提交 tokenId 时保留既有关系，新供应商可继承兼容默认 Token。
     */
    public InternalModelProviderManagementResponse save(UpdateInternalModelProvidersCommand command, String traceId) {
        Objects.requireNonNull(command, "command must not be null");
        Instant now = Instant.now();
        Map<String, InternalModelProvider> existingProviders = repository.findAll().stream()
                .collect(Collectors.toMap(InternalModelProvider::providerId, Function.identity()));

        String legacyAuthToken = normalizeOptionalToken(command.authToken());
        Set<String> prevalidatedProviderIds = new HashSet<>();
        // 兼容 Token 双写发生前先完成所有确定性校验，失败请求不得悄悄轮换旧全局凭据。
        command.providers().forEach(item -> item.validateBeforeMutation(tokenRepository, prevalidatedProviderIds));
        InternalModelToken legacyDefault = legacyAuthToken == null
                ? tokenRepository.findLegacyDefault().orElse(null)
                : tokenRepository.upsertLegacyDefault(legacyAuthToken, now);
        if (legacyAuthToken != null) {
            // 兼容混合版本 Java：旧进程仍从单例设置表读取全局 Token。
            repository.saveAuthToken(legacyAuthToken, now);
        }

        Set<String> normalizedProviderIds = new HashSet<>();
        List<InternalModelProvider> providers = command.providers().stream()
                .map(item -> item.toDomain(
                        now,
                        existingProviders,
                        legacyDefault == null ? null : legacyDefault.tokenId(),
                        legacyAuthToken != null,
                        tokenRepository,
                normalizedProviderIds))
                .toList();
        repository.replaceProviders(providers, now);
        eventPublisher.publishEvent(new InternalModelProvidersUpdatedEvent(traceId));
        return current();
    }

    public void requestRefresh(String traceId) {
        eventPublisher.publishEvent(new InternalModelProvidersUpdatedEvent(traceId));
    }

    public record InternalModelProviderManagementResponse(
            List<InternalModelProvider> providers,
            boolean tokenConfigured) {
    }

    public record UpdateInternalModelProvidersCommand(
            List<InternalModelProviderItem> providers,
            String authToken) {

        public UpdateInternalModelProvidersCommand {
            providers = providers == null ? List.of() : List.copyOf(providers);
        }
    }

    public record InternalModelProviderItem(
            String providerId,
            String name,
            String baseUrl,
            Boolean enabled,
            Integer sortOrder,
            Long tokenId,
            Boolean clearToken) {

        /** 历史 Java 调用兼容构造器；HTTP JSON 仍可省略新增字段。 */
        public InternalModelProviderItem(
                String providerId,
                String name,
                String baseUrl,
                Boolean enabled,
                Integer sortOrder) {
            this(providerId, name, baseUrl, enabled, sortOrder, null, false);
        }

        private InternalModelProvider toDomain(
                Instant now,
                Map<String, InternalModelProvider> existingProviders,
                Long legacyDefaultTokenId,
                boolean legacyTokenSubmitted,
                InternalModelTokenRepository tokenRepository,
                Set<String> normalizedProviderIds) {
            String normalizedProviderId = requireText(providerId, "providerId");
            String normalizedName = requireText(name, "name");
            String normalizedBaseUrl = requireText(baseUrl, "baseUrl");
            if (!normalizedProviderIds.add(normalizedProviderId)) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型供应商 providerId 不能重复");
            }
            if (Boolean.TRUE.equals(clearToken) && tokenId != null) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "清除 Token 关联时不能同时提交 tokenId");
            }

            InternalModelProvider existing = existingProviders.get(normalizedProviderId);
            Long resolvedTokenId;
            if (Boolean.TRUE.equals(clearToken)) {
                resolvedTokenId = null;
            } else if (tokenId != null) {
                if (tokenId <= 0) {
                    throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型供应商 tokenId 必须为正数");
                }
                resolvedTokenId = tokenId;
            } else if (existing != null) {
                // 旧请求通常保留既有关联；本次明确提交兼容 authToken 时，才给无关联旧行补挂默认 Token。
                resolvedTokenId = existing.tokenId() != null
                        ? existing.tokenId()
                        : (legacyTokenSubmitted ? legacyDefaultTokenId : null);
            } else {
                resolvedTokenId = legacyDefaultTokenId;
            }

            boolean resolvedEnabled = enabled == null || enabled;
            if (resolvedEnabled && resolvedTokenId == null) {
                throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "启用的内部模型供应商 %s 必须选择 Token".formatted(normalizedProviderId));
            }
            if (resolvedTokenId != null && tokenRepository.findById(resolvedTokenId).isEmpty()) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型供应商选择的 Token 不存在");
            }
            Instant createdAt = existing == null ? now : existing.createdAt();
            return new InternalModelProvider(
                    normalizedProviderId,
                    normalizedName,
                    normalizedBaseUrl,
                    resolvedEnabled,
                    sortOrder == null ? 0 : sortOrder,
                    resolvedTokenId,
                    null,
                    false,
                    createdAt,
                    now);
        }

        private void validateBeforeMutation(
                InternalModelTokenRepository tokenRepository,
                Set<String> normalizedProviderIds) {
            String normalizedProviderId = requireText(providerId, "providerId");
            requireText(name, "name");
            requireText(baseUrl, "baseUrl");
            if (!normalizedProviderIds.add(normalizedProviderId)) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型供应商 providerId 不能重复");
            }
            if (Boolean.TRUE.equals(clearToken) && tokenId != null) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "清除 Token 关联时不能同时提交 tokenId");
            }
            if (Boolean.TRUE.equals(clearToken) && (enabled == null || enabled)) {
                throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "启用的内部模型供应商 %s 必须选择 Token".formatted(normalizedProviderId));
            }
            if (tokenId != null) {
                if (tokenId <= 0) {
                    throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型供应商 tokenId 必须为正数");
                }
                if (tokenRepository.findById(tokenId).isEmpty()) {
                    throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型供应商选择的 Token 不存在");
                }
            }
        }

        private static String requireText(String value, String fieldName) {
            if (value == null || value.isBlank()) {
                throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "内部模型供应商 %s 不能为空".formatted(fieldName));
            }
            return value.trim();
        }
    }

    private static String normalizeOptionalToken(String value) {
        // 兼容入口同样原样记录外部 Token；只把全空白值视为“未提交”。
        return value == null || value.isBlank() ? null : value;
    }
}
