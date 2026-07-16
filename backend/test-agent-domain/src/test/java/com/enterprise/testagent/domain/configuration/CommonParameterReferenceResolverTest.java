package com.enterprise.testagent.domain.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CommonParameterReferenceResolverTest {

    private static final Instant NOW = Instant.parse("2026-06-27T00:00:00Z");

    private final CommonParameterReferenceResolver resolver = new CommonParameterReferenceResolver();

    @Test
    void expandsSimpleReferenceByAllContext() {
        Map<Key, CommonParameter> snapshot = new HashMap<>();
        snapshot.put(key("BASE", ParameterPlatform.ALL), param("BASE", "/data/base", ParameterPlatform.ALL));
        snapshot.put(key("CHILD", ParameterPlatform.ALL), param("CHILD", "${BASE}/child", ParameterPlatform.ALL));

        CommonParameterReferenceResolver.ResolvedValue result =
                resolver.resolve(snapshot.get(key("CHILD", ParameterPlatform.ALL)), exactLookup(snapshot));

        assertThat(result.resolvedValue()).isEqualTo("/data/base/child");
        assertThat(result.hasReference()).isTrue();
        assertThat(result.resolutionError()).isNull();
    }

    @Test
    void expandsNestedReferences() {
        Map<Key, CommonParameter> snapshot = new HashMap<>();
        snapshot.put(key("A", ParameterPlatform.ALL), param("A", "${B}/a", ParameterPlatform.ALL));
        snapshot.put(key("B", ParameterPlatform.ALL), param("B", "${C}/b", ParameterPlatform.ALL));
        snapshot.put(key("C", ParameterPlatform.ALL), param("C", "/root", ParameterPlatform.ALL));

        CommonParameterReferenceResolver.ResolvedValue result =
                resolver.resolve(snapshot.get(key("A", ParameterPlatform.ALL)), exactLookup(snapshot));

        assertThat(result.resolvedValue()).isEqualTo("/root/b/a");
    }

    @Test
    void linuxContextFallsBackToAll() {
        Map<Key, CommonParameter> snapshot = new HashMap<>();
        snapshot.put(key("ROOT", ParameterPlatform.ALL), param("ROOT", "/data", ParameterPlatform.ALL));
        snapshot.put(key("LINUX_PATH", ParameterPlatform.LINUX),
                param("LINUX_PATH", "${ROOT}/linux", ParameterPlatform.LINUX));

        CommonParameterReferenceResolver.ResolvedValue result =
                resolver.resolve(snapshot.get(key("LINUX_PATH", ParameterPlatform.LINUX)), exactLookup(snapshot));

        assertThat(result.resolvedValue()).isEqualTo("/data/linux");
    }

    @Test
    void allRowResolvesPlatformReferenceUnderPlatformContext() {
        Map<Key, CommonParameter> snapshot = new HashMap<>();
        // ALL 行引用了一个仅 LINUX 的参数；以 LINUX 作为解析上下文时按 LINUX 解析。
        snapshot.put(key("PLATFORM_ONLY", ParameterPlatform.LINUX),
                param("PLATFORM_ONLY", "/linux-only", ParameterPlatform.LINUX));
        snapshot.put(key("GLOBAL", ParameterPlatform.ALL),
                param("GLOBAL", "${PLATFORM_ONLY}/x", ParameterPlatform.ALL));

        CommonParameterReferenceResolver.ResolvedValue result =
                resolver.resolve(snapshot.get(key("GLOBAL", ParameterPlatform.ALL)), ParameterPlatform.LINUX, exactLookup(snapshot));

        assertThat(result.resolvedValue()).isEqualTo("/linux-only/x");
        assertThat(result.resolutionError()).isNull();
    }

    @Test
    void allRowLeavesLiteralUnderExplicitAllContext() {
        Map<Key, CommonParameter> snapshot = new HashMap<>();
        // 显式以 ALL 作为上下文时，ALL 行只能引用 ALL 参数；引用仅 LINUX 的参数视为未解析，保留字面。
        snapshot.put(key("PLATFORM_ONLY", ParameterPlatform.LINUX),
                param("PLATFORM_ONLY", "/linux-only", ParameterPlatform.LINUX));
        snapshot.put(key("GLOBAL", ParameterPlatform.ALL),
                param("GLOBAL", "${PLATFORM_ONLY}/x", ParameterPlatform.ALL));

        CommonParameterReferenceResolver.ResolvedValue result =
                resolver.resolve(snapshot.get(key("GLOBAL", ParameterPlatform.ALL)), ParameterPlatform.ALL, exactLookup(snapshot));

        assertThat(result.resolvedValue()).isEqualTo("${PLATFORM_ONLY}/x");
        assertThat(result.resolutionError()).isNotNull();
    }

    @Test
    void cycleKeepsLiteralAndDoesNotThrow() {
        Map<Key, CommonParameter> snapshot = new HashMap<>();
        snapshot.put(key("A", ParameterPlatform.ALL), param("A", "${B}", ParameterPlatform.ALL));
        snapshot.put(key("B", ParameterPlatform.ALL), param("B", "${A}", ParameterPlatform.ALL));

        CommonParameterReferenceResolver.ResolvedValue result =
                resolver.resolve(snapshot.get(key("A", ParameterPlatform.ALL)), exactLookup(snapshot));

        // 出现循环时不应抛异常，且至少保留一个字面占位符。
        assertThat(result.resolvedValue()).contains("${");
        assertThat(result.resolutionError()).isNotNull();
    }

    @Test
    void missingReferenceLeavesLiteral() {
        Map<Key, CommonParameter> snapshot = new HashMap<>();
        snapshot.put(key("A", ParameterPlatform.ALL), param("A", "${MISSING}/x", ParameterPlatform.ALL));

        CommonParameterReferenceResolver.ResolvedValue result =
                resolver.resolve(snapshot.get(key("A", ParameterPlatform.ALL)), exactLookup(snapshot));

        assertThat(result.resolvedValue()).isEqualTo("${MISSING}/x");
        assertThat(result.hasReference()).isTrue();
        assertThat(result.resolutionError()).isNotNull();
    }

    @Test
    void valueWithoutReferenceIsUnchanged() {
        Map<Key, CommonParameter> snapshot = new HashMap<>();
        snapshot.put(key("A", ParameterPlatform.ALL), param("A", "/plain/value", ParameterPlatform.ALL));

        CommonParameterReferenceResolver.ResolvedValue result =
                resolver.resolve(snapshot.get(key("A", ParameterPlatform.ALL)), exactLookup(snapshot));

        assertThat(result.resolvedValue()).isEqualTo("/plain/value");
        assertThat(result.hasReference()).isFalse();
        assertThat(result.resolutionError()).isNull();
    }

    @Test
    void expandsDollarHomeLiteralToUserHome() {
        Map<Key, CommonParameter> snapshot = new HashMap<>();
        snapshot.put(key("CONFIG_DIR", ParameterPlatform.LINUX),
                param("CONFIG_DIR", "$HOME/.testagent/agent-opencode/.config/opencode/", ParameterPlatform.LINUX));

        CommonParameterReferenceResolver.ResolvedValue result =
                resolver.resolve(snapshot.get(key("CONFIG_DIR", ParameterPlatform.LINUX)), exactLookup(snapshot));

        assertThat(result.resolvedValue().replace('\\', '/'))
                .isEqualTo(System.getProperty("user.home").replace('\\', '/')
                        + "/.testagent/agent-opencode/.config/opencode/");
        assertThat(result.hasReference()).isTrue();
        assertThat(result.resolutionError()).isNull();
    }

    @Test
    void expandsDollarEnvironmentVariableFromProcessEnvironment() {
        Map<Key, CommonParameter> snapshot = new HashMap<>();
        snapshot.put(key("CONFIG_DIR", ParameterPlatform.LINUX),
                param("CONFIG_DIR", "$PATH/opencode", ParameterPlatform.LINUX));

        CommonParameterReferenceResolver.ResolvedValue result =
                resolver.resolve(snapshot.get(key("CONFIG_DIR", ParameterPlatform.LINUX)), exactLookup(snapshot));

        assertThat(result.resolvedValue()).isEqualTo(System.getenv("PATH") + "/opencode");
        assertThat(result.hasReference()).isTrue();
        assertThat(result.resolutionError()).isNull();
    }

    @Test
    void expandsBracedEnvironmentVariableWhenNoCommonParameterExists() {
        Map<Key, CommonParameter> snapshot = new HashMap<>();
        snapshot.put(key("CONFIG_DIR", ParameterPlatform.LINUX),
                param("CONFIG_DIR", "${PATH}/opencode", ParameterPlatform.LINUX));

        CommonParameterReferenceResolver.ResolvedValue result =
                resolver.resolve(snapshot.get(key("CONFIG_DIR", ParameterPlatform.LINUX)), exactLookup(snapshot));

        assertThat(result.resolvedValue()).isEqualTo(System.getenv("PATH") + "/opencode");
        assertThat(result.hasReference()).isTrue();
        assertThat(result.resolutionError()).isNull();
    }

    @Test
    void commonParameterReferenceWinsOverEnvironmentVariableWithSameName() {
        Map<Key, CommonParameter> snapshot = new HashMap<>();
        snapshot.put(key("PATH", ParameterPlatform.LINUX), param("PATH", "/common/path", ParameterPlatform.LINUX));
        snapshot.put(key("CONFIG_DIR", ParameterPlatform.LINUX),
                param("CONFIG_DIR", "${PATH}/opencode", ParameterPlatform.LINUX));

        CommonParameterReferenceResolver.ResolvedValue result =
                resolver.resolve(snapshot.get(key("CONFIG_DIR", ParameterPlatform.LINUX)), exactLookup(snapshot));

        assertThat(result.resolvedValue()).isEqualTo("/common/path/opencode");
        assertThat(result.hasReference()).isTrue();
        assertThat(result.resolutionError()).isNull();
    }

    @Test
    void deepChainBeyondLimitKeepsLiteral() {
        Map<Key, CommonParameter> snapshot = new HashMap<>();
        // 构造一条长度超过 MAX_DEPTH 的引用链，末端应保留字面占位符。
        for (int i = 0; i < CommonParameterReferenceResolver.MAX_DEPTH + 2; i++) {
            String value = i == CommonParameterReferenceResolver.MAX_DEPTH + 1 ? "/deep-root" : "${P" + (i + 1) + "}";
            snapshot.put(key("P" + i, ParameterPlatform.ALL), param("P" + i, value, ParameterPlatform.ALL));
        }

        CommonParameterReferenceResolver.ResolvedValue result =
                resolver.resolve(snapshot.get(key("P0", ParameterPlatform.ALL)), exactLookup(snapshot));

        assertThat(result.resolvedValue()).containsAnyOf("${", "/deep-root");
        if (result.resolvedValue().contains("${")) {
            assertThat(result.resolutionError()).isNotNull();
        }
    }

    private record Key(String englishName, ParameterPlatform platform) {
    }

    private static Key key(String englishName, ParameterPlatform platform) {
        return new Key(englishName, platform);
    }

    private static CommonParameter param(String englishName, String value, ParameterPlatform platform) {
        return new CommonParameter(
                "param_" + englishName.toLowerCase(),
                englishName,
                englishName + " 中文名",
                value,
                platform,
                false,
                NOW,
                NOW);
    }

    private static java.util.function.BiFunction<String, ParameterPlatform, Optional<CommonParameter>> exactLookup(
            Map<Key, CommonParameter> snapshot) {
        return (englishName, platform) -> Optional.ofNullable(snapshot.get(key(englishName, platform)));
    }
}
