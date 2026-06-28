package com.icbc.testagent.configuration.management;

import static org.assertj.core.api.Assertions.assertThat;

import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.CommonParameterReferenceResolver;
import com.icbc.testagent.domain.configuration.CommonParameterRepository;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InMemoryCommonParameterValuesTest {

    private static final Instant NOW = Instant.parse("2026-06-27T00:00:00Z");

    private final CommonParameterReferenceResolver resolver = new CommonParameterReferenceResolver();

    @Test
    void reloadLoadsAllAndResolvesReferences() {
        FakeRepository repository = new FakeRepository();
        repository.add(param("BASE", "/data", ParameterPlatform.ALL));
        repository.add(param("CHILD", "${BASE}/child", ParameterPlatform.ALL));
        InMemoryCommonParameterValues values = new InMemoryCommonParameterValues(repository, resolver);

        values.reload();

        assertThat(values.resolvedValue("CHILD", ParameterPlatform.ALL)).hasValue("/data/child");
        assertThat(values.raw("CHILD", ParameterPlatform.ALL)).map(CommonParameter::parameterValue).hasValue("${BASE}/child");
        assertThat(values.findAll()).hasSize(2);
        assertThat(values.resolvedAll()).hasSize(2);
    }

    @Test
    void resolvedValueFallsBackToAllFromCurrentPlatform() {
        FakeRepository repository = new FakeRepository();
        repository.add(param("GLOBAL", "/g", ParameterPlatform.ALL));
        InMemoryCommonParameterValues values = new InMemoryCommonParameterValues(repository, resolver);
        values.reload();

        // 当前平台（测试机通常为 linux）无 LINUX 条目，回退 ALL。
        assertThat(values.resolvedValue("GLOBAL")).hasValue("/g");
        assertThat(values.raw("GLOBAL", ParameterPlatform.current())).map(CommonParameter::parameterValue).hasValue("/g");
    }

    @Test
    void missingParameterReturnsEmpty() {
        InMemoryCommonParameterValues values = new InMemoryCommonParameterValues(new FakeRepository(), resolver);
        values.reload();

        assertThat(values.resolvedValue("MISSING")).isEmpty();
        assertThat(values.raw("MISSING", ParameterPlatform.ALL)).isEmpty();
        assertThat(values.findAll()).isEmpty();
    }

    @Test
    void reloadAtomicallyReplacesSnapshot() {
        FakeRepository repository = new FakeRepository();
        repository.add(param("A", "1", ParameterPlatform.ALL));
        InMemoryCommonParameterValues values = new InMemoryCommonParameterValues(repository, resolver);
        values.reload();
        assertThat(values.resolvedValue("A", ParameterPlatform.ALL)).hasValue("1");

        repository.clear();
        repository.add(param("A", "2", ParameterPlatform.ALL));
        values.reload();

        assertThat(values.resolvedValue("A", ParameterPlatform.ALL)).hasValue("2");
    }

    private static CommonParameter param(String englishName, String value, ParameterPlatform platform) {
        return new CommonParameter(
                "param_" + englishName.toLowerCase(),
                englishName,
                englishName + " 中文名",
                value,
                platform,
                NOW,
                NOW);
    }

    private static final class FakeRepository implements CommonParameterRepository {
        private final List<CommonParameter> parameters = new ArrayList<>();

        void add(CommonParameter parameter) {
            parameters.add(parameter);
        }

        void clear() {
            parameters.clear();
        }

        @Override
        public Optional<CommonParameter> findByEnglishNameAndPlatform(String englishName, ParameterPlatform platform) {
            return parameters.stream()
                    .filter(p -> p.englishName().equals(englishName) && p.platform() == platform)
                    .findFirst();
        }

        @Override
        public List<CommonParameter> findAll() {
            return List.copyOf(parameters);
        }
    }
}
