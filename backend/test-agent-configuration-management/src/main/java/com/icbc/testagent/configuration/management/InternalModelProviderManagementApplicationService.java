package com.icbc.testagent.configuration.management;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.configuration.InternalModelProvider;
import com.icbc.testagent.domain.configuration.InternalModelProviderRepository;
import com.icbc.testagent.domain.configuration.InternalModelProvidersUpdatedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 内部模型供应商管理应用服务，只维护供应商转发地址和全局 ICBC token。
 */
@Service
public class InternalModelProviderManagementApplicationService {

    private final InternalModelProviderRepository repository;
    private final ApplicationEventPublisher eventPublisher;

    public InternalModelProviderManagementApplicationService(
            InternalModelProviderRepository repository,
            ApplicationEventPublisher eventPublisher) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "eventPublisher must not be null");
    }

    public InternalModelProviderManagementResponse current() {
        return new InternalModelProviderManagementResponse(repository.findAll(), repository.findAuthToken().isPresent());
    }

    public InternalModelProviderManagementResponse save(UpdateInternalModelProvidersCommand command, String traceId) {
        Objects.requireNonNull(command, "command must not be null");
        Instant now = Instant.now();
        List<InternalModelProvider> providers = command.providers().stream()
                .map(item -> item.toDomain(now))
                .toList();
        repository.replaceProviders(providers, now);
        if (command.authToken() != null) {
            repository.saveAuthToken(command.authToken(), now);
        }
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
            Integer sortOrder) {

        private InternalModelProvider toDomain(Instant now) {
            if (providerId == null || providerId.isBlank()
                    || name == null || name.isBlank()
                    || baseUrl == null || baseUrl.isBlank()) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "内部模型供应商 providerId/name/baseUrl 不能为空");
            }
            return new InternalModelProvider(
                    providerId.trim(),
                    name.trim(),
                    baseUrl.trim(),
                    enabled == null || enabled,
                    sortOrder == null ? 0 : sortOrder,
                    now,
                    now);
        }
    }
}
