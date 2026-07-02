package com.icbc.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceDirectoryServiceTest {

    @TempDir
    Path root;

    @Test
    void serviceListsOnlyDirectoriesUnderServerDefaultDirectory() throws Exception {
        Files.createDirectories(root.resolve("zeta"));
        Files.createDirectories(root.resolve("alpha"));
        Files.writeString(root.resolve("notes.txt"), "not a directory");
        WorkspaceDirectoryService service = new WorkspaceDirectoryService(1000);

        WorkspaceDirectoryListResponse response = service.listServerDirectories(null, root.toString());

        assertThat(response.path()).isEqualTo(root.toRealPath().toString());
        assertThat(response.parentPath()).isEqualTo(root.toRealPath().getParent().toString());
        assertThat(response.entries())
                .extracting(WorkspaceDirectoryEntryResponse::name)
                .containsExactly("alpha", "zeta");
    }

    @Test
    void serviceExposesParentForServerDirectoryNavigation() throws Exception {
        Path child = Files.createDirectories(root.resolve("child"));
        WorkspaceDirectoryService service = new WorkspaceDirectoryService(1000);

        WorkspaceDirectoryListResponse response = service.listServerDirectories(child.toString(), root.toString());

        assertThat(response.path()).isEqualTo(child.toRealPath().toString());
        assertThat(response.parentPath()).isEqualTo(root.toRealPath().toString());
    }

    @Test
    void serviceHonorsMaxDirectoryEntriesForServerPicker() throws Exception {
        Files.createDirectories(root.resolve("alpha"));
        Files.createDirectories(root.resolve("beta"));
        WorkspaceDirectoryService service = new WorkspaceDirectoryService(1);

        WorkspaceDirectoryListResponse response = service.listServerDirectories(root.toString(), root.toString());

        assertThat(response.entries())
                .extracting(WorkspaceDirectoryEntryResponse::name)
                .containsExactly("alpha");
    }

    @Test
    void serviceRejectsMissingDirectoryAsValidationError() {
        WorkspaceDirectoryService service = new WorkspaceDirectoryService(1000);

        assertThatThrownBy(() -> service.listServerDirectories(root.resolve("missing").toString(), root.toString()))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void serviceRejectsRegularFileAsValidationError() throws Exception {
        Path file = Files.writeString(root.resolve("notes.txt"), "not a directory");
        WorkspaceDirectoryService service = new WorkspaceDirectoryService(1000);

        assertThatThrownBy(() -> service.listServerDirectories(file.toString(), root.toString()))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
