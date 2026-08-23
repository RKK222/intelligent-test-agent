package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.git.GitWorkspaceService;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityModels;
import com.enterprise.testagent.domain.localclient.LocalClientPublicCapabilityRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

/** 验证公共服务端发布不会被客户端不可移植 Tool 阻断。 */
class PublicClientCapabilityPackageServiceTest {

    private static final String COMMIT = "c".repeat(40);

    @TempDir
    Path temporaryDirectory;

    @Test
    void persistsServerOnlyWhenToolDependencyIsNotPortable() throws Exception {
        Path gitRoot = temporaryDirectory.resolve("public-config");
        Path config = gitRoot.resolve("opencode");
        Files.createDirectories(config.resolve("tools"));
        Files.writeString(config.resolve("tools/demo.ts"), "import axios from 'axios'\nexport default axios\n");
        Files.writeString(config.resolve("tools/package.json"),
                "{\"type\":\"module\",\"dependencies\":{\"@opencode-ai/plugin\":\"1.18.4\"}}");

        Path runtime = temporaryDirectory.resolve("runtime");
        Path nodeModules = runtime.resolve("node_modules/@opencode-ai/plugin");
        Files.createDirectories(nodeModules);
        Files.writeString(nodeModules.resolve("package.json"),
                "{\"name\":\"@opencode-ai/plugin\",\"version\":\"1.18.4\"}");
        Files.writeString(nodeModules.resolve("index.js"), "export const tool = value => value\n");
        Path lock = runtime.resolve("package-lock.json");
        Files.writeString(lock,
                "{\"lockfileVersion\":3,\"packages\":{\"\":{\"dependencies\":{\"@opencode-ai/plugin\":\"1.18.4\"}},"
                        + "\"node_modules/@opencode-ai/plugin\":{\"version\":\"1.18.4\"}}}");

        LocalClientPublicCapabilityRepository repository = mock(LocalClientPublicCapabilityRepository.class);
        GitWorkspaceService git = mock(GitWorkspaceService.class);
        when(repository.findReleaseBySourceCommit(COMMIT)).thenReturn(Optional.empty());
        when(repository.findLatestAvailableRelease()).thenReturn(Optional.empty());
        when(git.headCommit(gitRoot)).thenReturn(COMMIT);
        PublicClientCapabilityPackageService service = new PublicClientCapabilityPackageService(
                repository,
                git,
                new ObjectMapper(),
                lock,
                runtime.resolve("node_modules"),
                Clock.fixed(Instant.parse("2026-08-23T00:00:00Z"), ZoneOffset.UTC));

        LocalClientPublicCapabilityModels.Release result = service.generateForPublishedCommit(
                gitRoot, COMMIT, "trace_server_only");

        assertThat(result.compatibility()).isEqualTo(LocalClientPublicCapabilityModels.Compatibility.SERVER_ONLY);
        assertThat(result.errorCode()).isEqualTo("TOOL_DEPENDENCY_UNDECLARED");
        assertThat(result.artifact()).isNull();
        ArgumentCaptor<LocalClientPublicCapabilityModels.Release> release =
                ArgumentCaptor.forClass(LocalClientPublicCapabilityModels.Release.class);
        verify(repository).insertRelease(release.capture());
        assertThat(release.getValue().sourceCommit()).isEqualTo(COMMIT);
        verify(repository, never()).markUpdateAvailableForCapableInstances(
                any(), any(), any(), eq(PublicClientCapabilityPackageService.PROTOCOL_CAPABILITY));
    }
}
