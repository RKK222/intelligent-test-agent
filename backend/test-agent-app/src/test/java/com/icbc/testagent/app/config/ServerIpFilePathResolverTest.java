package com.icbc.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.domain.configuration.CommonParameter;
import com.icbc.testagent.domain.configuration.CommonParameterValues;
import com.icbc.testagent.domain.configuration.ParameterPlatform;
import com.icbc.testagent.domain.configuration.ResolvedParameter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ServerIpFilePathResolverTest {

    @Test
    void resolvesServerIpFileFromSysDataRootDirCommonParameter() {
        ServerIpFilePathResolver resolver = new ServerIpFilePathResolver(values(Map.of(
                "SYS_DATA_ROOT_DIR", "/tmp/test-agent-data")));

        assertThat(resolver.resolve()).isEqualTo(Path.of("/tmp/test-agent-data/.serverip"));
    }

    @Test
    void rejectsMissingSysDataRootDir() {
        ServerIpFilePathResolver resolver = new ServerIpFilePathResolver(values(Map.of()));

        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("通用参数未配置：SYS_DATA_ROOT_DIR");
    }

    @Test
    void rejectsBlankSysDataRootDir() {
        ServerIpFilePathResolver resolver = new ServerIpFilePathResolver(values(Map.of(
                "SYS_DATA_ROOT_DIR", "  ")));

        assertThatThrownBy(resolver::resolve)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("通用参数未配置：SYS_DATA_ROOT_DIR");
    }

    private static CommonParameterValues values(Map<String, String> resolved) {
        return new CommonParameterValues() {
            @Override
            public Optional<String> resolvedValue(String englishName) {
                return Optional.ofNullable(resolved.get(englishName));
            }

            @Override
            public Optional<String> resolvedValue(String englishName, ParameterPlatform platform) {
                return resolvedValue(englishName);
            }

            @Override
            public Optional<CommonParameter> raw(String englishName, ParameterPlatform platform) {
                return Optional.empty();
            }

            @Override
            public List<CommonParameter> findAll() {
                return List.of();
            }

            @Override
            public List<ResolvedParameter> resolvedAll() {
                return List.of();
            }
        };
    }
}
