package com.enterprise.testagent.opencode.runtime.internalmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.InternalModelProvider;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRuntimeConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class InternalModelProviderRegistryTest {

    @Test
    void refreshBuildsProviderIdKeyedTokenSnapshot() {
        InternalModelProviderRepository repository = mock(InternalModelProviderRepository.class);
        when(repository.findEnabledRuntimeConfigs()).thenReturn(List.of(
                runtimeConfig("qwen-prod", 11L, "Qwen Token", "shared-qwen-token"),
                runtimeConfig("qwen-canary", 11L, "Qwen Token", "shared-qwen-token"),
                runtimeConfig("deepseek-prod", 12L, "DeepSeek Token", "deepseek-token")));
        InternalModelProviderRegistry registry = new InternalModelProviderRegistry(repository);

        InternalModelProviderSnapshot snapshot = registry.refresh("trace_refresh", "test");

        assertThat(registry.requireRuntimeConfig("qwen-prod").authToken()).isEqualTo("shared-qwen-token");
        assertThat(registry.requireRuntimeConfig("qwen-canary").authToken()).isEqualTo("shared-qwen-token");
        assertThat(registry.requireRuntimeConfig("deepseek-prod").authToken()).isEqualTo("deepseek-token");
        assertThat(snapshot.providers())
                .extracting(InternalModelProvider::providerId)
                .containsExactly("qwen-prod", "qwen-canary", "deepseek-prod");
        assertThat(snapshot.tokenConfigured()).isTrue();
        assertThat(snapshot.toResponse().toString()).doesNotContain("shared-qwen-token", "deepseek-token");
    }

    @Test
    void providerWithoutTokenRemainsVisibleButFailsWithSafeValidationError() {
        InternalModelProviderRepository repository = mock(InternalModelProviderRepository.class);
        when(repository.findEnabledRuntimeConfigs()).thenReturn(List.of(
                runtimeConfig("missing-token", null, null, null)));
        InternalModelProviderRegistry registry = new InternalModelProviderRegistry(repository);
        registry.refresh("trace_missing", "test");

        assertThat(registry.currentSnapshot().providers())
                .singleElement()
                .extracting(InternalModelProvider::providerId)
                .isEqualTo("missing-token");
        assertThat(registry.currentSnapshot().tokenConfigured()).isFalse();
        assertThatThrownBy(() -> registry.requireRuntimeConfig("missing-token"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).contains("missing-token", "Token");
                });
    }

    @Test
    void tokenRotationBecomesVisibleOnlyAfterWholeSnapshotRefresh() {
        InternalModelProviderRepository repository = mock(InternalModelProviderRepository.class);
        when(repository.findEnabledRuntimeConfigs()).thenReturn(
                List.of(runtimeConfig("qwen-prod", 11L, "Qwen Token", "token-before")),
                List.of(runtimeConfig("qwen-prod", 11L, "Qwen Token", "token-after")));
        InternalModelProviderRegistry registry = new InternalModelProviderRegistry(repository);

        registry.refresh("trace_before", "test");
        assertThat(registry.requireRuntimeConfig("qwen-prod").authToken()).isEqualTo("token-before");

        registry.refresh("trace_after", "token-updated");
        assertThat(registry.requireRuntimeConfig("qwen-prod").authToken()).isEqualTo("token-after");
        assertThat(registry.currentSnapshot().traceId()).isEqualTo("trace_after");
        verify(repository, org.mockito.Mockito.times(2)).findEnabledRuntimeConfigs();
    }

    private static InternalModelProviderRuntimeConfig runtimeConfig(
            String providerId,
            Long tokenId,
            String tokenName,
            String authToken) {
        Instant now = Instant.parse("2026-07-22T08:00:00Z");
        return new InternalModelProviderRuntimeConfig(
                new InternalModelProvider(
                        providerId,
                        providerId,
                        "http://" + providerId + ".example/v1",
                        true,
                        1,
                        tokenId,
                        tokenName,
                        authToken != null,
                        now,
                        now),
                authToken);
    }
}
