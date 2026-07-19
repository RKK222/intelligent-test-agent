package com.enterprise.testagent.opencode.runtime.night;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.configuration.CommonParameterMemoryLoadException;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 验证夜间容量条目从数据库严格加载，并只在完整校验成功后替换业务内存快照。 */
class NightExecutionCapacityRegistryTest {

    @Test
    void exposesStableMemoryParameterKey() {
        NightExecutionCapacityRegistry registry = new NightExecutionCapacityRegistry(mock(CommonParameterValues.class));

        assertThat(registry.key().englishName()).isEqualTo("NIGHT_EXECUTION_SLOT_CAPACITY");
        assertThat(registry.key().platform()).isEqualTo(ParameterPlatform.ALL);
    }

    @Test
    void loadsPositiveCapacityFromDatabase() {
        CommonParameterValues values = mock(CommonParameterValues.class);
        when(values.resolvedValue("NIGHT_EXECUTION_SLOT_CAPACITY", ParameterPlatform.ALL))
                .thenReturn(Optional.of("20"));
        NightExecutionCapacityRegistry registry = new NightExecutionCapacityRegistry(values);

        var loaded = registry.reloadFromDatabase();

        assertThat(loaded.sourceValue()).isEqualTo("20");
        assertThat(loaded.memoryValue()).isEqualTo("20");
        assertThat(registry.currentCapacity()).isEqualTo(20);
    }

    @Test
    void keepsResolvedDatabaseSourceTextAndNormalizesEffectiveMemoryValue() {
        CommonParameterValues values = mock(CommonParameterValues.class);
        when(values.resolvedValue("NIGHT_EXECUTION_SLOT_CAPACITY", ParameterPlatform.ALL))
                .thenReturn(Optional.of(" 020 "));
        NightExecutionCapacityRegistry registry = new NightExecutionCapacityRegistry(values);

        var loaded = registry.reloadFromDatabase();

        assertThat(loaded.sourceValue()).isEqualTo(" 020 ");
        assertThat(loaded.memoryValue()).isEqualTo("20");
        assertThat(registry.currentCapacity()).isEqualTo(20);
    }

    @Test
    void rejectsMissingOrInvalidCapacityWithoutEchoingRawValue() {
        for (Optional<String> invalid : List.of(
                Optional.<String>empty(),
                Optional.of(" "),
                Optional.of("secret-like-invalid"),
                Optional.of("0"),
                Optional.of("-1"),
                Optional.of("2147483648"))) {
            CommonParameterValues values = mock(CommonParameterValues.class);
            when(values.resolvedValue("NIGHT_EXECUTION_SLOT_CAPACITY", ParameterPlatform.ALL))
                    .thenReturn(invalid);
            NightExecutionCapacityRegistry registry = new NightExecutionCapacityRegistry(values);

            assertThatThrownBy(registry::reloadFromDatabase)
                    .isInstanceOf(CommonParameterMemoryLoadException.class)
                    .hasMessageNotContaining("secret-like-invalid");
        }
    }

    @Test
    void failedReloadRetainsLastValidCapacity() {
        CommonParameterValues values = mock(CommonParameterValues.class);
        when(values.resolvedValue("NIGHT_EXECUTION_SLOT_CAPACITY", ParameterPlatform.ALL))
                .thenReturn(Optional.of("20"), Optional.of("invalid"));
        NightExecutionCapacityRegistry registry = new NightExecutionCapacityRegistry(values);
        registry.reloadFromDatabase();

        assertThatThrownBy(registry::reloadFromDatabase)
                .isInstanceOf(CommonParameterMemoryLoadException.class);
        assertThat(registry.currentCapacity()).isEqualTo(20);
    }
}
