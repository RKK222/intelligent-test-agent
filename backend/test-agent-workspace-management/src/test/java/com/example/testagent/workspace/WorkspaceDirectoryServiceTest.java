package com.example.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceDirectoryServiceTest {

    @TempDir
    Path root;

    @Test
    void serviceListsOnlyDirectoriesUnderDefaultAllowedRoot() throws Exception {
        Files.createDirectories(root.resolve("zeta"));
        Files.createDirectories(root.resolve("alpha"));
        Files.writeString(root.resolve("notes.txt"), "not a directory");
        WorkspaceDirectoryService service = new WorkspaceDirectoryService(root.toString(), 1000);

        WorkspaceDirectoryListResponse response = service.listDirectories(null);

        assertThat(response.path()).isEqualTo(root.toRealPath().toString());
        assertThat(response.parentPath()).isNull();
        assertThat(response.entries())
                .extracting(WorkspaceDirectoryEntryResponse::name)
                .containsExactly("alpha", "zeta");
    }

    @Test
    void serviceExposesParentOnlyInsideAllowedRoot() throws Exception {
        Path child = Files.createDirectories(root.resolve("child"));
        WorkspaceDirectoryService service = new WorkspaceDirectoryService(root.toString(), 1000);

        WorkspaceDirectoryListResponse response = service.listDirectories(child.toString());

        assertThat(response.path()).isEqualTo(child.toRealPath().toString());
        assertThat(response.parentPath()).isEqualTo(root.toRealPath().toString());
    }

    @Test
    void serviceRejectsDirectoryOutsideAllowedRoots() {
        WorkspaceDirectoryService service = new WorkspaceDirectoryService(root.toString(), 1000);

        assertThatThrownBy(() -> service.listDirectories(root.getParent().toString()))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void serviceRejectsPathTraversalOutsideAllowedRoots() {
        WorkspaceDirectoryService service = new WorkspaceDirectoryService(root.toString(), 1000);

        assertThatThrownBy(() -> service.listDirectories(root.resolve("..").toString()))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void serviceRejectsMissingDirectoryAsValidationError() {
        WorkspaceDirectoryService service = new WorkspaceDirectoryService(root.toString(), 1000);

        assertThatThrownBy(() -> service.listDirectories(root.resolve("missing").toString()))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void serviceRejectsRegularFileAsValidationError() throws Exception {
        Path file = Files.writeString(root.resolve("notes.txt"), "not a directory");
        WorkspaceDirectoryService service = new WorkspaceDirectoryService(root.toString(), 1000);

        assertThatThrownBy(() -> service.listDirectories(file.toString()))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
