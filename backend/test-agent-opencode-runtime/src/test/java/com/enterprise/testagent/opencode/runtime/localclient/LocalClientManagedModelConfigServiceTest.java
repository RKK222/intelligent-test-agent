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
                  "agents": {"title": {"model": "enterprise-deepseek/DeepSeek-V4-Flash-W8A8"}},
                  "experimental": {"policies": [
                    {"action": "provider.use", "resource": "*", "effect": "deny"},
                    {"action": "provider.use", "resource": "enterprise-qwen", "effect": "allow"},
                    {"action": "provider.use", "resource": "enterprise-deepseek", "effect": "allow"},
                    {"action": "provider.use", "resource": "enterprise-disabled", "effect": "allow"},
                  ]},
                  "providers": {
                    "enterprise-qwen": {
                      "name": "企业通义",
                      "package": "aisdk:@ai-sdk/openai-compatible",
                      "env": ["SERVER_SECRET", "ENTERPRISE_UCID"],
                      "settings": {
                        "baseURL": "https://server-only.example",
                        "apiKey": "server-secret",
                        "includeUsage": false
                      },
                      "headers": {
                        "X-Enterprise-Model-Provider": "qwen-prod",
                        "ucid": "001177621"
                      },
                      "models": {"Qwen3.6-27B": {
                        "name": "Qwen 3.6", "modelID": "Qwen3.6-27B",
                        "capabilities": {"tools": true, "input": ["text"], "output": ["text"]},
                        "compatibility": {"reasoningField": "reasoning_content"},
                        "settings": {"apiKey": "model-secret"},
                        "limit": {"context": 200000, "output": 8192}
                      }}
                    },
                    "enterprise-deepseek": {
                      "name": "企业 DeepSeek",
                      "package": "aisdk:@ai-sdk/openai-compatible",
                      "settings": {
                        "headerTimeout": 30000,
                        "chunkTimeout": 120000
                      },
                      "headers": {"X-Enterprise-Model-Provider": "deepseek-prod"},
                      "models": {"DeepSeek-V4-Flash-W8A8": {
                        "name": "DeepSeek V4",
                        "capabilities": {"tools": true, "input": ["text"], "output": ["text"]}
                      }}
                    },
                    "enterprise-disabled": {
                      "headers": {"X-Enterprise-Model-Provider": "disabled-prod"},
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
                .contains("npm=@ai-sdk/openai-compatible")
                .contains("interleaved={field=reasoning_content}")
                .doesNotContain("enterprise-openai")
                .doesNotContain("disabled-prod")
                .doesNotContain("server-secret")
                .doesNotContain("model-secret")
                .doesNotContain("server-only.example")
                .doesNotContain("001177621")
                .doesNotContain("ENTERPRISE_UCID");
    }

    @Test
    void v2PolicyLastMatchCanDenyAnEarlierAllowedProvider() throws Exception {
        Files.writeString(tempDir.resolve("opencode.jsonc"), """
                {
                  "model": "enterprise-qwen/Qwen3.6-27B",
                  "experimental": {"policies": [
                    {"action": "provider.use", "resource": "*", "effect": "deny"},
                    {"action": "provider.use", "resource": "enterprise-qwen", "effect": "allow"},
                    {"action": "provider.use", "resource": "enterprise-deepseek", "effect": "allow"},
                    {"action": "provider.use", "resource": "enterprise-qwen", "effect": "deny"}
                  ]},
                  "providers": {
                    "enterprise-qwen": {
                      "headers": {"X-Enterprise-Model-Provider": "qwen-prod"},
                      "models": {"Qwen3.6-27B": {"capabilities": {"tools": true}}}
                    },
                    "enterprise-deepseek": {
                      "headers": {"X-Enterprise-Model-Provider": "deepseek-prod"},
                      "models": {"DeepSeek-V4-Flash-W8A8": {"capabilities": {"tools": true}}}
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
        when(registry.requireRuntimeConfig("deepseek-prod"))
                .thenReturn(mock(InternalModelProviderRuntimeConfig.class));

        Map<String, Object> result = new LocalClientManagedModelConfigService(
                catalog, parameters, registry).managedProviderConfig();

        assertThat(result.get("enabled_providers")).isEqualTo(List.of("enterprise-deepseek"));
        assertThat(result.get("model")).isEqualTo("enterprise-deepseek/DeepSeek-V4-Flash-W8A8");
        assertThat(result.toString()).doesNotContain("enterprise-qwen", "qwen-prod");
    }

    @Test
    void legacyPublicConfigStillProducesLoopbackHandshake() throws Exception {
        Files.writeString(tempDir.resolve("opencode.jsonc"), """
                {
                  "model": "enterprise-qwen/Qwen3.6-27B",
                  "small_model": "enterprise-qwen/Qwen3.6-27B",
                  "enabled_providers": ["enterprise-qwen"],
                  "provider": {
                    "enterprise-qwen": {
                      "npm": "@ai-sdk/openai-compatible",
                      "api": "https://server-only.example",
                      "options": {
                        "apiKey": "server-secret",
                        "headers": {
                          "X-Enterprise-Model-Provider": "qwen-prod",
                          "ucid": "private-user"
                        }
                      },
                      "models": {"Qwen3.6-27B": {"name": "Qwen 3.6", "tool_call": true}}
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

        Map<String, Object> result = new LocalClientManagedModelConfigService(
                catalog, parameters, registry).managedProviderConfig();

        assertThat(result.get("model")).isEqualTo("enterprise-qwen/Qwen3.6-27B");
        assertThat(result.get("enabled_providers")).isEqualTo(List.of("enterprise-qwen"));
        assertThat(result.toString())
                .contains("X-Enterprise-Model-Provider=qwen-prod")
                .contains("{env:TEST_AGENT_INTERNAL_PROXY_BASE_URL}")
                .doesNotContain("server-secret", "server-only.example", "private-user");
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
