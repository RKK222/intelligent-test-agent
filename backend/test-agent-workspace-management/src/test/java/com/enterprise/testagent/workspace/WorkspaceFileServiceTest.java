package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceFileServiceTest {

    private static final String APP_SOURCE_INDEX = ".testagent-appsource-index.json";

    @TempDir
    Path root;

    @TempDir
    Path externalRoot;

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
    void unavailableWorkspaceRootDoesNotExposePhysicalPath() {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Path missingRoot = root.resolve("missing-sensitive-root");

        assertThatThrownBy(() -> service.listDirectory(missingRoot.toString(), ""))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.getMessage()).doesNotContain(missingRoot.toString());
                    assertThat(exception.details().toString()).doesNotContain(missingRoot.toString());
                    assertThat(exception.getCause()).isNull();
                });
    }

    @Test
    void serviceDoesNotFollowSymbolicLinks() throws Exception {
        assumeTrue(isUnixLikePlatform());
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.writeString(externalRoot.resolve("external-secret.txt"), "external secret");
        Files.createDirectories(root.resolve(".git"));
        Files.writeString(root.resolve(".git/config"), "git metadata");
        Files.createSymbolicLink(root.resolve("outside-alias"), externalRoot);
        Files.createSymbolicLink(root.resolve("git-alias"), root.resolve(".git"));

        assertThat(service.listDirectory(root.toString(), ""))
                .extracting(FileTreeEntryResponse::name)
                .doesNotContain("outside-alias", "git-alias");
        assertThat(service.searchFiles(root.toString(), "secret"))
                .extracting(FileSearchResultResponse::path)
                .isEmpty();
        assertThatThrownBy(() -> service.listDirectory(root.toString(), "outside-alias"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.readContent(root.toString(), "git-alias/config"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.writeContent(root.toString(), "outside-alias/created.txt", "blocked"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.createDirectory(root.toString(), "outside-alias/created"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(externalRoot.resolve("created.txt")).doesNotExist();
        assertThat(externalRoot.resolve("created")).doesNotExist();
        assertThat(root.resolve(".git/config")).hasContent("git metadata");
    void serviceRejectsAbsoluteAndNormalizedTraversalPathsEvenWhenTheyResolveInsideRoot() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.writeString(root.resolve("secret.txt"), "secret");
        Files.createDirectories(root.resolve("nested"));

        assertThatThrownBy(() -> service.readContent(root.toString(), root.resolve("secret.txt").toString()))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.readContent(root.toString(), "nested/../secret.txt"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void serviceRejectsEveryOperationThroughDirectorySymlink() throws Exception {
        assumeTrue(!System.getProperty("os.name", "").toLowerCase().contains("win"));
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.writeString(externalRoot.resolve("secret.txt"), "outside");
        Path linkedDirectory = root.resolve("linked-outside");
        Files.createSymbolicLink(linkedDirectory, externalRoot);

        assertThatThrownBy(() -> service.listDirectory(root.toString(), "linked-outside"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.readContent(root.toString(), "linked-outside/secret.txt"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.writeContent(root.toString(), "linked-outside/new.txt", "blocked"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.deleteFile(root.toString(), "linked-outside/secret.txt"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(externalRoot.resolve("secret.txt")).hasContent("outside");
        assertThat(externalRoot.resolve("new.txt")).doesNotExist();
        assertThat(service.searchFiles(root.toString(), "secret.txt")).isEmpty();
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
    void serviceHidesAppSourceIndexFromDirectoryAndSearchViews() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1);
        Files.writeString(root.resolve(APP_SOURCE_INDEX), "platform metadata");
        Files.writeString(root.resolve("README.md"), "visible");

        assertThat(service.listDirectory(root.toString(), ""))
                .extracting(FileTreeEntryResponse::name)
                .containsExactly("README.md");
        assertThat(service.searchFiles(root.toString(), ""))
                .extracting(FileSearchResultResponse::name)
                .containsExactly("README.md");
    }

    @Test
    void serviceRejectsDirectReadAndWriteAccessToAppSourceIndex() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.writeString(root.resolve(APP_SOURCE_INDEX), "platform metadata");

        assertThatThrownBy(() -> service.readContent(root.toString(), APP_SOURCE_INDEX))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.readContentChunk(root.toString(), APP_SOURCE_INDEX, 0, null, null))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.readBinaryChunk(root.toString(), APP_SOURCE_INDEX, 0, null, null))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.writeContent(root.toString(), APP_SOURCE_INDEX, "tampered"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThatThrownBy(() -> service.status(root.toString(), APP_SOURCE_INDEX))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(Files.readString(root.resolve(APP_SOURCE_INDEX))).isEqualTo("platform metadata");
    }

    @Test
    void serviceRejectsRenamingOrdinaryFileIntoReservedAppSourceIndexName() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.writeString(root.resolve("ordinary.txt"), "user content");

        assertThatThrownBy(() -> service.renameFile(root.toString(), "ordinary.txt", APP_SOURCE_INDEX))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assertThat(root.resolve("ordinary.txt")).hasContent("user content");
        assertThat(root.resolve(APP_SOURCE_INDEX)).doesNotExist();
    }

    @Test
    void serviceRejectsReadAndWriteWhenFileExceedsConfiguredBytes() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(4, 1000);
        Files.writeString(root.resolve("large.txt"), "12345");

        assertThatThrownBy(() -> service.readContent(root.toString(), "large.txt"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(exception.details()).containsEntry("reason", "PREVIEW_TOO_LARGE");
                    assertThat(exception.details()).containsEntry("size", 5L);
                    assertThat(exception.details()).containsEntry("maxPreviewBytes", 4L);
                });
        assertThatThrownBy(() -> service.writeContent(root.toString(), "new.txt", "12345"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void serviceProgressivelyReadsEntireLargeUtf8FileAcrossCharacterBoundary() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(4, 1000);
        String content = "a".repeat(Utf8FilePreviewReader.CHUNK_BYTES - 1) + "你" + "tail";
        Files.writeString(root.resolve("large.txt"), content);

        FilePreviewChunkResponse first = service.readContentChunk(
                root.toString(), "large.txt", 0L, null, null);
        FilePreviewChunkResponse second = service.readContentChunk(
                root.toString(),
                "large.txt",
                first.nextOffset(),
                first.size(),
                first.lastModifiedMillis());

        assertThat(first.eof()).isFalse();
        assertThat(first.nextOffset()).isGreaterThan(Utf8FilePreviewReader.CHUNK_BYTES);
        assertThat(second.eof()).isTrue();
        assertThat(first.content() + second.content()).isEqualTo(content);
        assertThat(second.nextOffset()).isEqualTo(first.size());
        assertThat(first.warningThresholdBytes()).isEqualTo(4L);
    }

    @Test
    void serviceRejectsContinuingProgressivePreviewAfterFileChanges() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(4, 1000);
        Files.writeString(root.resolve("large.txt"), "a".repeat(Utf8FilePreviewReader.CHUNK_BYTES + 8));
        FilePreviewChunkResponse first = service.readContentChunk(
                root.toString(), "large.txt", 0L, null, null);
        Files.writeString(root.resolve("large.txt"), "changed");

        assertThatThrownBy(() -> service.readContentChunk(
                root.toString(),
                "large.txt",
                first.nextOffset(),
                first.size(),
                first.lastModifiedMillis()))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.details()).containsEntry("reason", "PREVIEW_CHANGED");
                });
    }

    @Test
    void serviceReadsBinaryFileInSnapshotCheckedChunks() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(4, 1000);
        byte[] content = new byte[Utf8FilePreviewReader.CHUNK_BYTES + 3];
        content[0] = 0;
        content[1] = (byte) 0xff;
        content[content.length - 1] = (byte) 0x80;
        Files.write(root.resolve("asset.bin"), content);

        FileBinaryChunkResponse first = service.readBinaryChunk(
                root.toString(), "asset.bin", 0L, null, null);
        FileBinaryChunkResponse second = service.readBinaryChunk(
                root.toString(),
                "asset.bin",
                first.nextOffset(),
                first.size(),
                first.lastModifiedMillis());

        byte[] downloaded = new byte[content.length];
        byte[] firstBytes = Base64.getDecoder().decode(first.contentBase64());
        byte[] secondBytes = Base64.getDecoder().decode(second.contentBase64());
        System.arraycopy(firstBytes, 0, downloaded, 0, firstBytes.length);
        System.arraycopy(secondBytes, 0, downloaded, firstBytes.length, secondBytes.length);
        assertThat(downloaded).containsExactly(content);
        assertThat(first.eof()).isFalse();
        assertThat(second.eof()).isTrue();
        assertThat(second.nextOffset()).isEqualTo(content.length);
    }

    @Test
    void serviceRejectsContinuingBinaryDownloadAfterFileChanges() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(4, 1000);
        Files.write(root.resolve("asset.bin"), new byte[Utf8FilePreviewReader.CHUNK_BYTES + 2]);
        FileBinaryChunkResponse first = service.readBinaryChunk(
                root.toString(), "asset.bin", 0L, null, null);
        Files.write(root.resolve("asset.bin"), new byte[] {1, 2, 3});

        assertThatThrownBy(() -> service.readBinaryChunk(
                root.toString(),
                "asset.bin",
                first.nextOffset(),
                first.size(),
                first.lastModifiedMillis()))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.details()).containsEntry("reason", "DOWNLOAD_CHANGED");
                });
    }

    @Test
    void serviceTreatsNullContentAsEmptyFile() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);

        service.writeContent(root.toString(), "empty.txt", null);

        assertThat(Files.readString(root.resolve("empty.txt"))).isEmpty();
        assertThat(service.readContent(root.toString(), "empty.txt").size()).isZero();
    }

    @Test
    void serviceUploadsBinaryContentWithoutOverwritingExistingFiles() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(8, 1000);
        byte[] content = new byte[] {0, 1, 2, (byte) 255};
        Files.createDirectories(root.resolve("assets"));

        service.uploadFile(root.toString(), "assets/icon.bin", Base64.getEncoder().encodeToString(content));

        assertThat(Files.readAllBytes(root.resolve("assets/icon.bin"))).containsExactly(content);
        assertThatThrownBy(() -> service.uploadFile(root.toString(), "assets/icon.bin", "AA=="))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.getMessage()).isEqualTo("目标文件已存在");
                });
        assertThatThrownBy(() -> service.uploadFile(root.toString(), "assets/bad.bin", "***"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void serviceStreamsFilePastPreviewLimitAndPublishesOnlyAfterComplete() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(4, 1000);
        Files.createDirectories(root.resolve("assets"));
        byte[] first = new byte[] {0, 1, 2, 3, 4};
        byte[] second = new byte[] {5, 6, 7, 8, 9};

        WorkspaceFileUpload upload = service.beginUpload(root.toString(), "assets/large.bin", 10L);
        assertThat(upload.chunkBytes()).isPositive();

        upload.append(0, Base64.getEncoder().encodeToString(first));
        assertThat(upload.uploadedBytes()).isEqualTo(5L);
        assertThat(Files.exists(root.resolve("assets/large.bin"))).isFalse();

        upload.append(1, Base64.getEncoder().encodeToString(second));
        assertThat(upload.complete()).isEqualTo(10L);

        assertThat(Files.readAllBytes(root.resolve("assets/large.bin")))
                .containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
    }

    @Test
    void serviceRejectsOutOfOrderChunkAndAbortRemovesTemporaryFile() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(4, 1000);
        WorkspaceFileUpload upload = service.beginUpload(root.toString(), "large.bin", 2L);

        assertThatThrownBy(() -> upload.append(1, "AA=="))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        upload.abort();

        assertThat(Files.exists(root.resolve("large.bin"))).isFalse();
        assertThat(service.listDirectory(root.toString(), ""))
                .extracting(FileTreeEntryResponse::name)
                .noneMatch(name -> name.startsWith(".test-agent-upload-"));
        assertThat(service.searchFiles(root.toString(), ""))
                .extracting(FileSearchResultResponse::name)
                .noneMatch(name -> name.startsWith(".test-agent-upload-"));
    }

    @Test
    void serviceRejectsCompletingUploadWhenDeclaredSizeDoesNotMatch() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(4, 1000);
        WorkspaceFileUpload upload = service.beginUpload(root.toString(), "large.bin", 2L);
        upload.append(0, "AA==");

        assertThatThrownBy(upload::complete)
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));

        upload.abort();
        assertThat(Files.exists(root.resolve("large.bin"))).isFalse();
    }

    @Test
    void serviceCopiesAndMovesRegularFilesAcrossWorkspaceDirectories() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.createDirectories(root.resolve("src"));
        Files.createDirectories(root.resolve("docs"));
        Files.writeString(root.resolve("src/example.txt"), "example");

        service.copyFile(root.toString(), "src/example.txt", "docs/copied.txt");
        assertThatThrownBy(() -> service.copyFile(root.toString(), "src/example.txt", "docs/copied.txt"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.getMessage()).isEqualTo("目标文件已存在");
                });
        service.moveFile(root.toString(), "src/example.txt", "docs/moved.txt");

        assertThat(Files.readString(root.resolve("docs/copied.txt"))).isEqualTo("example");
        assertThat(Files.readString(root.resolve("docs/moved.txt"))).isEqualTo("example");
        assertThat(Files.exists(root.resolve("src/example.txt"))).isFalse();
        assertThatThrownBy(() -> service.copyFile(root.toString(), "docs/copied.txt", "../outside.txt"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void serviceMovesNonEmptyDirectoryWithSingleFilesystemMove() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.createDirectories(root.resolve("src/nested"));
        Files.createDirectories(root.resolve("archive"));
        Files.writeString(root.resolve("src/nested/case.md"), "case");

        service.moveFile(root.toString(), "src", "archive/src");

        assertThat(Files.exists(root.resolve("src"))).isFalse();
        assertThat(Files.readString(root.resolve("archive/src/nested/case.md"))).isEqualTo("case");
    }

    @Test
    void serviceTreatsDirectoryMoveToSamePathAsIdempotent() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.createDirectories(root.resolve("suite/cases"));
        Files.writeString(root.resolve("suite/cases/case.md"), "case");

        service.moveFile(root.toString(), "suite", "suite");

        assertThat(Files.readString(root.resolve("suite/cases/case.md"))).isEqualTo("case");
    }

    @Test
    void serviceRejectsMovingWorkspaceRootOrDirectoryIntoItsDescendant() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.createDirectories(root.resolve("suite/target"));

        assertThatThrownBy(() -> service.moveFile(root.toString(), "", "renamed-root"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.moveFile(root.toString(), "suite", "suite/target/moved-suite"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void serviceRejectsMovingDirectoryIntoDescendantResolvedThroughWorkspaceAlias() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.createDirectories(root.resolve("suite/target"));
        Files.writeString(root.resolve("suite/case.md"), "case");
        Files.createSymbolicLink(root.resolve("alias"), root.resolve("suite/target"));

        assertThatThrownBy(() -> service.moveFile(root.toString(), "suite", "alias/moved-suite"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(Files.isDirectory(root.resolve("suite"))).isTrue();
        assertThat(Files.readString(root.resolve("suite/case.md"))).isEqualTo("case");
    }

    @Test
    void serviceRejectsMoveWhenSourceMissingTargetExistsOrTargetParentMissing() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.writeString(root.resolve("source.txt"), "source");
        Files.writeString(root.resolve("existing.txt"), "existing");

        assertThatThrownBy(() -> service.moveFile(root.toString(), "missing.txt", "new.txt"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
        assertThatThrownBy(() -> service.moveFile(root.toString(), "source.txt", "existing.txt"))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT);
                    assertThat(exception.getMessage()).isEqualTo("目标文件或目录已存在");
                });
        assertThatThrownBy(() -> service.moveFile(root.toString(), "source.txt", "missing/new.txt"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.NOT_FOUND));
    }

    @Test
    void serviceRejectsSymbolicLinkAndSpecialFileMoveSources() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.createDirectories(root.resolve("target"));
        Path linkedFile = root.resolve("linked.txt");
        Files.createSymbolicLink(linkedFile, root.resolve("target-file.txt"));

        assertThatThrownBy(() -> service.moveFile(root.toString(), "linked.txt", "target/linked.txt"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        assumeTrue(isUnixLikePlatform());
        Path fifo = root.resolve("events.fifo");
        Process process = new ProcessBuilder("mkfifo", fifo.toString()).start();
        assertThat(process.waitFor()).isZero();
        assertThatThrownBy(() -> service.moveFile(root.toString(), "events.fifo", "target/events.fifo"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void serviceRejectsMovePathTraversalAndTargetParentSymlinkOutsideWorkspace() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.writeString(root.resolve("source.txt"), "source");

        assertThatThrownBy(() -> service.moveFile(root.toString(), "source.txt", "../outside.txt"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        Path outsideLink = root.resolve("outside-link");
        Files.createSymbolicLink(outsideLink, externalRoot);
        try {
            assertThatThrownBy(() -> service.moveFile(root.toString(), "source.txt", "outside-link/moved.txt"))
                    .isInstanceOfSatisfying(PlatformException.class, exception ->
                            assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
            assertThat(Files.exists(externalRoot.resolve("moved.txt"))).isFalse();
        } finally {
            Files.deleteIfExists(outsideLink);
        }
    }

    @Test
    void serviceRejectsMoveWhenSourceResolvesOutsideWorkspaceThroughLinkedParent() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Path linkedParent = root.resolve("linked-parent");
        Files.writeString(externalRoot.resolve("source.txt"), "external source");
        Files.createSymbolicLink(linkedParent, externalRoot);

        try {
            assertThatThrownBy(() -> service.moveFile(root.toString(), "linked-parent/source.txt", "moved.txt"))
                    .isInstanceOfSatisfying(PlatformException.class, exception ->
                            assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
            assertThat(Files.readString(externalRoot.resolve("source.txt"))).isEqualTo("external source");
            assertThat(Files.exists(root.resolve("moved.txt"))).isFalse();
        } finally {
            Files.deleteIfExists(linkedParent);
        }
    }

    @Test
    void serviceFailsClosedWhenTargetParentBecomesSymlinkBeforeFilesystemMove() throws Exception {
        Files.writeString(root.resolve("source.txt"), "source");
        Files.createDirectories(root.resolve("target"));
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000, () -> {
            try {
                Files.delete(root.resolve("target"));
                Files.createSymbolicLink(root.resolve("target"), externalRoot);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });

        assertThatThrownBy(() -> service.moveFile(root.toString(), "source.txt", "target/moved.txt"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        assertThat(Files.readString(root.resolve("source.txt"))).isEqualTo("source");
        assertThat(Files.exists(externalRoot.resolve("moved.txt"))).isFalse();
    }

    @Test
    void serviceFailsClosedWhenWorkspaceRootAncestorBecomesSymlinkBeforeFilesystemMove() throws Exception {
        Path container = Files.createDirectories(root.resolve("container"));
        Path workspace = Files.createDirectories(container.resolve("workspace"));
        Files.createDirectories(workspace.resolve("target"));
        Files.writeString(workspace.resolve("source.txt"), "source");
        Path externalWorkspace = Files.createDirectories(externalRoot.resolve("workspace"));
        Files.createDirectories(externalWorkspace.resolve("target"));
        Files.writeString(externalWorkspace.resolve("source.txt"), "external source");
        Path originalContainer = root.resolve("container-original");
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000, () -> {
            try {
                Files.move(container, originalContainer);
                Files.createSymbolicLink(container, externalRoot);
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });

        try {
            assertThatThrownBy(() -> service.moveFile(
                            workspace.toString(), "source.txt", "target/moved.txt"))
                    .isInstanceOfSatisfying(PlatformException.class, exception ->
                            assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
            assertThat(Files.readString(originalContainer.resolve("workspace/source.txt"))).isEqualTo("source");
            assertThat(Files.readString(externalWorkspace.resolve("source.txt"))).isEqualTo("external source");
            assertThat(Files.exists(externalWorkspace.resolve("target/moved.txt"))).isFalse();
        } finally {
            Files.deleteIfExists(container);
            Files.move(originalContainer, container);
        }
    }

    @Test
    void serviceDoesNotOverwriteTargetCreatedImmediatelyBeforeFilesystemMove() throws Exception {
        Files.writeString(root.resolve("source.txt"), "source");
        Files.createDirectories(root.resolve("target"));
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000, () -> {
            try {
                Files.writeString(root.resolve("target/moved.txt"), "concurrent target");
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });

        assertThatThrownBy(() -> service.moveFile(root.toString(), "source.txt", "target/moved.txt"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        assertThat(Files.readString(root.resolve("source.txt"))).isEqualTo("source");
        assertThat(Files.readString(root.resolve("target/moved.txt"))).isEqualTo("concurrent target");
    }

    /**
     * FIFO 仅在类 Unix 平台可创建，其他平台跳过特殊文件断言以保持测试可移植。
     */
    private boolean isUnixLikePlatform() {
        return !System.getProperty("os.name", "").toLowerCase().contains("win");
    }

    @Test
    void serviceRenamesFileOrDirectoryInsideWorkspaceAndRejectsExistingTarget() throws Exception {
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

        Files.createDirectories(root.resolve("suite/cases"));
        Files.writeString(root.resolve("suite/cases/case.md"), "case");
        service.renameFile(root.toString(), "suite", "regression-suite");
        assertThat(Files.readString(root.resolve("regression-suite/cases/case.md"))).isEqualTo("case");
    }

    @Test
    void serviceRejectsUsingPathSeparatorsInNewName() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.writeString(root.resolve("note.txt"), "note");

        assertThatThrownBy(() -> service.renameFile(root.toString(), "note.txt", "nested/name.txt"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void serviceDeletesRegularFilesAndDirectoryTreesInsideWorkspaceRoot() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.writeString(root.resolve("remove.txt"), "delete me");
        Files.createDirectories(root.resolve("directory/nested"));
        Files.writeString(root.resolve("directory/nested/case.md"), "case");

        service.deleteFile(root.toString(), "remove.txt");
        service.deleteFile(root.toString(), "directory");

        assertThat(Files.exists(root.resolve("remove.txt"))).isFalse();
        assertThat(Files.exists(root.resolve("directory"))).isFalse();
    }

    @Test
    void serviceRejectsDeletingWorkspaceRootOrGitMetadata() throws Exception {
        WorkspaceFileService service = new WorkspaceFileService(1024 * 1024, 1000);
        Files.createDirectories(root.resolve("nested/.git"));

        assertThatThrownBy(() -> service.deleteFile(root.toString(), ""))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
        assertThatThrownBy(() -> service.deleteFile(root.toString(), "nested/.git"))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));
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
