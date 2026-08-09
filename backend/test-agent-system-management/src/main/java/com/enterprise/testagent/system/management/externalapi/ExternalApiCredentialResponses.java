package com.enterprise.testagent.system.management.externalapi;

import com.enterprise.testagent.domain.externalapi.ExternalApiScope;
import java.time.Instant;
import java.util.Set;

/** 外部 API 凭据管理命令与安全响应模型，所有列表模型均不含密文。 */
public final class ExternalApiCredentialResponses {

    private ExternalApiCredentialResponses() {
    }

    public record CreateCommand(
            String toolCode,
            String toolName,
            Set<ExternalApiScope> scopes,
            boolean enabled) {
        public CreateCommand {
            scopes = Set.copyOf(scopes == null ? Set.of() : scopes);
            if (scopes.isEmpty()) {
                throw new IllegalArgumentException("scopes must not be empty");
            }
        }
    }

    public record UpdateCommand(String toolName, Set<ExternalApiScope> scopes, boolean enabled) {
        public UpdateCommand {
            scopes = Set.copyOf(scopes == null ? Set.of() : scopes);
            if (scopes.isEmpty()) {
                throw new IllegalArgumentException("scopes must not be empty");
            }
        }
    }

    public record CredentialView(
            String credentialId,
            String toolCode,
            String toolName,
            Set<ExternalApiScope> scopes,
            String keyHint,
            boolean enabled,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record Created(CredentialView credential, String apiKey) {
    }

    public record Revealed(String credentialId, String toolCode, String apiKey) {
    }

    public record ScopeOption(String code, String name) {
    }
}
