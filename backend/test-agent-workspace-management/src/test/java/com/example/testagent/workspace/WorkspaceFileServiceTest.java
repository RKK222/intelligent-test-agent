package com.example.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class WorkspaceFileServiceTest {

    @TempDir
    Path root;

    @Test
    void serviceReadsAndWritesUtf8FilesInsideWorkspaceRoot() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);

        service.writeContent(root.toString(), "tests/example.txt", "hello");

        assertThat(Files.readString(root.resolve("tests/example.txt"))).isEqualTo("hello");
        assertThat(service.readContent(root.toString(), "tests/example.txt").content()).isEqualTo("hello");
        assertThat(service.status(root.toString(), "tests/example.txt").exists()).isTrue();
    }

    @Test
    void serviceRejectsPathTraversalOutsideWorkspaceRoot() {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);

        assertThatThrownBy(() -> service.readContent(root.toString(), "../secret.txt"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void serviceListsSingleDirectoryInSortedOrderWithConfiguredLimit() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 2);
        Files.createDirectories(root.resolve("src"));
        Files.writeString(root.resolve("src/b.txt"), "b");
        Files.writeString(root.resolve("src/a.txt"), "a");
        Files.writeString(root.resolve("src/c.txt"), "c");

        assertThat(service.listDirectory(root.toString(), "src"))
                .extracting(FileTreeEntryResponse::name)
                .containsExactly("a.txt", "b.txt");
    }

    @Test
    void serviceRejectsReadAndWriteWhenFileExceedsConfiguredBytes() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(4, 1000);
        Files.writeString(root.resolve("large.txt"), "12345");

        assertThatThrownBy(() -> service.readContent(root.toString(), "large.txt"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.writeContent(root.toString(), "new.txt", "12345"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void serviceTreatsNullContentAsEmptyFile() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);

        service.writeContent(root.toString(), "empty.txt", null);

        assertThat(Files.readString(root.resolve("empty.txt"))).isEmpty();
        assertThat(service.readContent(root.toString(), "empty.txt").size()).isZero();
    }
}
