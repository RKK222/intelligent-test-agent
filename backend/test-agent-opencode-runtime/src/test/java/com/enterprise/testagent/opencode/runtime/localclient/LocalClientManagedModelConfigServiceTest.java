package com.enterprise.testagent.opencode.runtime.localclient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRuntimeConfig;
import com.enterprise.testagent.opencode.runtime.internalmodel.InternalModelProviderRegistry;
import com.enterprise.testagent.opencode.runtime.model.ModelCatalogApplicationService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalClientManagedModelConfigServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void internalConfigUsesPublicProviderRoutesAndRemovesServerOnlySecrets() throws Exception {
        Files.writeString(tempDir.resolve("opencode.jsonc"), """
                {
                  // OpenCode provider ID 与 Java 内部路由 ID 故意不同。
                  "model": "enterprise-deepseek/DeepSeek-V4-Flash-W8A8",
                  "small_model": "enterprise-deepseek/DeepSeek-V4-Flash-W8A8",
                  "enabled_providers": ["enterprise-qwen", "enterprise-deepseek", "enterprise-disabled",],
                  "provider": {
                    "enterprise-qwen": {
                      "name": "企业通义",
                      "npm": "@ai-sdk/openai-compatible",
                      "api": "https://server-only.example",
                      "env": ["SERVER_SECRET", "ENTERPRISE_UCID"],
                      "options": {
                        "baseURL": "https://server-only.example",
                        "apiKey": "server-secret",
                        "includeUsage": false,
                        "headers": {
                          "X-Enterprise-Model-Provider": "qwen-prod",
                          "ucid": "001177621"
                        }
                      },
                      "models": {"Qwen3.6-27B": {"name": "Qwen 3.6", "apiKey": "model-secret"}}
                    },
                    "enterprise-deepseek": {
                      "name": "企业 DeepSeek",
                      "npm": "@ai-sdk/openai-compatible",
                      "options": {
                        "headerTimeout": 30000,
                        "chunkTimeout": 120000,
                        "headers": {"X-Enterprise-Model-Provider": "deepseek-prod"}
                      },
                      "models": {"DeepSeek-V4-Flash-W8A8": {"name": "DeepSeek V4"}}
                    },
                    "enterprise-disabled": {
                      "options": {"headers": {"X-Enterprise-Model-Provider": "disabled-prod"}},
                      "models": {"Disabled": {"name": "Disabled"}}
                    }
                  }
                }
                """);
        ModelCatalogApplicationService catalog = mock(ModelCatalogApplicationService.class);
        CommonParameterValues parameters = mock(CommonParameterValues.class);
        InternalModelProviderRegistry registry = mock(InternalModelProviderRegistry.class);
        when(catalog.managedSourceEnabled()).thenReturn(true);
        when(catalog.internalSourceEnabled()).thenReturn(true);
        when(parameters.resolvedValue("OPENCODE_PUBLIC_CONFIG_DIR"))
                .thenReturn(Optional.of(tempDir.toString()));
        when(registry.requireRuntimeConfig("qwen-prod"))
                .thenReturn(mock(InternalModelProviderRuntimeConfig.class));
        when(registry.requireRuntimeConfig("deepseek-prod"))
                .thenReturn(mock(InternalModelProviderRuntimeConfig.class));
        when(registry.requireRuntimeConfig("disabled-prod"))
                .thenThrow(new PlatformException(ErrorCode.VALIDATION_ERROR, "disabled"));

        Map<String, Object> result = new LocalClientManagedModelConfigService(
                catalog, parameters, registry).managedProviderConfig();

        assertThat(result.get("model")).isEqualTo("enterprise-deepseek/DeepSeek-V4-Flash-W8A8");
        assertThat(result.get("enabled_providers"))
                .isEqualTo(List.of("enterprise-qwen", "enterprise-deepseek"));
        assertThat(result.toString())
                .contains("X-Enterprise-Model-Provider=qwen-prod")
                .contains("X-Enterprise-Model-Provider=deepseek-prod")
                .contains("{env:TEST_AGENT_INTERNAL_PROXY_BASE_URL}")
                .contains("{env:TEST_AGENT_INTERNAL_PROXY_API_KEY}")
                .doesNotContain("enterprise-openai")
                .doesNotContain("disabled-prod")
                .doesNotContain("server-secret")
                .doesNotContain("model-secret")
                .doesNotContain("server-only.example")
                .doesNotContain("001177621")
                .doesNotContain("ENTERPRISE_UCID");
    }

    @Test
    void missingPublicConfigFailsClosedForInternalSource() {
        ModelCatalogApplicationService catalog = mock(ModelCatalogApplicationService.class);
        CommonParameterValues parameters = mock(CommonParameterValues.class);
        InternalModelProviderRegistry registry = mock(InternalModelProviderRegistry.class);
        when(catalog.managedSourceEnabled()).thenReturn(true);
        when(catalog.internalSourceEnabled()).thenReturn(true);
        when(parameters.resolvedValue("OPENCODE_PUBLIC_CONFIG_DIR")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new LocalClientManagedModelConfigService(
                catalog, parameters, registry).managedProviderConfig())
                .isInstanceOf(PlatformException.class)
                .extracting(exception -> ((PlatformException) exception).errorCode())
                .isEqualTo(ErrorCode.OPENCODE_UNAVAILABLE);
    }
}
