package com.enterprise.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.configuration.management.CommonParameterMemoryResponses.ProcessStatus;
import com.enterprise.testagent.domain.configuration.CommonParameterMemoryEntry;
import com.enterprise.testagent.domain.configuration.CommonParameterMemoryKey;
import com.enterprise.testagent.domain.configuration.CommonParameterMemoryLoadedValue;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import org.junit.jupiter.api.Test;

/** 验证本机内存参数应用服务只暴露安全状态，并正确汇总逐条刷新结果。 */
class CommonParameterMemoryApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-19T12:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void returnsCurrentProcessIdentityAndLoadedValues() {
        FakeEntry entry = new FakeEntry("NIGHT_EXECUTION_SLOT_CAPACITY", "20");
        CommonParameterMemoryRegistry registry = new CommonParameterMemoryRegistry(List.of(entry), CLOCK);
        registry.loadOnStartup();
        CommonParameterMemoryApplicationService service = new CommonParameterMemoryApplicationService(
                registry, identity(), CLOCK);

        var response = service.current();

        assertThat(response.backendProcessId()).isEqualTo("bjp_current");
        assertThat(response.linuxServerId()).isEqualTo("server-a");
        assertThat(response.listenUrl()).isEqualTo("http://server-a:8080");
        assertThat(response.instanceId()).isEqualTo("instance-a");
        assertThat(response.capturedAt()).isEqualTo(NOW);
        assertThat(response.status()).isEqualTo(ProcessStatus.SUCCESS);
        assertThat(response.parameters()).singleElement().satisfies(parameter -> {
            assertThat(parameter.englishName()).isEqualTo("NIGHT_EXECUTION_SLOT_CAPACITY");
            assertThat(parameter.platform()).isEqualTo("all");
            assertThat(parameter.sourceValue()).isEqualTo("20");
            assertThat(parameter.memoryValue()).isEqualTo("20");
            assertThat(parameter.loadedAt()).isEqualTo(NOW);
            assertThat(parameter.refreshStatus()).isEqualTo("SUCCESS");
            assertThat(parameter.errorMessage()).isNull();
        });
    }

    @Test
    void refreshReturnsPartialAndRetainsFailedEntryValue() {
        FakeEntry alpha = new FakeEntry("ALPHA", "old-alpha", new IllegalStateException("secret"));
        FakeEntry beta = new FakeEntry("BETA", "old-beta", "new-beta");
        CommonParameterMemoryRegistry registry = new CommonParameterMemoryRegistry(List.of(alpha, beta), CLOCK);
        registry.loadOnStartup();
        CommonParameterMemoryApplicationService service = new CommonParameterMemoryApplicationService(
                registry, identity(), CLOCK);

        var response = service.refresh("trace_manual");

        assertThat(response.status()).isEqualTo(ProcessStatus.PARTIAL);
        assertThat(response.errorCode()).isEqualTo("REFRESH_PARTIAL");
        assertThat(response.parameters()).extracting(item -> item.englishName() + ":" + item.memoryValue())
                .containsExactly("ALPHA:old-alpha", "BETA:new-beta");
        assertThat(response.parameters()).filteredOn(item -> item.englishName().equals("ALPHA"))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.refreshStatus()).isEqualTo("FAILED");
                    assertThat(item.errorMessage()).isEqualTo("读取或应用失败");
                });
    }

    private static BackendInstanceIdentity identity() {
        return new BackendInstanceIdentity() {
            @Override public String instanceId() { return "instance-a"; }
            @Override public String linuxServerId() { return "server-a"; }
            @Override public String backendProcessId() { return "bjp_current"; }
            @Override public String listenUrl() { return "http://server-a:8080"; }
        };
    }

    /** 依次返回字符串成功值或抛出异常的简单内存条目。 */
    private static final class FakeEntry implements CommonParameterMemoryEntry {

        private final CommonParameterMemoryKey key;
        private final Queue<Object> outcomes = new ArrayDeque<>();

        private FakeEntry(String englishName, Object... outcomes) {
            this.key = new CommonParameterMemoryKey(englishName, ParameterPlatform.ALL);
            this.outcomes.addAll(List.of(outcomes));
        }

        @Override
        public CommonParameterMemoryKey key() {
            return key;
        }

        @Override
        public CommonParameterMemoryLoadedValue reloadFromDatabase() {
            Object outcome = outcomes.remove();
            if (outcome instanceof RuntimeException exception) {
                throw exception;
            }
            String value = (String) outcome;
            return new CommonParameterMemoryLoadedValue(value, value);
        }
    }
}
