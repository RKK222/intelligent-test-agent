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
}
