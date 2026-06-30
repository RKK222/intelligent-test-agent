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

class RepositoryCommonParameterValuesTest {

    private static final Instant NOW = Instant.parse("2026-06-27T00:00:00Z");

    private final CommonParameterReferenceResolver resolver = new CommonParameterReferenceResolver();

    @Test
    void resolvedValueReadsRepositoryEachTimeWithoutReload() {
        FakeRepository repository = new FakeRepository();
        repository.add(param("A", "1", ParameterPlatform.ALL));
        RepositoryCommonParameterValues values = new RepositoryCommonParameterValues(repository, resolver);

        assertThat(values.resolvedValue("A", ParameterPlatform.ALL)).hasValue("1");

        repository.clear();
        repository.add(param("A", "2", ParameterPlatform.ALL));

        assertThat(values.resolvedValue("A", ParameterPlatform.ALL)).hasValue("2");
    }

    @Test
    void resolvedValueFallsBackToAllFromCurrentPlatform() {
        FakeRepository repository = new FakeRepository();
        repository.add(param("GLOBAL", "/g", ParameterPlatform.ALL));
        RepositoryCommonParameterValues values = new RepositoryCommonParameterValues(repository, resolver);

        // 当前平台无精确条目时直接从数据库 ALL 行回退。
        assertThat(values.resolvedValue("GLOBAL")).hasValue("/g");
        assertThat(values.raw("GLOBAL", ParameterPlatform.current())).map(CommonParameter::parameterValue).hasValue("/g");
    }

    @Test
    void missingParameterReturnsEmpty() {
        RepositoryCommonParameterValues values = new RepositoryCommonParameterValues(new FakeRepository(), resolver);

        assertThat(values.resolvedValue("MISSING")).isEmpty();
        assertThat(values.raw("MISSING", ParameterPlatform.ALL)).isEmpty();
        assertThat(values.findAll()).isEmpty();
    }

    @Test
    void allRowResolvesPlatformReferenceByCurrentPlatform() {
        FakeRepository repository = new FakeRepository();
        // SYS_DATA_ROOT_DIR 仅有平台行；OPENCODE_SESSION_DIR 为 all 行引用它。
        repository.add(param("SYS_DATA_ROOT_DIR", "/data/.testagent", ParameterPlatform.LINUX));
        repository.add(param("SYS_DATA_ROOT_DIR", "$HOME/.testagent", ParameterPlatform.MACOS));
        repository.add(param("SYS_DATA_ROOT_DIR", "D:/data/.testagent", ParameterPlatform.WINDOWS));
        repository.add(param("OPENCODE_SESSION_DIR", "${SYS_DATA_ROOT_DIR}/agent-opencode/.session/", ParameterPlatform.ALL));
        RepositoryCommonParameterValues values = new RepositoryCommonParameterValues(repository, resolver);

        // 按当前平台读取 all 行，${SYS_DATA_ROOT_DIR} 应展开为当前平台的值。
        String expectedRoot = switch (ParameterPlatform.current()) {
            case LINUX -> "/data/.testagent";
            case MACOS -> System.getProperty("user.home") + "/.testagent";
            case WINDOWS -> "D:/data/.testagent";
            case ALL -> "/data/.testagent";
        };
        assertThat(values.resolvedValue("OPENCODE_SESSION_DIR"))
                .hasValue(expectedRoot + "/agent-opencode/.session/");
    }

    @Test
    void resolvedAllReadsCurrentDatabaseRowsAndResolvesReferences() {
        FakeRepository repository = new FakeRepository();
        repository.add(param("BASE", "/data", ParameterPlatform.ALL));
        repository.add(param("CHILD", "${BASE}/child", ParameterPlatform.ALL));
        RepositoryCommonParameterValues values = new RepositoryCommonParameterValues(repository, resolver);

        assertThat(values.findAll()).hasSize(2);
        assertThat(values.resolvedAll()).extracting(resolved -> resolved.parameter().englishName())
                .containsExactly("BASE", "CHILD");
        assertThat(values.resolvedValue("CHILD", ParameterPlatform.ALL)).hasValue("/data/child");
        assertThat(values.raw("CHILD", ParameterPlatform.ALL)).map(CommonParameter::parameterValue).hasValue("${BASE}/child");
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
