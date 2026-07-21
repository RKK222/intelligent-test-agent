package com.enterprise.testagent.api.web.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.workspace.FileContentResponse;
import com.enterprise.testagent.workspace.WorkspaceApplicationService;
import com.enterprise.testagent.workspace.WorkspaceDirectoryService;
import com.enterprise.testagent.workspace.AgentConfigApplicationService;
import com.enterprise.testagent.workspace.WorkspaceViewApplicationService;
import com.enterprise.testagent.workspace.WorkspaceViewEntry;
import com.enterprise.testagent.workspace.WorkspaceViewListResponse;
import com.enterprise.testagent.workspace.WorkspaceViewLocator;
import com.enterprise.testagent.workspace.WorkspaceViewLocatorKind;
import com.enterprise.testagent.workspace.WorkspaceViewReadResponse;
import com.enterprise.testagent.workspace.WorkspaceViewSource;
import com.enterprise.testagent.domain.workspace.ConversationWorkspaceAccessAuthorizer;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.user.UserId;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(2).allSatisfy(message -> {
            assertThat(message).contains("\"type\":\"error\"");
            assertThat(message).contains("\"code\":\"FORBIDDEN\"");
        });
        verify(agentConfigService, never()).writePublicAgentFile("review.md", "changed", "agw_123", new UserId("usr_admin"));
        verify(agentConfigService, never()).deletePublicAgentFile("review.md", "agw_123", new UserId("usr_admin"));
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
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(1).allSatisfy(message -> {
            assertThat(message).contains("\"type\":\"error\"");
            assertThat(message).contains("\"code\":\"FORBIDDEN\"");
        });
        verify(agentConfigService, never()).renameWorkspaceAgentFile(
                "wrk_1234567890abcdef",
                "agents/review.md",
                "payment-review.md",
                null);
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
                        {"id":"req_delete","op":"workspace.delete","params":{"workspaceId":"wrk_1234567890abcdef","path":".opencode"}}
                        """, """
                        {"id":"req_delete_alias","op":"workspace.delete","params":{"workspaceId":"wrk_1234567890abcdef","path":"./tmp/../.opencode"}}
                        """));

        handler.handle(session).block();

        assertThat(session.sentText()).hasSize(3).allSatisfy(message -> {
            assertThat(message).contains("\"type\":\"error\"");
            assertThat(message).contains("\"code\":\"FORBIDDEN\"");
        });
        verify(workspaceService, never()).writeFile(Mockito.any(), Mockito.anyString(), Mockito.anyString());
        verify(workspaceService, never()).deleteFile(Mockito.any(), Mockito.anyString());
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
    void listsCompositeWorkspaceViewAndMapsOnlyLogicalLocatorFields() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        WorkspaceViewApplicationService viewService = Mockito.mock(WorkspaceViewApplicationService.class);
        ConversationWorkspaceAccessAuthorizer authorizer = Mockito.mock(ConversationWorkspaceAccessAuthorizer.class);
        when(ticketService.consume("wft_workspace", "http://localhost:3000"))
                .thenReturn(workspaceTicket("wrk_1234567890abcdef"));
        WorkspaceViewLocator root = WorkspaceViewLocator.root();
        when(viewService.list(new WorkspaceId("wrk_1234567890abcdef"), root)).thenReturn(new WorkspaceViewListResponse(
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
                authorizer,
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
        verify(authorizer).requireFileAccess(
                new UserId("usr_1234567890abcdef"),
                new WorkspaceId("wrk_1234567890abcdef"),
                false);
        verify(viewService).list(new WorkspaceId("wrk_1234567890abcdef"), root);
    }

    @Test
    void readsReferenceFileThroughLogicalLocator() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceViewApplicationService viewService = Mockito.mock(WorkspaceViewApplicationService.class);
        ConversationWorkspaceAccessAuthorizer authorizer = Mockito.mock(ConversationWorkspaceAccessAuthorizer.class);
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        WorkspaceViewLocator locator = new WorkspaceViewLocator(
                WorkspaceViewLocatorKind.REFERENCE,
                "guide.md",
                "docs-requirements");
        when(ticketService.consume("wft_workspace", "http://localhost:3000"))
                .thenReturn(workspaceTicket(workspaceId.value()));
        when(viewService.read(workspaceId, locator)).thenReturn(new WorkspaceViewReadResponse(
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
                authorizer,
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
        verify(authorizer).requireFileAccess(new UserId("usr_1234567890abcdef"), workspaceId, false);
        verify(viewService).read(workspaceId, locator);
    }

    @Test
    void revokedMembershipStopsTheNextWorkspaceModeRpcOnTheSameSocket() {
        WorkspaceFileSocketTicketService ticketService = Mockito.mock(WorkspaceFileSocketTicketService.class);
        WorkspaceApplicationService workspaceService = Mockito.mock(WorkspaceApplicationService.class);
        WorkspaceViewApplicationService viewService = Mockito.mock(WorkspaceViewApplicationService.class);
        ConversationWorkspaceAccessAuthorizer authorizer = Mockito.mock(ConversationWorkspaceAccessAuthorizer.class);
        WorkspaceId workspaceId = new WorkspaceId("wrk_1234567890abcdef");
        UserId userId = new UserId("usr_1234567890abcdef");
        when(ticketService.consume("wft_workspace", "http://localhost:3000"))
                .thenReturn(workspaceTicket(workspaceId.value()));
        when(workspaceService.listFiles(workspaceId, "")).thenReturn(List.of());
        Mockito.doNothing()
                .doThrow(new com.enterprise.testagent.common.error.PlatformException(
                        com.enterprise.testagent.common.error.ErrorCode.FORBIDDEN,
                        "成员关系已失效"))
                .when(authorizer)
                .requireFileAccess(userId, workspaceId, false);
        WebSocketHandler handler = new WorkspaceFileWebSocketHandler(
                ticketService,
                workspaceService,
                Mockito.mock(WorkspaceDirectoryService.class),
                Mockito.mock(AgentConfigApplicationService.class),
                viewService,
                authorizer,
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
                Mockito.mock(ConversationWorkspaceAccessAuthorizer.class),
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

        private FakeWebSocketSession(String path, List<String> incoming) {
            HttpHeaders headers = new HttpHeaders();
            headers.setOrigin("http://localhost:3000");
            headers.set("X-Trace-Id", TRACE_ID);
            this.handshakeInfo = new HandshakeInfo(URI.create("ws://127.0.0.1:8080" + path), headers, Mono.<Principal>empty(), null);
            this.incoming = List.copyOf(incoming);
        }

        static FakeWebSocketSession allowed(String path, List<String> incoming) {
            return new FakeWebSocketSession(path, incoming);
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
            return Flux.fromIterable(incoming).map(this::textMessage);
        }

        @Override
        public Mono<Void> send(Publisher<WebSocketMessage> messages) {
            return Flux.from(messages)
                    .doOnNext(message -> sentText.add(message.getPayloadAsText()))
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
