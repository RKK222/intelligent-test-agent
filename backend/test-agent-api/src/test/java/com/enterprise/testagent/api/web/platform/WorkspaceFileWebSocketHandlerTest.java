package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceReplica;
import com.enterprise.testagent.domain.appsource.AppSourceReplicaStatus;
import com.enterprise.testagent.domain.appsource.AppSourceRepository;
import com.enterprise.testagent.domain.appsource.AppSourceRepositorySlot;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshot;
import com.enterprise.testagent.domain.appsource.AppSourceSnapshotStatus;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.workspace.FileContentResponse;
import com.enterprise.testagent.workspace.FileSearchResultResponse;
import com.enterprise.testagent.workspace.FileTreeEntryResponse;
import com.enterprise.testagent.workspace.FileBinaryChunkResponse;
import com.enterprise.testagent.workspace.FilePreviewChunkResponse;
import com.enterprise.testagent.workspace.WorkspaceApplicationService;
import com.enterprise.testagent.workspace.WorkspaceDirectoryService;
import com.enterprise.testagent.workspace.WorkspaceFileUpload;
import com.enterprise.testagent.workspace.AgentConfigApplicationService;
import com.enterprise.testagent.workspace.ApplicationAutomationReferenceWorkspaceReconciliationService;
import com.enterprise.testagent.workspace.ManagedConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.workspace.WorkspaceViewApplicationService;
import com.enterprise.testagent.workspace.WorkspaceViewEntry;
import com.enterprise.testagent.workspace.WorkspaceViewListResponse;
import com.enterprise.testagent.workspace.WorkspaceViewLocator;
import com.enterprise.testagent.workspace.WorkspaceViewLocatorKind;
import com.enterprise.testagent.workspace.WorkspaceViewReadResponse;
import com.enterprise.testagent.workspace.WorkspaceViewSource;
import com.enterprise.testagent.workspace.RequirementImportApplicationService;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.opencodeprocess.LinuxServerId;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.automationreference.AutomationReferenceRunPreparation;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAvailability;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessFileRoutingAffinity;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientWorkspaceFileGateway;
import com.enterprise.testagent.system.supportaccess.SupportAccessAuthorization;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class WorkspaceFileWebSocketHandlerTest {

    private static final String TRACE_ID = "trace_1234567890abcdef";
    private static final Instant NOW = Instant.parse("2026-06-28T00:00:00Z");

    @Test
    void wildcardOriginAllowsValidBrowserOrigin() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        when(ticketService.consume("wft_workspace", "http://127.0.0.1:3000"))
                .thenReturn(workspaceTicket(workspaceId.value()));
        when(workspaceService.listFiles(workspaceId, "")).thenReturn(List.of());
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                new ObjectMapper().findAndRegisterModules(),
                "*");
        FakeWebSocketSession session = FakeWebSocketSession.withOrigin(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace",
                List.of("""
                        {"id":"req_1","op":"workspace.list","params":{"workspaceId":"wrk_1234567890abcdef","path":""}}
                        """),
                "http://127.0.0.1:3000");

        handler.handle(session).block();

        assertThat(session.sentText()).singleElement().satisfies(message ->
                assertThat(message).contains("\"id\":\"req_1\"", "\"type\":\"result\""));
        verify(workspaceService).listFiles(workspaceId, "");
    }

    @Test
    void wildcardOriginStillRejectsMalformedOrigin() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                Mockito.mock(WorkspaceApplicationService.class),
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                new ObjectMapper().findAndRegisterModules(),
                "*");
        FakeWebSocketSession session = FakeWebSocketSession.withOrigin(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace",
                List.of(),
                "file://127.0.0.1");

        handler.handle(session).block();

        assertThat(session.sentText()).singleElement().satisfies(message ->
                assertThat(message).contains("\"type\":\"error\"", "\"code\":\"FORBIDDEN\""));
        verify(ticketService, never()).consume(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void mixedWildcardDoesNotAllowUnlistedOrigin() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                Mockito.mock(WorkspaceApplicationService.class),
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                new ObjectMapper().findAndRegisterModules(),
                "*,http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.withOrigin(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace",
                List.of(),
                "http://127.0.0.1:3000");

        handler.handle(session).block();

        assertThat(session.sentText()).singleElement().satisfies(message ->
                assertThat(message).contains("\"type\":\"error\"", "\"code\":\"FORBIDDEN\""));
        verify(ticketService, never()).consume(Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void readsPublicAgentConfigFileThroughWebSocketTicket() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_public", "http://localhost:3000")).thenReturn(agentTicket(true, "PUBLIC", null, "agw_123"));
        when(agentConfigService.readPublicAgentFile("review.md", "agw_123", new UserId("usr_admin")))
                .thenReturn(new FileContentResponse("review.md", "content", 7));
        WebSocketHandler handler = handler(ticketService, agentConfigService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_public",
                List.of("""
                        {"id":"req_1","op":"agent-config.read","params":{"scope":"PUBLIC","worktreeId":"agw_123","path":"review.md"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).anySatisfy(message -> {
            assertThat(message).contains("\"id\":\"req_1\"");
            assertThat(message).contains("\"type\":\"result\"");
            assertThat(message).contains("\"content\":\"content\"");
        });
        verify(agentConfigService).readPublicAgentFile("review.md", "agw_123", new UserId("usr_admin"));
    }

    @Test
    void progressivelyReadsPublicAgentConfigFileWithSnapshotFence() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_public", "http://localhost:3000"))
                .thenReturn(agentTicket(true, "PUBLIC", null, "agw_123"));
        when(agentConfigService.readPublicAgentFilePreviewChunk(
                "large.log", 524288L, 1048576L, 1234L, "agw_123", new UserId("usr_admin")))
                .thenReturn(new FilePreviewChunkResponse(
                        "large.log", "next", 524288L, 524292L, 1048576L, false, 5242880L, 1234L));
        WebSocketHandler handler = handler(ticketService, agentConfigService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_public",
                List.of("""
                        {"id":"req_chunk","op":"agent-config.read.chunk","params":{"scope":"PUBLIC","worktreeId":"agw_123","path":"large.log","offset":524288,"expectedSize":1048576,"expectedLastModifiedMillis":1234}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).singleElement().satisfies(message ->
                assertThat(message).contains(
                        "\"id\":\"req_chunk\"",
                        "\"content\":\"next\"",
                        "\"nextOffset\":524292"));
        verify(agentConfigService).readPublicAgentFilePreviewChunk(
                "large.log", 524288L, 1048576L, 1234L, "agw_123", new UserId("usr_admin"));
    }

    @Test
    void deletesPublicAgentDirectoryThroughWebSocketTicket() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_public", "http://localhost:3000")).thenReturn(agentTicket(true, "PUBLIC", null, "agw_123"));
        WebSocketHandler handler = handler(ticketService, agentConfigService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_public",
                List.of("""
                        {"id":"req_delete","op":"agent-config.delete","params":{"scope":"PUBLIC","worktreeId":"agw_123","path":"skills/obsolete"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(1).allSatisfy(message ->
                assertThat(message).contains("\"id\":\"req_delete\"", "\"type\":\"result\""));
        verify(agentConfigService).deletePublicAgentFile("skills/obsolete", "agw_123", new UserId("usr_admin"));
    }

    @Test
    void renamesPublicAgentFileThroughSuperAdminTicket() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_public", "http://localhost:3000")).thenReturn(agentTicket(true, "PUBLIC", null, "agw_123"));
        WebSocketHandler handler = handler(ticketService, agentConfigService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_public",
                List.of("""
                        {"id":"req_rename","op":"agent-config.rename","params":{"scope":"PUBLIC","worktreeId":"agw_123","path":"agents/review.md","name":"shared-review.md"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(1).allSatisfy(message ->
                assertThat(message).contains("\"id\":\"req_rename\"", "\"type\":\"result\""));
        verify(agentConfigService).renamePublicAgentFile(
                "agents/review.md",
                "shared-review.md",
                "agw_123",
                new UserId("usr_admin"));
    }

    @Test
    void copiesAndMovesPublicAgentFilesThroughSuperAdminTicket() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_public", "http://localhost:3000")).thenReturn(agentTicket(true, "PUBLIC", null, "agw_123"));
        WebSocketHandler handler = handler(ticketService, agentConfigService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_public",
                List.of(
                        """
                        {"id":"req_copy","op":"agent-config.copy","params":{"scope":"PUBLIC","worktreeId":"agw_123","sourcePath":"agents/a.md","targetPath":"skills/a.md"}}
                        """,
                        """
                        {"id":"req_move","op":"agent-config.move","params":{"scope":"PUBLIC","worktreeId":"agw_123","sourcePath":"agents/b.md","targetPath":"skills/b.md"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(2).allSatisfy(message -> assertThat(message).contains("\"type\":\"result\""));
        verify(agentConfigService).copyPublicAgentFile(
                "agents/a.md", "skills/a.md", "agw_123", new UserId("usr_admin"));
        verify(agentConfigService).movePublicAgentFile(
                "agents/b.md", "skills/b.md", "agw_123", new UserId("usr_admin"));
    }

    @Test
    void uploadsPublicAgentFileThroughSuperAdminTicket() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_public", "http://localhost:3000")).thenReturn(agentTicket(true, "PUBLIC", null, "agw_123"));
        WebSocketHandler handler = handler(ticketService, agentConfigService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_public",
                List.of("""
                        {"id":"req_upload","op":"agent-config.upload","params":{"scope":"PUBLIC","worktreeId":"agw_123","path":"agents/icon.bin","contentBase64":"AAEC/w=="}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(1).allSatisfy(message ->
                assertThat(message).contains("\"id\":\"req_upload\"", "\"type\":\"result\""));
        verify(agentConfigService).uploadPublicAgentFile(
                "agents/icon.bin",
                "AAEC/w==",
                "agw_123",
                new UserId("usr_admin"));
    }

    @Test
    void uploadsWorkspaceAgentFileThroughAppAdminTicket() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_workspace_agent", "http://localhost:3000"))
                .thenReturn(workspaceAgentTicket(true));
        WebSocketHandler handler = handler(ticketService, agentConfigService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace_agent",
                List.of("""
                        {"id":"req_upload","op":"agent-config.upload","params":{"scope":"WORKSPACE","workspaceId":"wrk_1234567890abcdef","path":"skills/payment/example.json","contentBase64":"e30="}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(1).allSatisfy(message ->
                assertThat(message).contains("\"id\":\"req_upload\"", "\"type\":\"result\""));
        verify(agentConfigService).uploadWorkspaceAgentFile(
                "wrk_1234567890abcdef",
                "skills/payment/example.json",
                "e30=",
                null);
    }

    @Test
    void deletesWorkspaceAgentFileWhenTicketHasAppAdminPermission() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_workspace_agent", "http://localhost:3000"))
                .thenReturn(workspaceAgentTicket(true));
        WebSocketHandler handler = handler(ticketService, agentConfigService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace_agent",
                List.of("""
                        {"id":"req_delete","op":"agent-config.delete","params":{"scope":"WORKSPACE","workspaceId":"wrk_1234567890abcdef","path":"agents/obsolete.md"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(1).allSatisfy(message ->
                assertThat(message).contains("\"id\":\"req_delete\"", "\"type\":\"result\""));
        verify(agentConfigService).deleteWorkspaceAgentFile(
                "wrk_1234567890abcdef",
                "agents/obsolete.md",
                null);
    }

    @Test
    void copiesAndMovesWorkspaceAgentFilesWhenTicketHasAppAdminPermission() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_workspace_agent", "http://localhost:3000"))
                .thenReturn(workspaceAgentTicket(true));
        WebSocketHandler handler = handler(ticketService, agentConfigService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace_agent",
                List.of(
                        """
                        {"id":"req_copy","op":"agent-config.copy","params":{"scope":"WORKSPACE","workspaceId":"wrk_1234567890abcdef","sourcePath":"agents/a.md","targetPath":"skills/a.md"}}
                        """,
                        """
                        {"id":"req_move","op":"agent-config.move","params":{"scope":"WORKSPACE","workspaceId":"wrk_1234567890abcdef","sourcePath":"agents/b.md","targetPath":"skills/b.md"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(2).allSatisfy(message -> assertThat(message).contains("\"type\":\"result\""));
        verify(agentConfigService).copyWorkspaceAgentFile(
                "wrk_1234567890abcdef", "agents/a.md", "skills/a.md", null);
        verify(agentConfigService).moveWorkspaceAgentFile(
                "wrk_1234567890abcdef", "agents/b.md", "skills/b.md", null);
    }

    @Test
    void rejectsAgentConfigMutationsWhenTicketIsNotSuperAdmin() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_public", "http://localhost:3000")).thenReturn(agentTicket(false, "PUBLIC", null, "agw_123"));
        WebSocketHandler handler = handler(ticketService, agentConfigService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_public",
                List.of("""
                        {"id":"req_1","op":"agent-config.write","params":{"scope":"PUBLIC","worktreeId":"agw_123","path":"review.md","content":"changed"}}
                        """, """
                        {"id":"req_2","op":"agent-config.delete","params":{"scope":"PUBLIC","worktreeId":"agw_123","path":"review.md"}}
                        """, """
                        {"id":"req_3","op":"agent-config.rename","params":{"scope":"PUBLIC","worktreeId":"agw_123","path":"review.md","name":"renamed.md"}}
                        """, """
                        {"id":"req_4","op":"agent-config.upload","params":{"scope":"PUBLIC","worktreeId":"agw_123","path":"review.bin","contentBase64":"AA=="}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(4).allSatisfy(message -> {
            assertThat(message).contains("\"type\":\"error\"");
            assertThat(message).contains("\"code\":\"FORBIDDEN\"");
        });
        verify(agentConfigService, never()).writePublicAgentFile("review.md", "changed", "agw_123", new UserId("usr_admin"));
        verify(agentConfigService, never()).deletePublicAgentFile("review.md", "agw_123", new UserId("usr_admin"));
        verify(agentConfigService, never()).renamePublicAgentFile("review.md", "renamed.md", "agw_123", new UserId("usr_admin"));
        verify(agentConfigService, never()).uploadPublicAgentFile("review.bin", "AA==", "agw_123", new UserId("usr_admin"));
    }

    @Test
    void renamesWorkspaceAgentFileForAppAdmin() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_workspace_agent", "http://localhost:3000"))
                .thenReturn(agentTicket(true, "WORKSPACE", "wrk_1234567890abcdef", null));
        WebSocketHandler handler = handler(ticketService, agentConfigService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace_agent",
                List.of("""
                        {"id":"req_rename","op":"agent-config.rename","params":{"scope":"WORKSPACE","workspaceId":"wrk_1234567890abcdef","path":"agents/review.md","name":"payment-review.md"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(1).allSatisfy(message -> {
            assertThat(message).contains("\"type\":\"result\"");
            assertThat(message).contains("\"id\":\"req_rename\"");
        });
        verify(agentConfigService).renameWorkspaceAgentFile(
                "wrk_1234567890abcdef",
                "agents/review.md",
                "payment-review.md",
                null);
    }

    @Test
    void rejectsWorkspaceAgentRenameForOrdinaryUser() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_workspace_agent", "http://localhost:3000"))
                .thenReturn(agentTicket(false, "WORKSPACE", "wrk_1234567890abcdef", null));
        WebSocketHandler handler = handler(ticketService, agentConfigService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace_agent",
                List.of("""
                        {"id":"req_rename","op":"agent-config.rename","params":{"scope":"WORKSPACE","workspaceId":"wrk_1234567890abcdef","path":"agents/review.md","name":"payment-review.md"}}
                        """, """
                        {"id":"req_upload","op":"agent-config.upload","params":{"scope":"WORKSPACE","workspaceId":"wrk_1234567890abcdef","path":"agents/icon.bin","contentBase64":"AA=="}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(2).allSatisfy(message -> {
            assertThat(message).contains("\"type\":\"error\"");
            assertThat(message).contains("\"code\":\"FORBIDDEN\"");
        });
        verify(agentConfigService, never()).renameWorkspaceAgentFile(
                "wrk_1234567890abcdef",
                "agents/review.md",
                "payment-review.md",
                null);
        verify(agentConfigService, never()).uploadWorkspaceAgentFile(
                "wrk_1234567890abcdef",
                "agents/icon.bin",
                "AA==",
                null);
    }

    @Test
    void ordinaryApplicationMemberCanRequestBackendAutomationReferenceReconciliation() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        ApplicationAutomationReferenceWorkspaceReconciliationService reconciliationService =
                Mockito.mock(ApplicationAutomationReferenceWorkspaceReconciliationService.class);
        Workspace workspace = new Workspace(
                new WorkspaceId("wrk_1234567890abcdef"),
                "个人工作树",
                "/logical/workspace",
                WorkspaceStatus.ACTIVE,
                NOW,
                NOW,
                "linux-1",
                TRACE_ID);
        when(ticketService.consume("wft_workspace_agent", "http://localhost:3000"))
                .thenReturn(agentTicket(false, "WORKSPACE", workspace.workspaceId().value(), null));
        when(workspaceService.requireWorkspaceOnCurrentServer(workspace.workspaceId(), TRACE_ID))
                .thenReturn(workspace);
        when(reconciliationService.reconcile(workspace, new UserId("usr_admin"), TRACE_ID))
                .thenReturn(new AutomationReferenceRunPreparation(
                        List.of(), List.of("自动化库：当前副本尚未就绪"), true));
        WorkspaceFileWebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                agentConfigService,
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        handler.setAutomationReferenceReconciliationService(reconciliationService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace_agent",
                List.of("""
                        {"id":"req_reconcile","op":"agent-config.automation-reference.reconcile","params":{"scope":"WORKSPACE","workspaceId":"wrk_1234567890abcdef"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).singleElement().satisfies(message ->
                assertThat(message).contains(
                        "\"type\":\"result\"",
                        "\"changed\":true",
                        "当前副本尚未就绪"));
        verify(reconciliationService).reconcile(workspace, new UserId("usr_admin"), TRACE_ID);
    }

    @Test
    void rejectsAgentConfigRequestWhenWorktreeDoesNotMatchTicket() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_public", "http://localhost:3000")).thenReturn(agentTicket(true, "PUBLIC", null, "agw_123"));
        WebSocketHandler handler = handler(ticketService, agentConfigService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_public",
                List.of("""
                        {"id":"req_1","op":"agent-config.read","params":{"scope":"PUBLIC","worktreeId":"agw_other","path":"review.md"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).anySatisfy(message -> {
            assertThat(message).contains("\"type\":\"error\"");
            assertThat(message).contains("\"code\":\"FORBIDDEN\"");
        });
        verify(agentConfigService, never()).readPublicAgentFile("review.md", "agw_other", new UserId("usr_admin"));
    }

    @Test
    void rejectsProtectedWorkspaceConfigWriteForOrdinaryUser() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_workspace", "http://localhost:3000")).thenReturn(new WorkspaceFileSocketTicket(
                "wft_workspace",
                "wrk_1234567890abcdef",
                "linux-1",
                "linux-1",
                false,
                false,
                "usr_1234567890abcdef",
                "workspace",
                null,
                null,
                TRACE_ID,
                NOW.plusSeconds(60)));
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                agentConfigService,
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace",
                List.of("""
                        {"id":"req_write","op":"workspace.write","params":{"workspaceId":"wrk_1234567890abcdef","path":".opencode/skills/pay/SKILL.md","content":"changed"}}
                        """, """
                        {"id":"req_write_command","op":"workspace.write","params":{"workspaceId":"wrk_1234567890abcdef","path":".opencode/commands/deploy.md","content":"changed"}}
                        """, """
                        {"id":"req_delete","op":"workspace.delete","params":{"workspaceId":"wrk_1234567890abcdef","path":".opencode"}}
                        """, """
                        {"id":"req_delete_alias","op":"workspace.delete","params":{"workspaceId":"wrk_1234567890abcdef","path":"./tmp/../.opencode"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(4).allSatisfy(message -> {
            assertThat(message).contains("\"type\":\"error\"");
            assertThat(message).contains("\"code\":\"FORBIDDEN\"");
        });
        verify(workspaceService, never()).writeFile(Mockito.any(), Mockito.anyString(), Mockito.anyString());
        verify(workspaceService, never()).deleteFile(Mockito.any(), Mockito.anyString());
    }

    @Test
    void experienceWorkspaceHidesProtectedConfigEvenForApplicationAdministrator() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        WorkspaceId workspaceId = new WorkspaceId("wrk_exp_1234567890abcdef");
        WorkspaceFileSocketTicket ticket = new WorkspaceFileSocketTicket(
                "wft_experience",
                workspaceId.value(),
                "linux-1",
                "linux-1",
                true,
                true,
                "usr_experience_admin",
                "workspace",
                null,
                null,
                TRACE_ID,
                NOW.plusSeconds(60));
        when(ticketService.consume("wft_experience", "http://localhost:3000")).thenReturn(ticket);
        when(workspaceService.listFiles(workspaceId, "")).thenReturn(List.of(
                new FileTreeEntryResponse(".git", ".git", true, 0, NOW),
                new FileTreeEntryResponse(".opencode", ".opencode", true, 0, NOW),
                new FileTreeEntryResponse("examples/.OPENCODE", ".OPENCODE", true, 0, NOW),
                new FileTreeEntryResponse("README.md", "README.md", false, 12, NOW)));
        when(workspaceService.searchFiles(workspaceId, "agent")).thenReturn(List.of(
                new FileSearchResultResponse(".git/config", "config", ".git", 12, NOW),
                new FileSearchResultResponse(
                        ".opencode/agents/review.md", "review.md", ".opencode/agents", 12, NOW),
                new FileSearchResultResponse(
                        "examples/.GIT/config", "config", "examples/.GIT", 12, NOW),
                new FileSearchResultResponse("docs/agent.md", "agent.md", "docs", 12, NOW)));
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                agentConfigService,
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_experience",
                List.of("""
                        {"id":"req_list","op":"workspace.list","params":{"workspaceId":"wrk_exp_1234567890abcdef","path":""}}
                        """, """
                        {"id":"req_search","op":"workspace.search","params":{"workspaceId":"wrk_exp_1234567890abcdef","query":"agent"}}
                        """, """
                        {"id":"req_read","op":"workspace.read","params":{"workspaceId":"wrk_exp_1234567890abcdef","path":".opencode/opencode.jsonc"}}
                        """, """
                        {"id":"req_write","op":"workspace.write","params":{"workspaceId":"wrk_exp_1234567890abcdef","path":".opencode/opencode.jsonc","content":"{}"}}
                        """, """
                        {"id":"req_read_nested_config","op":"workspace.read","params":{"workspaceId":"wrk_exp_1234567890abcdef","path":"examples/.opencode/opencode.jsonc"}}
                        """, """
                        {"id":"req_write_nested_config","op":"workspace.write","params":{"workspaceId":"wrk_exp_1234567890abcdef","path":"examples/.opencode/opencode.jsonc","content":"{}"}}
                        """, """
                        {"id":"req_read_upper_git","op":"workspace.read","params":{"workspaceId":"wrk_exp_1234567890abcdef","path":"examples/.GIT/config"}}
                        """, """
                        {"id":"req_write_upper_config","op":"workspace.write","params":{"workspaceId":"wrk_exp_1234567890abcdef","path":"examples/.OPENCODE/opencode.jsonc","content":"{}"}}
                        """, """
                        {"id":"req_read_git","op":"workspace.read","params":{"workspaceId":"wrk_exp_1234567890abcdef","path":".git/config"}}
                        """, """
                        {"id":"req_write_git","op":"workspace.write","params":{"workspaceId":"wrk_exp_1234567890abcdef","path":".git/config","content":"changed"}}
                        """, """
                        {"id":"req_copy_git","op":"workspace.copy","params":{"workspaceId":"wrk_exp_1234567890abcdef","sourcePath":"README.md","targetPath":".git/copied"}}
                        """, """
                        {"id":"req_move_config","op":"workspace.move","params":{"workspaceId":"wrk_exp_1234567890abcdef","sourcePath":".opencode/agent.md","targetPath":"agent.md"}}
                        """, """
                        {"id":"req_rename_git","op":"workspace.rename","params":{"workspaceId":"wrk_exp_1234567890abcdef","path":"README.md","name":".git"}}
                        """, """
                        {"id":"req_status_git","op":"workspace.status","params":{"workspaceId":"wrk_exp_1234567890abcdef","path":".git/config"}}
                        """, """
                        {"id":"req_delete_config","op":"workspace.delete","params":{"workspaceId":"wrk_exp_1234567890abcdef","path":".opencode/agent.md"}}
                        """, """
                        {"id":"req_mkdir_git","op":"workspace.mkdir","params":{"workspaceId":"wrk_exp_1234567890abcdef","path":".git/generated"}}
                        """, """
                        {"id":"req_view","op":"workspace.view.list","params":{"workspaceId":"wrk_exp_1234567890abcdef","locator":{"kind":"WORKSPACE","path":""}}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText())
                .filteredOn(message -> message.contains("\"id\":\"req_list\""))
                .singleElement()
                .satisfies(message -> assertThat(message)
                        .contains("README.md")
                        .doesNotContain(".opencode", ".git"));
        assertThat(session.sentText())
                .filteredOn(message -> message.contains("\"id\":\"req_search\""))
                .singleElement()
                .satisfies(message -> assertThat(message)
                        .contains("docs/agent.md")
                        .doesNotContain(".opencode", ".git"));
        assertThat(session.sentText().stream()
                .filter(message -> message.contains("\"id\":\"req_read\"")
                        || message.contains("\"id\":\"req_write\"")
                        || message.contains("\"id\":\"req_read_git\"")
                        || message.contains("\"id\":\"req_write_git\"")
                        || message.contains("\"id\":\"req_read_nested_config\"")
                        || message.contains("\"id\":\"req_write_nested_config\"")
                        || message.contains("\"id\":\"req_read_upper_git\"")
                        || message.contains("\"id\":\"req_write_upper_config\"")
                        || message.contains("\"id\":\"req_copy_git\"")
                        || message.contains("\"id\":\"req_move_config\"")
                        || message.contains("\"id\":\"req_rename_git\"")
                        || message.contains("\"id\":\"req_status_git\"")
                        || message.contains("\"id\":\"req_delete_config\"")
                        || message.contains("\"id\":\"req_mkdir_git\"")
                        || message.contains("\"id\":\"req_view\"")))
                .hasSize(15)
                .allSatisfy(message -> assertThat(message)
                        .contains("\"type\":\"error\"", "\"code\":\"FORBIDDEN\""));
        verify(workspaceService, never()).readFile(workspaceId, ".opencode/opencode.jsonc");
        verify(workspaceService, never()).writeFile(workspaceId, ".opencode/opencode.jsonc", "{}");
        verify(workspaceService, never()).readFile(workspaceId, "examples/.opencode/opencode.jsonc");
        verify(workspaceService, never()).writeFile(workspaceId, "examples/.opencode/opencode.jsonc", "{}");
        verify(workspaceService, never()).readFile(workspaceId, "examples/.GIT/config");
        verify(workspaceService, never()).writeFile(workspaceId, "examples/.OPENCODE/opencode.jsonc", "{}");
        verify(workspaceService, never()).readFile(workspaceId, ".git/config");
        verify(workspaceService, never()).writeFile(workspaceId, ".git/config", "changed");
    }

    @Test
    void renamesWorkspaceFileThroughWebSocketTicket() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_workspace", "http://localhost:3000"))
                .thenReturn(workspaceTicket("wrk_1234567890abcdef"));
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                agentConfigService,
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace",
                List.of("""
                        {"id":"req_rename","op":"workspace.rename","params":{"workspaceId":"wrk_1234567890abcdef","path":"docs/old.md","name":"new.md"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).anySatisfy(message -> {
            assertThat(message).contains("\"id\":\"req_rename\"");
            assertThat(message).contains("\"type\":\"result\"");
        });
        verify(workspaceService).renameFile(
                new com.enterprise.testagent.domain.workspace.WorkspaceId("wrk_1234567890abcdef"),
                "docs/old.md",
                "new.md");
    }

    @Test
    void uploadsCopiesAndMovesWorkspaceFilesThroughWebSocketTicket() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        when(ticketService.consume("wft_workspace", "http://localhost:3000"))
                .thenReturn(workspaceTicket("wrk_1234567890abcdef"));
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                agentConfigService,
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace",
                List.of(
                        """
                        {"id":"req_upload","op":"workspace.upload","params":{"workspaceId":"wrk_1234567890abcdef","path":"assets/icon.bin","contentBase64":"AAEC/w=="}}
                        """,
                        """
                        {"id":"req_copy","op":"workspace.copy","params":{"workspaceId":"wrk_1234567890abcdef","sourcePath":"docs/a.md","targetPath":"backup/a.md"}}
                        """,
                        """
                        {"id":"req_move","op":"workspace.move","params":{"workspaceId":"wrk_1234567890abcdef","sourcePath":"docs/a.md","targetPath":"archive/a.md"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(3).allSatisfy(message -> assertThat(message).contains("\"type\":\"result\""));
        var workspaceId = new com.enterprise.testagent.domain.workspace.WorkspaceId("wrk_1234567890abcdef");
        verify(workspaceService).uploadFile(workspaceId, "assets/icon.bin", "AAEC/w==");
        verify(workspaceService).copyFile(workspaceId, "docs/a.md", "backup/a.md");
        verify(workspaceService).moveFile(workspaceId, "docs/a.md", "archive/a.md");
    }

    @Test
    void streamsWorkspaceUploadThroughBeginChunkAndComplete() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        WorkspaceFileUpload upload = Mockito.mock(WorkspaceFileUpload.class);
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        when(ticketService.consume("wft_workspace", "http://localhost:3000"))
                .thenReturn(workspaceTicket(workspaceId.value()));
        when(workspaceService.beginFileUpload(workspaceId, "assets/large.bin", 4L)).thenReturn(upload);
        when(upload.chunkBytes()).thenReturn(262144);
        when(upload.expectedBytes()).thenReturn(4L);
        when(upload.uploadedBytes()).thenReturn(4L);
        when(upload.complete()).thenReturn(4L);
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.uploadFlow(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace",
                """
                {"id":"req_begin","op":"workspace.upload.begin","params":{"workspaceId":"wrk_1234567890abcdef","path":"assets/large.bin","size":4}}
                """,
                response -> {
                    String uploadId = uploadId(response);
                    return List.of(
                            """
                            {"id":"req_chunk","op":"workspace.upload.chunk","params":{"workspaceId":"wrk_1234567890abcdef","uploadId":"%s","index":0,"contentBase64":"AAEC/w=="}}
                            """.formatted(uploadId),
                            """
                            {"id":"req_complete","op":"workspace.upload.complete","params":{"workspaceId":"wrk_1234567890abcdef","uploadId":"%s"}}
                            """.formatted(uploadId));
                });

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(3).allSatisfy(message ->
                assertThat(message).contains("\"type\":\"result\""));
        assertThat(session.sentText().get(0)).contains("\"chunkBytes\":262144", "\"totalBytes\":4");
        assertThat(session.sentText().get(2)).contains("\"size\":4");
        verify(workspaceService).beginFileUpload(workspaceId, "assets/large.bin", 4L);
        verify(upload).append(0L, "AAEC/w==");
        verify(upload).complete();
        verify(upload, never()).abort();
    }

    @Test
    void abortsPublicAgentUploadWhenSocketClosesBeforeComplete() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        AgentConfigApplicationService agentConfigService = Mockito.mock(AgentConfigApplicationService.class);
        WorkspaceFileUpload upload = Mockito.mock(WorkspaceFileUpload.class);
        when(ticketService.consume("wft_public", "http://localhost:3000"))
                .thenReturn(agentTicket(true, "PUBLIC", null, "agw_123"));
        when(agentConfigService.beginPublicAgentFileUpload(
                        "agents/large.bin", 9L, "agw_123", new UserId("usr_admin")))
                .thenReturn(upload);
        when(upload.chunkBytes()).thenReturn(262144);
        when(upload.expectedBytes()).thenReturn(9L);
        WebSocketHandler handler = handler(ticketService, agentConfigService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_public",
                List.of("""
                        {"id":"req_begin","op":"agent-config.upload.begin","params":{"scope":"PUBLIC","worktreeId":"agw_123","path":"agents/large.bin","size":9}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).singleElement().satisfies(message ->
                assertThat(message).contains("\"type\":\"result\"", "\"chunkBytes\":262144"));
        verify(agentConfigService).beginPublicAgentFileUpload(
                "agents/large.bin", 9L, "agw_123", new UserId("usr_admin"));
        verify(upload).abort();
    }

    @Test
    void listsCompositeWorkspaceViewAndMapsOnlyLogicalLocatorFields() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        WorkspaceViewApplicationService viewService = Mockito.mock(WorkspaceViewApplicationService.class);
        when(ticketService.consume("wft_workspace", "http://localhost:3000"))
                .thenReturn(workspaceTicket("wrk_1234567890abcdef"));
        WorkspaceViewLocator root = WorkspaceViewLocator.root();
        when(viewService.list(
                new UserId("usr_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                root)).thenReturn(new WorkspaceViewListResponse(
                List.of(new WorkspaceViewEntry(
                        "view_docs",
                        "docs",
                        "docs",
                        true,
                        0L,
                        NOW,
                        new WorkspaceViewLocator(WorkspaceViewLocatorKind.COMPOSITE, "docs", null),
                        WorkspaceViewSource.MIXED,
                        true,
                        false,
                        false,
                        "docs",
                        List.of("docs-requirements"))),
                List.of(),
                false));
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                viewService,
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace",
                List.of("""
                        {"id":"req_view","op":"workspace.view.list","params":{
                          "workspaceId":"wrk_1234567890abcdef",
                          "locator":{"kind":"COMPOSITE","path":""}
                        }}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).singleElement().satisfies(message -> {
            assertThat(message).contains("\"type\":\"result\"");
            assertThat(message).contains("\"id\":\"view_docs\"");
            assertThat(message).contains("\"source\":\"MIXED\"");
        });
        verify(ticketService).authorizeWorkspaceRpc(
                workspaceTicket("wrk_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"));
        verify(viewService).list(
                new UserId("usr_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                root);
    }

    @Test
    void readsReferenceFileThroughLogicalLocator() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceViewApplicationService viewService = Mockito.mock(WorkspaceViewApplicationService.class);
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        WorkspaceViewLocator locator = new WorkspaceViewLocator(
                WorkspaceViewLocatorKind.REFERENCE,
                "guide.md",
                "docs-requirements");
        when(ticketService.consume("wft_workspace", "http://localhost:3000"))
                .thenReturn(workspaceTicket(workspaceId.value()));
        when(viewService.read(new UserId("usr_1234567890abcdef"), workspaceId, locator)).thenReturn(new WorkspaceViewReadResponse(
                "docs/guide.md",
                "reference-content",
                17L,
                true,
                WorkspaceViewSource.REFERENCE,
                "docs-requirements",
                locator));
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                Mockito.mock(WorkspaceApplicationService.class),
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                viewService,
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace",
                List.of("""
                        {"id":"req_read","op":"workspace.view.read","params":{
                          "workspaceId":"wrk_1234567890abcdef",
                          "locator":{"kind":"REFERENCE","path":"guide.md","referenceAlias":"docs-requirements"}
                        }}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).singleElement().satisfies(message -> {
            assertThat(message).contains("\"type\":\"result\"");
            assertThat(message).contains("\"content\":\"reference-content\"");
            assertThat(message).doesNotContain("physicalPath", "rootPath", "repositoryId");
        });
        verify(ticketService).authorizeWorkspaceRpc(workspaceTicket(workspaceId.value()), workspaceId);
        verify(viewService).read(new UserId("usr_1234567890abcdef"), workspaceId, locator);
    }

    @Test
    void readsWorkspaceAndReferenceBinaryChunksThroughLogicalRoutes() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        WorkspaceViewApplicationService viewService = Mockito.mock(WorkspaceViewApplicationService.class);
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        WorkspaceViewLocator locator = new WorkspaceViewLocator(
                WorkspaceViewLocatorKind.REFERENCE,
                "asset.bin",
                "docs-requirements");
        when(ticketService.consume("wft_workspace", "http://localhost:3000"))
                .thenReturn(workspaceTicket(workspaceId.value()));
        when(workspaceService.readFileBinaryChunk(workspaceId, "asset.bin", 0L, null, null))
                .thenReturn(new FileBinaryChunkResponse("asset.bin", "AP8=", 0L, 2L, 2L, true, 1234L));
        when(viewService.readBinaryChunk(
                new UserId("usr_1234567890abcdef"), workspaceId, locator, 0L, null, null))
                .thenReturn(new FileBinaryChunkResponse("docs/asset.bin", "AYA=", 0L, 2L, 2L, true, 1234L));
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                viewService,
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace",
                List.of(
                        """
                        {"id":"req_workspace","op":"workspace.read.binary.chunk","params":{"workspaceId":"wrk_1234567890abcdef","path":"asset.bin","offset":0}}
                        """,
                        """
                        {"id":"req_reference","op":"workspace.view.read.binary.chunk","params":{"workspaceId":"wrk_1234567890abcdef","locator":{"kind":"REFERENCE","path":"asset.bin","referenceAlias":"docs-requirements"},"offset":0}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(2);
        assertThat(session.sentText().get(0)).contains("\"contentBase64\":\"AP8=\"");
        assertThat(session.sentText().get(1)).contains("\"contentBase64\":\"AYA=\"", "\"path\":\"docs/asset.bin\"");
        verify(ticketService, times(2)).authorizeWorkspaceRpc(workspaceTicket(workspaceId.value()), workspaceId);
        verify(workspaceService).readFileBinaryChunk(workspaceId, "asset.bin", 0L, null, null);
        verify(viewService).readBinaryChunk(
                new UserId("usr_1234567890abcdef"), workspaceId, locator, 0L, null, null);
    }

    @Test
    void revokedMembershipStopsTheNextWorkspaceModeRpcOnTheSameSocket() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        WorkspaceViewApplicationService viewService = Mockito.mock(WorkspaceViewApplicationService.class);
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        WorkspaceFileSocketTicket ticket = workspaceTicket(workspaceId.value());
        when(ticketService.consume("wft_workspace", "http://localhost:3000"))
                .thenReturn(ticket);
        when(workspaceService.listFiles(workspaceId, "")).thenReturn(List.of());
        when(ticketService.authorizeWorkspaceRpc(ticket, workspaceId))
                .thenReturn(null)
                .thenThrow(new com.enterprise.testagent.common.error.PlatformException(
                        com.enterprise.testagent.common.error.ErrorCode.FORBIDDEN,
                        "成员关系已失效"));
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                viewService,
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace",
                List.of(
                        """
                        {"id":"req_1","op":"workspace.list","params":{"workspaceId":"wrk_1234567890abcdef","path":""}}
                        """,
                        """
                        {"id":"req_2","op":"workspace.view.read","params":{"workspaceId":"wrk_1234567890abcdef","locator":{"kind":"REFERENCE","path":"readme.md","referenceAlias":"docs-requirements"}}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(2);
        assertThat(session.sentText().get(0)).contains("\"type\":\"result\"");
        assertThat(session.sentText().get(1)).contains("\"type\":\"error\"", "\"code\":\"FORBIDDEN\"");
        verify(viewService, never()).read(Mockito.any(), Mockito.any());
        verify(workspaceService).listFiles(workspaceId, "");
    }

    @Test
    void everyRpcRejectsWorkspaceOnServerBWhenReplicaIsOnServerA() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        WorkspaceFileSocketTicket ticket = new WorkspaceFileSocketTicket(
                "wft_workspace", workspaceId.value(), "server-b", "server-b",
                false, false, "usr_1234567890abcdef", "workspace",
                null, null, TRACE_ID, NOW.plusSeconds(60));
        when(ticketService.consume("wft_workspace", "http://localhost:3000"))
                .thenReturn(ticket);
        Mockito.doThrow(new com.enterprise.testagent.common.error.PlatformException(
                        com.enterprise.testagent.common.error.ErrorCode.FORBIDDEN,
                        "应用源码工作区服务器绑定不一致"))
                .when(ticketService)
                .authorizeWorkspaceRpc(ticket, workspaceId);
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                Mockito.mock(WorkspaceViewApplicationService.class),
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace",
                List.of("""
                        {"id":"req_1","op":"workspace.list","params":{"workspaceId":"wrk_1234567890abcdef","path":""}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).singleElement().satisfies(message ->
                assertThat(message).contains("\"type\":\"error\"", "\"code\":\"FORBIDDEN\""));
        verify(workspaceService, never()).listFiles(workspaceId, "");
    }

    @Test
    void sameSocketRejectsNextWorkspaceRpcAfterAgentBindingMovesToAnotherServer() {
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        ConversationWorkspaceAccessAuthorizer authorizer = Mockito.mock(ConversationWorkspaceAccessAuthorizer.class);
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        WorkspaceFileSocketTicketService ticketService = realWorkspaceTicketService(
                workspaceService, assignmentService, authorizer);
        when(workspaceService.currentLinuxServerId()).thenReturn("server-b");
        when(workspaceService.listFiles(workspaceId, "")).thenReturn(List.of());
        when(assignmentService.fileRoutingAffinity(
                new UserId("usr_1234567890abcdef"), "opencode", TRACE_ID))
                .thenReturn(
                        readyAffinity("server-b"),
                        readyAffinity("server-b"),
                        readyAffinity("server-c"));
        ticketService.createTicket(
                workspacePrincipal(),
                new WorkspaceFileSocketDtos.TicketRequest(workspaceId.value(), "server-b", "workspace"),
                TRACE_ID);
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_dynamic",
                List.of(
                        """
                        {"id":"req_1","op":"workspace.list","params":{"workspaceId":"wrk_1234567890abcdef","path":""}}
                        """,
                        """
                        {"id":"req_2","op":"workspace.list","params":{"workspaceId":"wrk_1234567890abcdef","path":""}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(2);
        assertThat(session.sentText().get(0)).contains("\"type\":\"result\"");
        assertThat(session.sentText().get(1)).contains("\"type\":\"error\"", "\"code\":\"FORBIDDEN\"");
        verify(workspaceService, times(1)).listFiles(workspaceId, "");
    }

    @Test
    void sameSuperAdminSocketRejectsNextRpcWhenBoundAppSourceReplicaMappingDisappears() {
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        AppSourceRepository appSources = Mockito.mock(AppSourceRepository.class);
        ConversationWorkspaceAccessAuthorizer authorizer = appSourceAuthorizer(workspaceId, appSources);
        WorkspaceFileSocketTicketService ticketService = realWorkspaceTicketService(
                workspaceService, assignmentService, authorizer);
        when(workspaceService.currentLinuxServerId()).thenReturn("server-a");
        when(workspaceService.listFiles(workspaceId, "")).thenReturn(List.of());
        when(assignmentService.fileRoutingAffinity(
                new UserId("usr_1234567890abcdef"), "opencode", TRACE_ID))
                .thenReturn(readyAffinity("server-a"));
        when(appSources.findReplicaByRuntimeWorkspaceId(workspaceId.value())).thenReturn(
                Optional.of(appSourceReplica(workspaceId)),
                Optional.of(appSourceReplica(workspaceId)),
                Optional.empty());
        ticketService.createTicket(
                workspacePrincipal(true),
                new WorkspaceFileSocketDtos.TicketRequest(workspaceId.value(), "server-a", "workspace"),
                TRACE_ID);
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_dynamic",
                List.of(
                        """
                        {"id":"req_1","op":"workspace.list","params":{"workspaceId":"wrk_1234567890abcdef","path":""}}
                        """,
                        """
                        {"id":"req_2","op":"workspace.list","params":{"workspaceId":"wrk_1234567890abcdef","path":""}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(2);
        assertThat(session.sentText().get(0)).contains("\"type\":\"result\"");
        assertThat(session.sentText().get(1)).contains("\"type\":\"error\"", "\"code\":\"FORBIDDEN\"");
        verify(workspaceService, times(1)).listFiles(workspaceId, "");
    }

    @Test
    void superAdminCannotUseOrdinaryTicketForUnmanagedWorkspace() {
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        ConversationWorkspaceAccessAuthorizer authorizer = appSourceAuthorizer(
                workspaceId, Mockito.mock(AppSourceRepository.class));
        WorkspaceFileSocketTicketService ticketService = realWorkspaceTicketService(
                workspaceService, assignmentService, authorizer);
        when(workspaceService.currentLinuxServerId()).thenReturn("server-a");
        when(workspaceService.listFiles(workspaceId, "")).thenReturn(List.of());
        when(assignmentService.fileRoutingAffinity(
                new UserId("usr_1234567890abcdef"), "opencode", TRACE_ID))
                .thenReturn(readyAffinity("server-a"));
        assertThatThrownBy(() -> ticketService.createTicket(
                        workspacePrincipal(true),
                        new WorkspaceFileSocketDtos.TicketRequest(workspaceId.value(), "server-a", "workspace"),
                        TRACE_ID))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.FORBIDDEN));

        verify(workspaceService, never()).listFiles(workspaceId, "");
    }

    @Test
    void rejectsWorkspaceRpcWhenTicketServerNoLongerMatchesCurrentJvm() {
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        UserOpencodeProcessAssignmentService assignmentService = Mockito.mock(UserOpencodeProcessAssignmentService.class);
        ConversationWorkspaceAccessAuthorizer authorizer = Mockito.mock(ConversationWorkspaceAccessAuthorizer.class);
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        WorkspaceFileSocketTicketService ticketService = realWorkspaceTicketService(
                workspaceService, assignmentService, authorizer);
        when(workspaceService.currentLinuxServerId()).thenReturn("server-b", "server-c");
        when(assignmentService.fileRoutingAffinity(
                new UserId("usr_1234567890abcdef"), "opencode", TRACE_ID))
                .thenReturn(readyAffinity("server-b"));
        ticketService.createTicket(
                workspacePrincipal(),
                new WorkspaceFileSocketDtos.TicketRequest(workspaceId.value(), "server-b", "workspace"),
                TRACE_ID);
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_dynamic",
                List.of("""
                        {"id":"req_1","op":"workspace.list","params":{"workspaceId":"wrk_1234567890abcdef","path":""}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).singleElement().satisfies(message ->
                assertThat(message).contains("\"type\":\"error\"", "\"code\":\"FORBIDDEN\""));
        verify(workspaceService, never()).listFiles(workspaceId, "");
    }

    @Test
    void rejectsViewWorkspaceIdSpoofAndPhysicalLocatorFieldsBeforeCallingViewService() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceViewApplicationService viewService = Mockito.mock(WorkspaceViewApplicationService.class);
        when(ticketService.consume("wft_workspace", "http://localhost:3000"))
                .thenReturn(workspaceTicket("wrk_1234567890abcdef"));
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                Mockito.mock(WorkspaceApplicationService.class),
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                viewService,
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace",
                List.of(
                        """
                        {"id":"req_spoof","op":"workspace.view.list","params":{"workspaceId":"wrk_other","locator":{"kind":"COMPOSITE","path":""}}}
                        """,
                        """
                        {"id":"req_physical","op":"workspace.view.read","params":{"workspaceId":"wrk_1234567890abcdef","locator":{"kind":"REFERENCE","path":"guide.md","referenceAlias":"docs-requirements","physicalPath":"/private/reference"}}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(2).allSatisfy(message ->
                assertThat(message).contains("\"type\":\"error\"", "\"code\":\"FORBIDDEN\""));
        verify(viewService, never()).list(Mockito.any(), Mockito.any());
        verify(viewService, never()).read(Mockito.any(), Mockito.any());
    }

    @Test
    void supportReadOnlyTicketRejectsWriteBeforeCallingWorkspaceService() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        WorkspaceFileSocketTicket ticket = supportWorkspaceTicket();
        when(ticketService.consume("wft_support", "http://localhost:3000")).thenReturn(ticket);
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_support",
                List.of("""
                        {"id":"req_support_write","op":"workspace.write","params":{"workspaceId":"wrk_1234567890abcdef","path":"README.md","content":"changed"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).singleElement().satisfies(message ->
                assertThat(message).contains("\"type\":\"error\"", "\"code\":\"FORBIDDEN\""));
        verify(workspaceService, never()).writeFile(Mockito.any(), Mockito.any(), Mockito.any());
        verify(ticketService, never()).authorizeWorkspaceRpc(Mockito.any(), Mockito.any());
    }

    @Test
    void importsRequirementsWithServerTicketIdentityAndTrustedSelectionOnly() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        RequirementImportApplicationService importService = Mockito.mock(RequirementImportApplicationService.class);
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        WorkspaceFileSocketTicket ticket = workspaceTicket(workspaceId.value(), "u001");
        when(ticketService.consume("wft_workspace", "http://localhost:3000")).thenReturn(ticket);
        when(importService.importRequirements(Mockito.eq("u001"), Mockito.any()))
                .thenReturn(new RequirementImportApplicationService.ImportResult(
                        "SUCCEEDED",
                        6,
                        1,
                        0,
                        0,
                        List.of(),
                        List.of("spec/I-01-登录需求")));
        WorkspaceFileWebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        handler.setRequirementImportService(importService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace",
                List.of("""
                        {"id":"req_import","op":"workspace.requirement-import","params":{"workspaceId":"wrk_1234567890abcdef","appShortName":"APP-A","editionId":"2026年8月","selectedSubItemNos":["SI-01"],"requestId":"browser-1","documentUrl":"https://attacker.example/doc"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).singleElement().satisfies(message ->
                assertThat(message)
                        .contains(
                                "\"type\":\"result\"",
                                "\"status\":\"SUCCEEDED\"",
                                "\"workspaceRelativeDisplayPaths\":[\"spec/I-01-登录需求\"]")
                        .doesNotContain("physicalRootPath"));
        verify(workspaceService).requireWorkspaceWriteAccess(
                workspaceId, new UserId("usr_1234567890abcdef"), false);
        var command = org.mockito.ArgumentCaptor.forClass(RequirementImportApplicationService.ImportCommand.class);
        verify(importService).importRequirements(Mockito.eq("u001"), command.capture());
        assertThat(command.getValue().selectedSubItemNos()).containsExactly("SI-01");
        assertThat(command.getValue().toString()).doesNotContain("attacker.example", "documentUrl");
    }

    @Test
    void listsRequirementImportStateThroughTheAuthorizedWorkspaceSocket() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        RequirementImportApplicationService importService = Mockito.mock(RequirementImportApplicationService.class);
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        WorkspaceFileSocketTicket ticket = workspaceTicket(workspaceId.value(), "u001");
        when(ticketService.consume("wft_workspace", "http://localhost:3000")).thenReturn(ticket);
        when(importService.listWorkspaceItems("u001", workspaceId.value(), "APP-A", "2026年8月"))
                .thenReturn(List.of(new RequirementImportApplicationService.ItemOption(
                        "I-01",
                        "登录需求",
                        List.of(new RequirementImportApplicationService.SubItemOption(
                                "SI-01", "登录校验", true)),
                        true)));
        WorkspaceFileWebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        handler.setRequirementImportService(importService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_workspace",
                List.of("""
                        {"id":"req_items","op":"workspace.requirement-import-items","params":{"workspaceId":"wrk_1234567890abcdef","appShortName":"APP-A","editionId":"2026年8月"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).singleElement().satisfies(message ->
                assertThat(message)
                        .contains("\"type\":\"result\"", "\"itemNo\":\"SI-01\"", "\"imported\":true")
                        .doesNotContain("documentUrl", "physicalRootPath"));
        verify(workspaceService).requireWorkspaceWriteAccess(
                workspaceId, new UserId("usr_1234567890abcdef"), false);
        verify(importService).listWorkspaceItems("u001", workspaceId.value(), "APP-A", "2026年8月");
    }

    @Test
    void sharedWorkspaceCannotReadImportStateOrImportRequirements() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        RequirementImportApplicationService importService = Mockito.mock(RequirementImportApplicationService.class);
        when(ticketService.consume("wft_shared", "http://localhost:3000")).thenReturn(sharedWorkspaceTicket(true));
        WorkspaceFileWebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        handler.setRequirementImportService(importService);
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_shared",
                List.of(
                        """
                        {"id":"req_items","op":"workspace.requirement-import-items","params":{"workspaceId":"wrk_1234567890abcdef","appShortName":"APP-A","editionId":"2026年8月"}}
                        """,
                        """
                        {"id":"req_import","op":"workspace.requirement-import","params":{"workspaceId":"wrk_1234567890abcdef","appShortName":"APP-A","editionId":"2026年8月","selectedSubItemNos":["SI-01"],"requestId":"browser-1"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(2).allSatisfy(message ->
                assertThat(message).contains("\"type\":\"error\"", "\"code\":\"FORBIDDEN\""));
        verify(importService, never()).importRequirements(Mockito.anyString(), Mockito.any());
        verify(importService, never()).listWorkspaceItems(
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
    }

    @Test
    void supportReadOnlyTicketFiltersAgentConfigAndAuditsBeforeReturningList() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        WorkspaceFileSocketTicket ticket = supportWorkspaceTicket();
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        SupportAccessAuthorization authorization = Mockito.mock(SupportAccessAuthorization.class);
        when(ticketService.consume("wft_support", "http://localhost:3000")).thenReturn(ticket);
        when(ticketService.authorizeWorkspaceRpc(ticket, workspaceId)).thenReturn(authorization);
        when(workspaceService.listFiles(workspaceId, "")).thenReturn(List.of(
                new FileTreeEntryResponse(".opencode", ".opencode", true, 0, NOW),
                new FileTreeEntryResponse("README.md", "README.md", false, 12, NOW)));
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_support",
                List.of("""
                        {"id":"req_support_list","op":"workspace.list","params":{"workspaceId":"wrk_1234567890abcdef","path":""}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).singleElement().satisfies(message -> {
            assertThat(message).contains("README.md", "\"type\":\"result\"");
            assertThat(message).doesNotContain(".opencode");
        });
        verify(ticketService).recordSupportRpc(
                authorization, "workspace.list", workspaceId, "", "SUCCESS", null, TRACE_ID);
    }

    @Test
    void sharedWorkspaceReadIsReauthorizedAndAuditedWithoutBody() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        WorkspaceFileSocketTicket ticket = sharedWorkspaceTicket(true);
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        when(ticketService.consume("wft_shared", "http://localhost:3000")).thenReturn(ticket);
        when(workspaceService.readFile(workspaceId, "docs/secret.md"))
                .thenReturn(new com.enterprise.testagent.workspace.FileContentResponse(
                        "docs/secret.md", "sensitive body", 14));
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
        FakeWebSocketSession session = FakeWebSocketSession.allowed(
                "/api/internal/platform/workspace-management/file/ws?ticket=wft_shared",
                List.of("""
                        {"id":"req_shared_read","op":"workspace.read","params":{"workspaceId":"wrk_1234567890abcdef","path":"docs/secret.md"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).singleElement().satisfies(message ->
                assertThat(message).contains("\"type\":\"result\"", "sensitive body"));
        verify(ticketService).authorizeWorkspaceRpc(ticket, workspaceId);
        verify(ticketService).recordSharedRpc(
                ticket, "workspace.read", workspaceId, "docs/secret.md", "SUCCESS", null, TRACE_ID);
    }

    private static WorkspaceFileWebSocketHandler handler(
            WorkspaceFileSocketTicketService ticketService,
            AgentConfigApplicationService agentConfigService) {
        return new WorkspaceFileWebSocketHandler(
                ticketService,
                Mockito.mock(WorkspaceApplicationService.class),
                Mockito.mock(WorkspaceDirectoryService.class),
                agentConfigService,
                new ObjectMapper().findAndRegisterModules(),
                "http://localhost:3000");
    }

    private static WorkspaceFileSocketTicketService realWorkspaceTicketService(
            WorkspaceApplicationService workspaceService,
            UserOpencodeProcessAssignmentService assignmentService,
            ConversationWorkspaceAccessAuthorizer authorizer) {
        return new WorkspaceFileSocketTicketService(
                workspaceService,
                assignmentService,
                new WorkspaceFileSocketTicketStore(
                        Clock.fixed(NOW, ZoneOffset.UTC), () -> "wft_dynamic"),
                authorizer);
    }

    private static ConversationWorkspaceAccessAuthorizer appSourceAuthorizer(
            WorkspaceId workspaceId,
            AppSourceRepository appSources) {
        ManagedWorkspaceRepository managed = Mockito.mock(ManagedWorkspaceRepository.class);
        ConfigurationManagementRepository configuration = Mockito.mock(ConfigurationManagementRepository.class);
        WorkspaceRepository workspaces = Mockito.mock(WorkspaceRepository.class);
        ApplicationId appId = new ApplicationId("app_1");
        CodeRepositoryId repositoryId = new CodeRepositoryId("repo_source");
        Instant acceptedAt = Instant.parse("2099-01-01T00:00:00Z");
        when(appSources.findSlot(repositoryId)).thenReturn(Optional.of(new AppSourceRepositorySlot(
                repositoryId, 1L, null, 2L, "release..1", 1L, NOW, NOW)));
        when(appSources.findSnapshot(repositoryId, 1L)).thenReturn(Optional.of(new AppSourceSnapshot(
                repositoryId, 1L, "source", AppSourcePurpose.TEAM,
                new UserId("usr_1234567890abcdef"), "main", "a".repeat(40),
                List.of(new AppSourceSelectedPath(".", AppSourcePathType.DIRECTORY)),
                "b".repeat(64), acceptedAt, acceptedAt.plusSeconds(3600),
                AppSourceSnapshotStatus.ACTIVE, NOW, NOW)));
        when(configuration.findApplicationsByRepository(repositoryId)).thenReturn(List.of(
                new ApplicationDefinition(appId, "应用", true, NOW, NOW)));
        when(configuration.findRepositoriesByApplication(appId)).thenReturn(List.of(new CodeRepository(
                repositoryId, "https://git.example/source.git", "源码", "source",
                CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(), false, NOW, NOW)));
        when(configuration.isActiveMember(appId, new UserId("usr_1234567890abcdef"))).thenReturn(true);
        when(workspaces.findById(workspaceId)).thenReturn(Optional.of(new Workspace(
                workspaceId, "Source", "/source", WorkspaceStatus.ACTIVE,
                NOW.minusSeconds(60), NOW, "server-a", TRACE_ID)));
        return new ManagedConversationWorkspaceAccessAuthorizer(
                managed, configuration, appSources, workspaces);
    }

    private static AppSourceReplica appSourceReplica(WorkspaceId workspaceId) {
        return new AppSourceReplica(
                new CodeRepositoryId("repo_source"), 1L, new LinuxServerId("server-a"), workspaceId,
                AppSourceReplicaStatus.READY, null, null, 1, null, null, null,
                NOW.minusSeconds(60), NOW);
    }

    private static UserOpencodeProcessFileRoutingAffinity readyAffinity(String linuxServerId) {
        return new UserOpencodeProcessFileRoutingAffinity(
                UserOpencodeProcessAvailability.READY,
                false,
                "ready",
                "ocp_1234567890abcdef",
                linuxServerId,
                "ctr_01",
                4096,
                linuxServerId + ":4096",
                NOW);
    }

    private static AuthPrincipal workspacePrincipal() {
        return workspacePrincipal(false);
    }

    private static AuthPrincipal workspacePrincipal(boolean superAdmin) {
        return new AuthPrincipal(
                "token_123",
                new UserId("usr_1234567890abcdef"),
                "tester",
                "tester",
                List.of(superAdmin ? Dictionary.ROLE_SUPER_ADMIN : Dictionary.ROLE_USER),
                NOW,
                NOW.plusSeconds(3600));
    }

    private static String uploadId(String response) {
        try {
            return new ObjectMapper().readTree(response).path("data").path("uploadId").asText();
        } catch (Exception exception) {
            throw new AssertionError("无法解析上传会话响应", exception);
        }
    }

    private static WorkspaceFileSocketTicket agentTicket(
            boolean superAdmin,
            String scope,
            String workspaceId,
            String worktreeId) {
        return new WorkspaceFileSocketTicket(
                "wft_public",
                workspaceId,
                "linux-1",
                null,
                superAdmin,
                superAdmin,
                "usr_admin",
                "agent-config",
                scope,
                worktreeId,
                TRACE_ID,
                NOW.plusSeconds(60));
    }

    private static WorkspaceFileSocketTicket workspaceTicket(String workspaceId) {
        return new WorkspaceFileSocketTicket(
                "wft_workspace",
                workspaceId,
                "linux-1",
                "linux-1",
                false,
                false,
                "usr_1234567890abcdef",
                "workspace",
                null,
                null,
                TRACE_ID,
                NOW.plusSeconds(60));
    }

    private static WorkspaceFileSocketTicket workspaceTicket(String workspaceId, String unifiedAuthId) {
        WorkspaceFileSocketTicket ticket = workspaceTicket(workspaceId);
        return new WorkspaceFileSocketTicket(
                ticket.ticket(), ticket.workspaceId(), ticket.linuxServerId(), ticket.agentLinuxServerId(),
                ticket.appSourceWorkspace(), ticket.superAdmin(), ticket.appAdmin(), ticket.userId(),
                ticket.mode(), ticket.scope(), ticket.worktreeId(), ticket.supportReadOnly(),
                ticket.supportGrantId(), ticket.supportGrantTokenDigest(), ticket.supportActorSessionDigest(),
                ticket.supportTargetUserId(), ticket.traceId(), ticket.expiresAt(), ticket.shareId(),
                ticket.shareVersion(), ticket.shareActorUserId(), ticket.executionOwnerUserId(),
                ticket.shareCanChat(), ticket.shareExpiresAt(), ticket.shareSessionId(), unifiedAuthId);
    }

    private static WorkspaceFileSocketTicket sharedWorkspaceTicket(boolean canChat) {
        return new WorkspaceFileSocketTicket(
                "wft_shared",
                "wrk_1234567890abcdef",
                "linux-1",
                "linux-1",
                false,
                false,
                false,
                "usr_session_owner",
                "workspace",
                null,
                null,
                false,
                null,
                null,
                null,
                null,
                TRACE_ID,
                NOW.plusSeconds(60),
                "shr_" + "b".repeat(64),
                4L,
                "usr_shared_actor",
                "usr_session_owner",
                canChat,
                NOW.plusSeconds(3600),
                "ses_shared_scope");
    }

    private static WorkspaceFileSocketTicket supportWorkspaceTicket() {
        return new WorkspaceFileSocketTicket(
                "wft_support",
                "wrk_1234567890abcdef",
                "linux-1",
                null,
                false,
                true,
                false,
                "usr_support_actor",
                "workspace",
                null,
                null,
                true,
                "sag_support",
                "1".repeat(64),
                "2".repeat(64),
                "usr_support_target",
                TRACE_ID,
                NOW.plusSeconds(60));
    }

    private static WorkspaceFileSocketTicket workspaceAgentTicket(boolean appAdmin) {
        return new WorkspaceFileSocketTicket(
                "wft_workspace_agent",
                "wrk_1234567890abcdef",
                "linux-1",
                null,
                false,
                appAdmin,
                "usr_app_admin",
                "agent-config",
                "WORKSPACE",
                null,
                TRACE_ID,
                NOW.plusSeconds(60));
    }

    private static final class FakeWebSocketSession implements WebSocketSession {
        private final HandshakeInfo handshakeInfo;
        private final List<String> incoming;
        private final DataBufferFactory bufferFactory = DefaultDataBufferFactory.sharedInstance;
        private final List<String> sentText = new ArrayList<>();
        private final Function<String, List<String>> afterFirstResponse;
        private final reactor.core.publisher.Sinks.One<String> firstResponse = reactor.core.publisher.Sinks.one();

        private FakeWebSocketSession(String path, List<String> incoming) {
            this(path, incoming, null);
        }

        private FakeWebSocketSession(
                String path,
                List<String> incoming,
                Function<String, List<String>> afterFirstResponse) {
            this(path, incoming, afterFirstResponse, "http://localhost:3000");
        }

        private FakeWebSocketSession(
                String path,
                List<String> incoming,
                Function<String, List<String>> afterFirstResponse,
                String origin) {
            HttpHeaders headers = new HttpHeaders();
            headers.setOrigin(origin);
            headers.set("X-Trace-Id", TRACE_ID);
            this.handshakeInfo = new HandshakeInfo(URI.create("ws://127.0.0.1:8080" + path), headers, Mono.<Principal>empty(), null);
            this.incoming = List.copyOf(incoming);
            this.afterFirstResponse = afterFirstResponse;
        }

        static FakeWebSocketSession allowed(String path, List<String> incoming) {
            return new FakeWebSocketSession(path, incoming);
        }

        static FakeWebSocketSession withOrigin(String path, List<String> incoming, String origin) {
            return new FakeWebSocketSession(path, incoming, null, origin);
        }

        static FakeWebSocketSession uploadFlow(
                String path,
                String begin,
                Function<String, List<String>> afterFirstResponse) {
            return new FakeWebSocketSession(path, List.of(begin), afterFirstResponse);
        }

        List<String> sentText() {
            return sentText;
        }

        @Override
        public String getId() {
            return "ws_test";
        }

        @Override
        public HandshakeInfo getHandshakeInfo() {
            return handshakeInfo;
        }

        @Override
        public DataBufferFactory bufferFactory() {
            return bufferFactory;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return Map.of();
        }

        @Override
        public Flux<WebSocketMessage> receive() {
            Flux<WebSocketMessage> initial = Flux.fromIterable(incoming).map(this::textMessage);
            if (afterFirstResponse == null) {
                return initial;
            }
            return Flux.concat(
                    initial,
                    firstResponse.asMono()
                            .flatMapMany(response -> Flux.fromIterable(afterFirstResponse.apply(response)))
                            .map(this::textMessage));
        }

        @Override
        public Mono<Void> send(Publisher<WebSocketMessage> messages) {
            return Flux.from(messages)
                    .doOnNext(message -> {
                        String payload = message.getPayloadAsText();
                        sentText.add(payload);
                        firstResponse.tryEmitValue(payload);
                    })
                    .then();
        }

        @Override
        public boolean isOpen() {
            return true;
        }

        @Override
        public Mono<Void> close(CloseStatus status) {
            return Mono.empty();
        }

        @Override
        public Mono<CloseStatus> closeStatus() {
            return Mono.just(CloseStatus.NORMAL);
        }

        @Override
        public WebSocketMessage textMessage(String payload) {
            return new WebSocketMessage(
                    WebSocketMessage.Type.TEXT,
                    bufferFactory.wrap(payload.getBytes(StandardCharsets.UTF_8)));
        }

        @Override
        public WebSocketMessage binaryMessage(java.util.function.Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.BINARY, payloadFactory.apply(bufferFactory));
        }

        @Override
        public WebSocketMessage pingMessage(java.util.function.Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.PING, payloadFactory.apply(bufferFactory));
        }

        @Override
        public WebSocketMessage pongMessage(java.util.function.Function<DataBufferFactory, DataBuffer> payloadFactory) {
            return new WebSocketMessage(WebSocketMessage.Type.PONG, payloadFactory.apply(bufferFactory));
        }
    }
}
