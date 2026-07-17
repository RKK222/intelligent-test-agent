package com.enterprise.testagent.domain.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import com.enterprise.testagent.domain.configuration.ResolvedParameter;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ManagedWorkspacePathResolverTest {

    @TempDir
    Path root;

    @Test
    void resolvesLogicalApplicationWorkspacePathFromCommonParameterRoot() {
        ManagedWorkspacePathResolver resolver = new ManagedWorkspacePathResolver(parameters());

        Path resolved = resolver.resolve("appworkspace:20260707/gcms/F-GCMS/workspace");

        assertThat(resolved).isEqualTo(root.resolve("appworkspace/20260707/gcms/F-GCMS/workspace").toAbsolutePath().normalize());
    }

    @Test
    void resolvesLogicalPersonalWorkspacePathFromCommonParameterRoot() {
        ManagedWorkspacePathResolver resolver = new ManagedWorkspacePathResolver(parameters());

        Path resolved = resolver.resolve("personalworktree:20260707/usr_1/gcms/feature_testagent_20260707_usr_1_default/F-GCMS/workspace");

        assertThat(resolved).isEqualTo(root.resolve("personalworktree/20260707/usr_1/gcms/feature_testagent_20260707_usr_1_default/F-GCMS/workspace").toAbsolutePath().normalize());
    }

    @Test
    void keepsUnixAndWindowsAbsolutePathsAsLegacyValues() {
        ManagedWorkspacePathResolver resolver = new ManagedWorkspacePathResolver(parameters());

        assertThat(resolver.resolve("/data/workspace/demo")).isEqualTo(Path.of("/data/workspace/demo").toAbsolutePath().normalize());
        assertThat(resolver.isLegacyAbsolutePath("D:\\data\\.testagent\\workspace")).isTrue();
        assertThat(resolver.isLegacyAbsolutePath("\\\\server\\share\\workspace")).isTrue();
    }

    @Test
    void rejectsLogicalPathTraversal() {
        ManagedWorkspacePathResolver resolver = new ManagedWorkspacePathResolver(parameters());

        assertThatThrownBy(() -> resolver.resolve("appworkspace:20260707/../secret"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void buildsStableLogicalValues() {
        ManagedWorkspacePathResolver resolver = new ManagedWorkspacePathResolver(parameters());

        assertThat(resolver.appValue("20260707", "gcms", "F-GCMS/workspace"))
                .isEqualTo("appworkspace:20260707/gcms/F-GCMS/workspace");
        assertThat(resolver.personalValue("20260707", "usr_1", "gcms", "feature_testagent_20260707_usr_1_default", "F-GCMS/workspace"))
                .isEqualTo("personalworktree:20260707/usr_1/gcms/feature_testagent_20260707_usr_1_default/F-GCMS/workspace");
    }

    private CommonParameterValues parameters() {
        Map<String, String> values = Map.of(
                ManagedWorkspacePathResolver.PARAM_OPENCODE_APP_WORKSPACE_ROOT,
                root.resolve("appworkspace").toString(),
                ManagedWorkspacePathResolver.PARAM_OPENCODE_PERSONAL_WORKTREE_ROOT,
                root.resolve("personalworktree").toString());
        return new CommonParameterValues() {
            @Override
            public Optional<String> resolvedValue(String englishName) {
                return Optional.ofNullable(values.get(englishName));
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
