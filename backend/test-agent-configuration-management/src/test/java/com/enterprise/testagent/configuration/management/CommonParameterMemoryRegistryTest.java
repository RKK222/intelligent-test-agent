package com.enterprise.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.configuration.CommonParameterMemoryEntry;
import com.enterprise.testagent.domain.configuration.CommonParameterMemoryKey;
import com.enterprise.testagent.domain.configuration.CommonParameterMemoryLoadedValue;
import com.enterprise.testagent.domain.configuration.CommonParameterMemoryLoadException;
import com.enterprise.testagent.domain.configuration.CommonParameterMemoryRefreshStatus;
import com.enterprise.testagent.domain.configuration.CommonParameterReloadedEvent;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;

/** 验证显式内存通用参数注册表的加载、事件匹配和最后有效值保护语义。 */
class CommonParameterMemoryRegistryTest {

    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void rejectsDuplicateEnglishNameAndPlatformKeys() {
        FakeEntry first = entry("BETA", ParameterPlatform.ALL, value("1"));
        FakeEntry duplicate = entry("BETA", ParameterPlatform.ALL, value("2"));

        assertThatThrownBy(() -> new CommonParameterMemoryRegistry(List.of(first, duplicate), CLOCK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("BETA")
                .hasMessageContaining("all");
    }

    @Test
    void startupLoadsEveryEntryAndReturnsStableKeyOrder() {
        FakeEntry beta = entry("BETA", ParameterPlatform.ALL, value("2"));
        FakeEntry alphaLinux = entry("ALPHA", ParameterPlatform.LINUX, value("linux-value"));
        FakeEntry alphaAll = entry("ALPHA", ParameterPlatform.ALL, value("all-value"));
        CommonParameterMemoryRegistry registry = new CommonParameterMemoryRegistry(
                List.of(beta, alphaLinux, alphaAll), CLOCK);

        registry.loadOnStartup();

        assertThat(registry.snapshots())
                .extracting(state -> state.key().englishName() + "/" + state.key().platform().value())
                .containsExactly("ALPHA/all", "ALPHA/linux", "BETA/all");
        assertThat(registry.snapshots())
                .allSatisfy(state -> {
                    assertThat(state.refreshStatus()).isEqualTo(CommonParameterMemoryRefreshStatus.SUCCESS);
                    assertThat(state.loadedAt()).isEqualTo(NOW);
                    assertThat(state.lastRefreshAttemptAt()).isEqualTo(NOW);
                });
    }

    @Test
    void startupExecutesEntriesInStableKeyOrder() {
        List<String> reloadOrder = new ArrayList<>();
        CommonParameterMemoryRegistry registry = new CommonParameterMemoryRegistry(
                List.of(
                        recordingEntry("ZETA", reloadOrder),
                        recordingEntry("ALPHA", reloadOrder),
                        recordingEntry("BETA", reloadOrder)),
                CLOCK);

        registry.loadOnStartup();

        assertThat(reloadOrder).containsExactly("ALPHA", "BETA", "ZETA");
    }

    @Test
    void startupFailureIsFatalAndDoesNotExposeRejectedSourceValue() {
        FakeEntry invalid = entry(
                "NIGHT_EXECUTION_SLOT_CAPACITY",
                ParameterPlatform.ALL,
                new CommonParameterMemoryLoadException("不是正整数"));
        CommonParameterMemoryRegistry registry = new CommonParameterMemoryRegistry(List.of(invalid), CLOCK);

        assertThatThrownBy(registry::loadOnStartup)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NIGHT_EXECUTION_SLOT_CAPACITY")
                .hasMessageNotContaining("secret-like-invalid");
    }

    @Test
    void matchingAndBulkEventsRefreshOnlySelectedEntries() {
        FakeEntry alpha = entry("ALPHA", ParameterPlatform.ALL, value("1"), value("2"), value("3"));
        FakeEntry beta = entry("BETA", ParameterPlatform.ALL, value("10"), value("11"));
        CommonParameterMemoryRegistry registry = new CommonParameterMemoryRegistry(List.of(alpha, beta), CLOCK);
        registry.loadOnStartup();

        registry.onCommonParameterReloaded(event("ALPHA", ParameterPlatform.ALL, "trace_match"));
        assertThat(alpha.reloadCount).isEqualTo(2);
        assertThat(beta.reloadCount).isEqualTo(1);

        registry.onCommonParameterReloaded(event(null, null, "trace_bulk"));
        assertThat(alpha.reloadCount).isEqualTo(3);
        assertThat(beta.reloadCount).isEqualTo(2);
    }

    @Test
    void runtimeFailureRetainsLastSuccessfulValuesAndRecordsSafeFailure() {
        FakeEntry entry = entry(
                "ALPHA",
                ParameterPlatform.ALL,
                value("raw-one", "memory-one"),
                new CommonParameterMemoryLoadException("数据库值无效"));
        CommonParameterMemoryRegistry registry = new CommonParameterMemoryRegistry(List.of(entry), CLOCK);
        registry.loadOnStartup();

        assertThatCode(() -> registry.refreshAll("trace_manual")).doesNotThrowAnyException();

        assertThat(registry.snapshots()).singleElement().satisfies(state -> {
            assertThat(state.sourceValue()).isEqualTo("raw-one");
            assertThat(state.memoryValue()).isEqualTo("memory-one");
            assertThat(state.loadedAt()).isEqualTo(NOW);
            assertThat(state.lastRefreshAttemptAt()).isEqualTo(NOW);
            assertThat(state.refreshStatus()).isEqualTo(CommonParameterMemoryRefreshStatus.FAILED);
            assertThat(state.errorMessage()).isEqualTo("数据库值无效");
        });
    }

    private static CommonParameterReloadedEvent event(
            String englishName,
            ParameterPlatform platform,
            String traceId) {
        return new CommonParameterReloadedEvent(
                englishName,
                platform,
                englishName == null ? null : "param_" + englishName.toLowerCase(),
                traceId,
                "instance-test");
    }

    private static FakeEntry entry(String englishName, ParameterPlatform platform, Object... outcomes) {
        return new FakeEntry(new CommonParameterMemoryKey(englishName, platform), outcomes);
    }

    private static CommonParameterMemoryLoadedValue value(String value) {
        return value(value, value);
    }

    private static CommonParameterMemoryLoadedValue value(String sourceValue, String memoryValue) {
        return new CommonParameterMemoryLoadedValue(sourceValue, memoryValue);
    }

    private static CommonParameterMemoryEntry recordingEntry(String englishName, List<String> reloadOrder) {
        CommonParameterMemoryKey key = new CommonParameterMemoryKey(englishName, ParameterPlatform.ALL);
        return new CommonParameterMemoryEntry() {
            @Override
            public CommonParameterMemoryKey key() {
                return key;
            }

            @Override
            public CommonParameterMemoryLoadedValue reloadFromDatabase() {
                reloadOrder.add(englishName);
                return value(englishName);
            }
        };
    }

    /** 可按队列返回成功值或安全失败的内存参数测试替身。 */
    private static final class FakeEntry implements CommonParameterMemoryEntry {

        private final CommonParameterMemoryKey key;
        private final Queue<Object> outcomes = new ArrayDeque<>();
        private int reloadCount;

        private FakeEntry(CommonParameterMemoryKey key, Object... outcomes) {
            this.key = key;
            this.outcomes.addAll(List.of(outcomes));
        }

        @Override
        public CommonParameterMemoryKey key() {
            return key;
        }

        @Override
        public CommonParameterMemoryLoadedValue reloadFromDatabase() {
            reloadCount++;
            Object outcome = outcomes.remove();
            if (outcome instanceof RuntimeException exception) {
                throw exception;
            }
            return (CommonParameterMemoryLoadedValue) outcome;
        }
    }
}
