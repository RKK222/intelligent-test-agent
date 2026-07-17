package com.enterprise.testagent.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.configuration.ResolvedParameter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ServerIdentityFilePathResolverTest {

    @Test
    void resolvesServerIdentityAndHostFilesFromSysDataRootDirCommonParameter() {
        ServerIdentityFilePathResolver resolver = new ServerIdentityFilePathResolver(values(Map.of(
                "SYS_DATA_ROOT_DIR", "/tmp/test-agent-data")));

        assertThat(resolver.serverIdFile()).isEqualTo(Path.of("/tmp/test-agent-data/.serverid"));
        assertThat(resolver.serverHostFile()).isEqualTo(Path.of("/tmp/test-agent-data/.serverhost"));
    }

    @Test
    void rejectsMissingSysDataRootDir() {
        ServerIdentityFilePathResolver resolver = new ServerIdentityFilePathResolver(values(Map.of()));

        assertThatThrownBy(resolver::serverIdFile)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("通用参数未配置：SYS_DATA_ROOT_DIR");
    }

    @Test
    void rejectsBlankSysDataRootDir() {
        ServerIdentityFilePathResolver resolver = new ServerIdentityFilePathResolver(values(Map.of(
                "SYS_DATA_ROOT_DIR", "  ")));

        assertThatThrownBy(resolver::serverHostFile)
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
