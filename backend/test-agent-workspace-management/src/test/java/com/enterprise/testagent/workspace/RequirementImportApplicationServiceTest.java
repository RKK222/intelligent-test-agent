package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.tcds.TcdsGateway;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RequirementImportApplicationServiceTest {

    @Test
    void requeriesTrustedMetadataAndKeepsSuccessfulDocumentsOnPartialFailure() {
        TcdsGateway gateway = mock(TcdsGateway.class);
        WorkspaceApplicationService workspace = mock(WorkspaceApplicationService.class);
        var requirement = new TcdsGateway.Document("需求说明.txt", URI.create("https://docs.internal/req"), "3");
        var unsupported = new TcdsGateway.Document("设计附件.bin", URI.create("https://docs.internal/design"), "1");
        var child = new TcdsGateway.RequirementSubItem("SI-01", "登录/校验", List.of(requirement, unsupported));
        when(gateway.listApplications("u001")).thenReturn(List.of(new TcdsGateway.Application("个人金融", "PSN")));
        when(gateway.listRequirementItems("u001", "PSN", "2026年8月"))
                .thenReturn(List.of(new TcdsGateway.RequirementItem("I-01", "登录需求", List.of(child))));
        when(gateway.download(any(), anyLong()))
                .thenReturn(new TcdsGateway.DownloadedDocument("需求正文".getBytes(StandardCharsets.UTF_8), "text/plain"));
        when(workspace.fileStatus(any(WorkspaceId.class), anyString()))
                .thenAnswer(invocation -> new FileStatusResponse(invocation.getArgument(1), false, false, 0, null));
        RequirementImportApplicationService service = new RequirementImportApplicationService(gateway, workspace);

        var result = service.importRequirements("u001", new RequirementImportApplicationService.ImportCommand(
                "wrk_1", "PSN", "2026年8月", List.of("SI-01"), "request-1"));

        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.importedFiles()).isEqualTo(1);
        assertThat(result.failedFiles()).isEqualTo(1);
        ArgumentCaptor<String> path = ArgumentCaptor.forClass(String.class);
        verify(workspace).writeFile(any(WorkspaceId.class), path.capture(), anyString());
        assertThat(path.getValue()).isEqualTo("spec/I-01-登录需求/01-需求/SI-01-登录校验/需求文档/需求说明.md");
    }

    @Test
    void rejectsSelectionThatWasNotReturnedByTrustedTcdsQuery() {
        TcdsGateway gateway = mock(TcdsGateway.class);
        WorkspaceApplicationService workspace = mock(WorkspaceApplicationService.class);
        when(gateway.listApplications("u001")).thenReturn(List.of(new TcdsGateway.Application("个人金融", "PSN")));
        when(gateway.listRequirementItems("u001", "PSN", "2026年8月")).thenReturn(List.of());
        RequirementImportApplicationService service = new RequirementImportApplicationService(gateway, workspace);

        assertThatThrownBy(() -> service.importRequirements("u001", new RequirementImportApplicationService.ImportCommand(
                "wrk_1", "PSN", "2026年8月", List.of("../../outside"), "request-1")))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void restoresImportedStatusFromTheSameNormalizedWorkspaceDirectories() {
        TcdsGateway gateway = mock(TcdsGateway.class);
        WorkspaceApplicationService workspace = mock(WorkspaceApplicationService.class);
        var imported = new TcdsGateway.RequirementSubItem("SI-01", "登录/校验", List.of());
        var pending = new TcdsGateway.RequirementSubItem("SI-02", "会话 续期", List.of());
        when(gateway.listRequirementItems("u001", "PSN", "2026年8月"))
                .thenReturn(List.of(new TcdsGateway.RequirementItem(
                        "I-01", "登录需求", List.of(imported, pending))));
        when(workspace.fileStatus(any(WorkspaceId.class), anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(1);
            boolean exists = path.equals("spec/I-01-登录需求")
                    || path.equals("spec/I-01-登录需求/01-需求/SI-01-登录校验");
            return new FileStatusResponse(path, exists, exists, 0, null);
        });
        RequirementImportApplicationService service = new RequirementImportApplicationService(gateway, workspace);

        var result = service.listWorkspaceItems("u001", "wrk_1", "PSN", "2026年8月");

        assertThat(result).singleElement().satisfies(parent -> {
            assertThat(parent.imported()).isTrue();
            assertThat(parent.children()).extracting(
                    RequirementImportApplicationService.SubItemOption::itemNo,
                    RequirementImportApplicationService.SubItemOption::imported)
                    .containsExactly(
                            org.assertj.core.groups.Tuple.tuple("SI-01", true),
                            org.assertj.core.groups.Tuple.tuple("SI-02", false));
        });
    }

    @Test
    void overwritesExistingDocumentWithoutDeletingOtherFiles() {
        TcdsGateway gateway = mock(TcdsGateway.class);
        WorkspaceApplicationService workspace = mock(WorkspaceApplicationService.class);
        var document = new TcdsGateway.Document("需求.txt", URI.create("https://docs.internal/req"), "3");
        var child = new TcdsGateway.RequirementSubItem("SI-01", "登录", List.of(document));
        when(gateway.listApplications("u001")).thenReturn(List.of(new TcdsGateway.Application("个人金融", "PSN")));
        when(gateway.listRequirementItems("u001", "PSN", "2026年8月"))
                .thenReturn(List.of(new TcdsGateway.RequirementItem("I-01", "登录", List.of(child))));
        when(gateway.findFallbackDesignDocument("u001", "SI-01")).thenReturn(java.util.Optional.empty());
        when(gateway.download(document, RequirementImportApplicationService.MAX_DOCUMENT_BYTES))
                .thenReturn(new TcdsGateway.DownloadedDocument("新正文".getBytes(StandardCharsets.UTF_8), "text/plain"));
        when(workspace.fileStatus(any(WorkspaceId.class), anyString())).thenAnswer(invocation -> {
            String path = invocation.getArgument(1);
            return new FileStatusResponse(path, path.endsWith("需求.md"), false, 0, null);
        });
        RequirementImportApplicationService service = new RequirementImportApplicationService(gateway, workspace);

        var result = service.importRequirements("u001", new RequirementImportApplicationService.ImportCommand(
                "wrk_1", "PSN", "2026年8月", List.of("SI-01"), "request-1"));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.importedFiles()).isZero();
        assertThat(result.overwrittenFiles()).isEqualTo(1);
        verify(workspace, times(6)).createDirectory(any(WorkspaceId.class), anyString());
        verify(workspace).writeFile(any(WorkspaceId.class), anyString(), org.mockito.ArgumentMatchers.eq("新正文\n"));
        verify(workspace, never()).deleteFile(any(WorkspaceId.class), anyString());
    }

    @Test
    void importsDocxContentReturnedUnderLegacyDocName() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("历史设计正文");
            document.write(output);
            docx = output.toByteArray();
        }
        TcdsGateway gateway = mock(TcdsGateway.class);
        WorkspaceApplicationService workspace = mock(WorkspaceApplicationService.class);
        var design = new TcdsGateway.Document("需求子条目设计文档.doc", URI.create("https://docs.internal/design"), "1");
        var child = new TcdsGateway.RequirementSubItem("SI-01", "登录", List.of(design));
        when(gateway.listApplications("u001")).thenReturn(List.of(new TcdsGateway.Application("个人金融", "PSN")));
        when(gateway.listRequirementItems("u001", "PSN", "2026年8月"))
                .thenReturn(List.of(new TcdsGateway.RequirementItem("I-01", "登录", List.of(child))));
        when(gateway.download(design, RequirementImportApplicationService.MAX_DOCUMENT_BYTES))
                .thenReturn(new TcdsGateway.DownloadedDocument(docx, "application/octet-stream"));
        when(workspace.fileStatus(any(WorkspaceId.class), anyString()))
                .thenAnswer(invocation -> new FileStatusResponse(invocation.getArgument(1), false, false, 0, null));
        RequirementImportApplicationService service = new RequirementImportApplicationService(gateway, workspace);

        var result = service.importRequirements("u001", new RequirementImportApplicationService.ImportCommand(
                "wrk_1", "PSN", "2026年8月", List.of("SI-01"), "request-doc"));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.importedFiles()).isEqualTo(1);
        verify(workspace).writeFile(
                any(WorkspaceId.class),
                org.mockito.ArgumentMatchers.endsWith("/需求子条目设计文档.md"),
                org.mockito.ArgumentMatchers.contains("历史设计正文"));
    }

    @Test
    void importsFlattenedTextReturnedUnderWordNameWithoutRequiringAWordContainer() {
        TcdsGateway gateway = mock(TcdsGateway.class);
        WorkspaceApplicationService workspace = mock(WorkspaceApplicationService.class);
        var design = new TcdsGateway.Document("历史设计.docx", URI.create("https://docs.internal/design"), "1");
        var child = new TcdsGateway.RequirementSubItem("SI-01", "登录", List.of(design));
        when(gateway.listApplications("u001")).thenReturn(List.of(new TcdsGateway.Application("个人金融", "PSN")));
        when(gateway.listRequirementItems("u001", "PSN", "2026年8月"))
                .thenReturn(List.of(new TcdsGateway.RequirementItem("I-01", "登录", List.of(child))));
        when(gateway.download(design, RequirementImportApplicationService.MAX_DOCUMENT_BYTES))
                .thenReturn(new TcdsGateway.DownloadedDocument(
                        "旧链路压平的设计正文".getBytes(Charset.forName("GB18030")),
                        "application/octet-stream"));
        when(workspace.fileStatus(any(WorkspaceId.class), anyString()))
                .thenAnswer(invocation -> new FileStatusResponse(invocation.getArgument(1), false, false, 0, null));
        RequirementImportApplicationService service = new RequirementImportApplicationService(gateway, workspace);

        var result = service.importRequirements("u001", new RequirementImportApplicationService.ImportCommand(
                "wrk_1", "PSN", "2026年8月", List.of("SI-01"), "request-flat-word"));

        assertThat(result.status()).isEqualTo("SUCCEEDED");
        assertThat(result.importedFiles()).isEqualTo(1);
        verify(workspace).writeFile(
                any(WorkspaceId.class),
                org.mockito.ArgumentMatchers.endsWith("/历史设计.md"),
                org.mockito.ArgumentMatchers.eq("旧链路压平的设计正文\n"));
    }

    @Test
    void rejectsDifferentDocumentsMappedToOneNormalizedTarget() {
        TcdsGateway gateway = mock(TcdsGateway.class);
        WorkspaceApplicationService workspace = mock(WorkspaceApplicationService.class);
        var first = new TcdsGateway.Document("设计/文档.docx", URI.create("https://docs.internal/one"), "1");
        var second = new TcdsGateway.Document("设计文档.docx", URI.create("https://docs.internal/two"), "1");
        var child = new TcdsGateway.RequirementSubItem("SI-01", "登录", List.of(first, second));
        when(gateway.listApplications("u001")).thenReturn(List.of(new TcdsGateway.Application("个人金融", "PSN")));
        when(gateway.listRequirementItems("u001", "PSN", "2026年8月"))
                .thenReturn(List.of(new TcdsGateway.RequirementItem("I-01", "登录", List.of(child))));
        RequirementImportApplicationService service = new RequirementImportApplicationService(gateway, workspace);

        assertThatThrownBy(() -> service.importRequirements(
                "u001",
                new RequirementImportApplicationService.ImportCommand(
                        "wrk_1", "PSN", "2026年8月", List.of("SI-01"), "request-1")))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.PATH_COLLISION);
                    assertThat(exception.details()).containsEntry(
                            "path", "spec/I-01-登录/02-设计/SI-01-登录/开发文档/设计文档.md");
                });
        verify(workspace, never()).writeFile(any(WorkspaceId.class), anyString(), anyString());
    }
}
