package com.icbc.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
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

    @Test
    void serviceRenamesRegularFileInsideWorkspaceAndRejectsExistingTarget() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.createDirectories(root.resolve("docs"));
        Files.writeString(root.resolve("docs/old.md"), "# 内容");
        Files.writeString(root.resolve("docs/existing.md"), "existing");

        service.renameFile(root.toString(), "docs/old.md", "new.md");

        assertThat(Files.exists(root.resolve("docs/old.md"))).isFalse();
        assertThat(Files.readString(root.resolve("docs/new.md"))).isEqualTo("# 内容");
        assertThatThrownBy(() -> service.renameFile(root.toString(), "docs/new.md", "existing.md"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
    }

    @Test
    void serviceRejectsRenamingDirectoryOrUsingPathSeparatorsInNewName() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.createDirectory(root.resolve("directory"));
        Files.writeString(root.resolve("note.txt"), "note");

        assertThatThrownBy(() -> service.renameFile(root.toString(), "directory", "renamed"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.renameFile(root.toString(), "note.txt", "nested/name.txt"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void serviceDeletesOnlyRegularFilesInsideWorkspaceRoot() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.writeString(root.resolve("remove.txt"), "delete me");
        Files.createDirectory(root.resolve("directory"));

        service.deleteFile(root.toString(), "remove.txt");

        assertThat(Files.exists(root.resolve("remove.txt"))).isFalse();
        assertThatThrownBy(() -> service.deleteFile(root.toString(), "directory"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void searchFilesReturnsFilesWhoseNameContainsQueryCaseInsensitively() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.createDirectories(root.resolve("src/components"));
        Files.writeString(root.resolve("src/components/AgentConfig.vue"), "a");
        Files.writeString(root.resolve("src/components/Button.vue"), "b");
        Files.writeString(root.resolve("README.md"), "c");

        // 子串匹配，不区分大小写：query=conf 只命中 AgentConfig.vue
        var results = service.searchFiles(root.toString(), "conf");

        assertThat(results).extracting(FileSearchResultResponse::name).containsExactly("AgentConfig.vue");
        FileSearchResultResponse hit = results.get(0);
        assertThat(hit.path()).isEqualTo("src/components/AgentConfig.vue");
        assertThat(hit.directory()).isEqualTo("src/components");
    }

    @Test
    void searchFilesMatchesRequirementPathAndListsFilesForBlankQuery() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Path requirementFile = root.resolve("120260624-0318-需求项/01-需求/S0001-子条目/需求文档/test.txt");
        Files.createDirectories(requirementFile.getParent());
        Files.writeString(requirementFile, "需求正文");
        Files.writeString(root.resolve("README.md"), "说明");

        assertThat(service.searchFiles(root.toString(), "/01-需求/"))
                .extracting(FileSearchResultResponse::path)
                .containsExactly("120260624-0318-需求项/01-需求/S0001-子条目/需求文档/test.txt");
        assertThat(service.searchFiles(root.toString(), ""))
                .extracting(FileSearchResultResponse::path)
                .containsExactlyInAnyOrder(
                        "120260624-0318-需求项/01-需求/S0001-子条目/需求文档/test.txt",
                        "README.md");
    }

    @Test
    void searchFilesSkipsBlacklistedDirectories() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.createDirectories(root.resolve("node_modules"));
        Files.writeString(root.resolve("node_modules/config.js"), "x");
        Files.writeString(root.resolve("config.json"), "y");

        var results = service.searchFiles(root.toString(), "config");

        // node_modules 被跳过，只命中根目录下的 config.json
        assertThat(results).extracting(FileSearchResultResponse::name).containsExactly("config.json");
    }

    @Test
    void searchFilesReturnsAllFilesForBlankQuery() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.writeString(root.resolve("README.md"), "说明");

        assertThat(service.searchFiles(root.toString(), ""))
                .extracting(FileSearchResultResponse::path)
                .containsExactly("README.md");
        assertThat(service.searchFiles(root.toString(), "   "))
                .extracting(FileSearchResultResponse::path)
                .containsExactly("README.md");
    }
}
