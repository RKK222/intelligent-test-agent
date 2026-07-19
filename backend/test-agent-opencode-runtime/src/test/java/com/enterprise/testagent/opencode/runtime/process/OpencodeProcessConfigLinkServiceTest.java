package com.enterprise.testagent.opencode.runtime.process;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.CommonParameterValues;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpencodeProcessConfigLinkServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void switchesOneStableLinkBetweenSharedAndPersonalWithoutCopyingFiles() throws Exception {
        Path shared = configRoot("shared", "shared-v1");
        Path personal = configRoot("personal", "personal-v1");
        Path session = tempDir.resolve("session/usr-1");
        OpencodeProcessConfigLinkService service = service(shared);
        Path managed = OpencodeProcessConfigLinkService.managedConfigPath(session.toString());

        service.switchToShared(session.toString(), managed.toString());

        assertThat(Files.isSymbolicLink(managed)).isTrue();
        assertThat(Files.readSymbolicLink(managed)).isEqualTo(shared.toAbsolutePath().normalize());
        assertThat(Files.readString(managed.resolve("opencode.jsonc"))).isEqualTo("shared-v1");

        service.switchTo(personal.toString(), managed.toString());

        assertThat(Files.isSymbolicLink(managed)).isTrue();
        assertThat(Files.readSymbolicLink(managed)).isEqualTo(personal.toAbsolutePath().normalize());
        assertThat(Files.readString(managed.resolve("opencode.jsonc"))).isEqualTo("personal-v1");
        Files.writeString(personal.resolve("opencode.jsonc"), "personal-v2");
        assertThat(Files.readString(managed.resolve("opencode.jsonc"))).isEqualTo("personal-v2");

        service.switchToShared(session.toString(), managed.toString());
        assertThat(Files.readSymbolicLink(managed)).isEqualTo(shared.toAbsolutePath().normalize());
        try (var entries = Files.list(managed.getParent())) {
            assertThat(entries.map(path -> path.getFileName().toString()).toList())
                    .containsExactly("current-public-config");
        }
    }

    @Test
    void rejectsOrdinaryDirectoryAtManagedPathWithoutDeletingUserData() throws Exception {
        Path shared = configRoot("shared", "shared");
        Path session = tempDir.resolve("session/usr-2");
        Path managed = OpencodeProcessConfigLinkService.managedConfigPath(session.toString());
        Files.createDirectories(managed);
        Files.writeString(managed.resolve("keep.txt"), "keep");

        assertThatThrownBy(() -> service(shared).switchToShared(session.toString(), managed.toString()))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("普通文件或目录占用");
        assertThat(Files.readString(managed.resolve("keep.txt"))).isEqualTo("keep");
        assertThat(Files.isSymbolicLink(managed)).isFalse();
    }

    @Test
    void rejectsSymlinkSourceAndNeverFallsBackToCopy() throws Exception {
        Path shared = configRoot("shared", "shared");
        Path sourceLink = tempDir.resolve("linked-source");
        Files.createSymbolicLink(sourceLink, shared);
        Path session = tempDir.resolve("session/usr-3");
        Path managed = OpencodeProcessConfigLinkService.managedConfigPath(session.toString());

        assertThatThrownBy(() -> service(shared).switchTo(sourceLink.toString(), managed.toString()))
                .isInstanceOf(PlatformException.class)
                .hasMessageContaining("源目录不可用");
        assertThat(Files.exists(managed)).isFalse();
    }

    private Path configRoot(String name, String content) throws Exception {
        Path root = tempDir.resolve(name);
        Files.createDirectories(root);
        Files.writeString(root.resolve("opencode.jsonc"), content);
        return root;
    }

    private OpencodeProcessConfigLinkService service(Path shared) {
        CommonParameterValues values = mock(CommonParameterValues.class);
        when(values.resolvedValue(OpencodeProcessConfigLinkService.PUBLIC_CONFIG_PARAMETER))
                .thenReturn(Optional.of(shared.toString()));
        return new OpencodeProcessConfigLinkService(values);
    }
}
