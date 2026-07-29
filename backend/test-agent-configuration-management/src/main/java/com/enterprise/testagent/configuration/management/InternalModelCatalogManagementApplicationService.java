package com.enterprise.testagent.configuration.management;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.InternalModelCatalogUpdatedEvent;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModel;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModelRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRepository;
import com.enterprise.testagent.domain.configuration.ModelCapability;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 超级管理员维护内部供应商公开模型目录的应用服务。 */
@Service
public class InternalModelCatalogManagementApplicationService implements InternalModelCatalogManagementService {

    private static final int MAX_PROVIDER_ID_LENGTH = 128;
    private static final int MAX_MODEL_TEXT_LENGTH = 256;

    private final InternalModelProviderRepository providerRepository;
    private final InternalModelProviderModelRepository modelRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Autowired
    public InternalModelCatalogManagementApplicationService(
            InternalModelProviderRepository providerRepository,
            InternalModelProviderModelRepository modelRepository,
            ApplicationEventPublisher eventPublisher) {
        this(providerRepository, modelRepository, eventPublisher, Clock.systemUTC());
    }

    InternalModelCatalogManagementApplicationService(
            InternalModelProviderRepository providerRepository,
            InternalModelProviderModelRepository modelRepository,
            ApplicationEventPublisher eventPublisher,
            Clock clock) {
        this.providerRepository = Objects.requireNonNull(providerRepository);
        this.modelRepository = Objects.requireNonNull(modelRepository);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public List<InternalModelProviderModel> current(String providerId) {
        requireProvider(providerId);
        return modelRepository.findByProviderId(providerId);
    }

    /** 覆盖一个 provider 的模型目录；任何配置更改都清空旧探测结果。 */
    @Transactional
    @Override
    public List<InternalModelProviderModel> save(
            String providerId,
            UpdateCatalogCommand command,
            String traceId) {
        String normalizedProviderId = requireProvider(providerId);
        Objects.requireNonNull(command, "command must not be null");
        Instant now = clock.instant();
        Map<String, InternalModelProviderModel> existing = modelRepository.findByProviderId(normalizedProviderId)
                .stream()
                .collect(Collectors.toMap(InternalModelProviderModel::modelId, Function.identity()));
        Set<String> modelIds = new HashSet<>();
        List<InternalModelProviderModel> replacement = command.models().stream()
                .map(item -> item.toDomain(normalizedProviderId, now, existing, modelRepository, modelIds))
                .toList();
        modelRepository.replaceForProvider(normalizedProviderId, replacement, now);
        eventPublisher.publishEvent(new InternalModelCatalogUpdatedEvent(normalizedProviderId, traceId));
        return modelRepository.findByProviderId(normalizedProviderId);
    }

    private String requireProvider(String providerId) {
        String normalized = requireText(providerId, "providerId", MAX_PROVIDER_ID_LENGTH);
        if (providerRepository.findByProviderId(normalized).isEmpty()) {
            throw new PlatformException(ErrorCode.NOT_FOUND, "内部模型供应商不存在");
        }
        return normalized;
    }

    public record UpdateCatalogCommand(List<ModelItem> models) {
        public UpdateCatalogCommand {
            models = models == null ? List.of() : List.copyOf(models);
        }
    }

    public record ModelItem(
            String modelId,
            String upstreamModelId,
            String displayName,
            Long contextLimit,
            Boolean enabled,
            Set<ModelCapability> capabilities) {

        private InternalModelProviderModel toDomain(
                String providerId,
                Instant now,
                Map<String, InternalModelProviderModel> existing,
                InternalModelProviderModelRepository repository,
                Set<String> modelIds) {
            String normalizedModelId = requireText(modelId, "modelId", MAX_MODEL_TEXT_LENGTH);
            if (!modelIds.add(normalizedModelId)) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "公开模型 modelId 不能重复");
            }
            repository.findByModelId(normalizedModelId).ifPresent(owner -> {
                if (!providerId.equals(owner.providerId())) {
                    throw new PlatformException(ErrorCode.CONFLICT, "公开模型 modelId 已被其他供应商使用");
                }
            });
            Set<ModelCapability> normalizedCapabilities = capabilities == null ? Set.of() : Set.copyOf(capabilities);
            if (normalizedCapabilities.isEmpty()) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "模型至少声明一项能力");
            }
            if (contextLimit != null && contextLimit <= 0) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "contextLimit 必须为正数");
            }
            InternalModelProviderModel previous = existing.get(normalizedModelId);
            return new InternalModelProviderModel(
                    providerId,
                    normalizedModelId,
                    requireText(upstreamModelId, "upstreamModelId", MAX_MODEL_TEXT_LENGTH),
                    requireText(displayName, "displayName", MAX_MODEL_TEXT_LENGTH),
                    contextLimit,
                    enabled == null || enabled,
                    normalizedCapabilities,
                    Set.of(),
                    null,
                    previous == null ? now : previous.createdAt(),
                    now);
        }
    }

    private static String requireText(String value, String field, int maximumLength) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + " 不能为空");
        }
        String normalized = value.trim();
        if (normalized.length() > maximumLength) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + " 长度超过限制");
        }
        return normalized;
    }
}
