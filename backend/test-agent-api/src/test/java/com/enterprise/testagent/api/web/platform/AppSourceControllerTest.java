package com.enterprise.testagent.api.web.platform;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.GlobalExceptionHandler;
import com.enterprise.testagent.api.web.common.TraceIdWebFilter;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.git.GitRemoteService;
import com.enterprise.testagent.domain.appsource.AppSourceOperation;
import com.enterprise.testagent.domain.appsource.AppSourceOperationStatus;
import com.enterprise.testagent.domain.appsource.AppSourceOperationType;
import com.enterprise.testagent.domain.appsource.AppSourcePathType;
import com.enterprise.testagent.domain.appsource.AppSourcePurpose;
import com.enterprise.testagent.domain.appsource.AppSourceSelectedPath;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignment;
import com.enterprise.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService;
import com.enterprise.testagent.workspace.AppSourceApplicationService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

class AppSourceControllerTest {

    private static final UserId USER_ID = new UserId("usr_1234567890abcdef");
    private static final String TRACE_ID = "trace_1234567890abcdef";
    private static final String TREE_COMMIT = "0123456789abcdef0123456789abcdef01234567";
    private static final Instant NOW = Instant.parse("2026-07-28T04:00:00Z");

    @Test
    void repositoryListSerializesTheFourDownloadStatesWithExactWireValues() {
        AppSourceApplicationService service = mock(AppSourceApplicationService.class);
        when(service.listRepositories("app_1", USER_ID, false, "server-a")).thenReturn(List.of(
                summary("repo_1", AppSourceApplicationService.DownloadState.NOT_DOWNLOADED),
                summary("repo_2", AppSourceApplicationService.DownloadState.DOWNLOADED_ACTIVE),
                summary("repo_3", AppSourceApplicationService.DownloadState.DOWNLOADED_EXPIRED),
                summary("repo_4", AppSourceApplicationService.DownloadState.PERSONAL_OCCUPIED)));

        client(service, List.of("USER")).get()
                .uri("/api/internal/platform/workspace-management/applications/app_1/app-source-repositories")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].downloadState").isEqualTo("NOT_DOWNLOADED")
                .jsonPath("$.data[1].downloadState").isEqualTo("DOWNLOADED_ACTIVE")
                .jsonPath("$.data[2].downloadState").isEqualTo("DOWNLOADED_EXPIRED")
                .jsonPath("$.data[3].downloadState").isEqualTo("PERSONAL_OCCUPIED");

        verify(service).listRepositories("app_1", USER_ID, false, "server-a");
    }

    @Test
    void businessAuthorizationFailureUsesTheUnifiedErrorEnvelope() {
        AppSourceApplicationService service = mock(AppSourceApplicationService.class);
        when(service.listRepositories("app_1", USER_ID, false, "server-a"))
                .thenThrow(new PlatformException(ErrorCode.FORBIDDEN, "当前用户不是应用成员"));

        client(service, List.of("USER")).get()
                .uri("/api/internal/platform/workspace-management/applications/app_1/app-source-repositories")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.success").isEqualTo(false)
                .jsonPath("$.code").isEqualTo("FORBIDDEN")
                .jsonPath("$.message").isEqualTo("当前用户不是应用成员")
                .jsonPath("$.traceId").isEqualTo(TRACE_ID);
    }

    @Test
    void materializationMapsEveryRequestFieldAndReturnsPersistedOperationSnapshot() {
        AppSourceApplicationService service = mock(AppSourceApplicationService.class);
        AppSourceOperation operation = new AppSourceOperation(
                "aso_12345678", new com.enterprise.testagent.domain.configuration.ApplicationId("app_1"),
                new com.enterprise.testagent.domain.configuration.CodeRepositoryId("repo_1"),
                3L, 4L, USER_ID, AppSourceOperationType.DOWNLOAD, "request-hash",
                AppSourceOperationStatus.PENDING, TRACE_ID, NOW, null);
        when(service.materialize(
                eq("app_1"), eq("repo_1"), org.mockito.ArgumentMatchers.any(),
                eq(USER_ID), eq(true), eq(TRACE_ID))).thenReturn(operation);
        when(service.getOperation("aso_12345678", USER_ID, true)).thenReturn(operationSnapshot(
                "aso_12345678", AppSourceOperationStatus.PENDING));

        client(service, List.of("APP_ADMIN")).post()
                .uri("/api/internal/platform/workspace-management/applications/app_1/app-source-repositories/repo_1/materializations")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("""
                        {
                          "operationId":"aso_12345678",
                          "expectedGeneration":3,
                          "branch":"feature/source",
                          "expectedTreeCommit":"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                          "selectedPaths":[{"path":"src","type":"DIRECTORY"}],
                          "purpose":"TEAM",
                          "retentionHours":6,
                          "confirmReplace":true
                        }
                        """)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.operationId").isEqualTo("aso_12345678")
                .jsonPath("$.data.targetGeneration").isEqualTo(4)
                .jsonPath("$.data.traceId").isEqualTo(TRACE_ID);

        ArgumentCaptor<AppSourceApplicationService.MaterializationCommand> command =
                ArgumentCaptor.forClass(AppSourceApplicationService.MaterializationCommand.class);
        verify(service).materialize(
                eq("app_1"), eq("repo_1"), command.capture(), eq(USER_ID), eq(true), eq(TRACE_ID));
        org.assertj.core.api.Assertions.assertThat(command.getValue()).satisfies(value -> {
            org.assertj.core.api.Assertions.assertThat(value.operationId()).isEqualTo("aso_12345678");
            org.assertj.core.api.Assertions.assertThat(value.expectedGeneration()).isEqualTo(3L);
            org.assertj.core.api.Assertions.assertThat(value.branch()).isEqualTo("feature/source");
            org.assertj.core.api.Assertions.assertThat(value.expectedTreeCommit()).isEqualTo("b".repeat(40));
            org.assertj.core.api.Assertions.assertThat(value.selectedPaths()).singleElement().satisfies(path -> {
                org.assertj.core.api.Assertions.assertThat(path.path()).isEqualTo("src");
                org.assertj.core.api.Assertions.assertThat(path.pathType()).isEqualTo(AppSourcePathType.DIRECTORY);
            });
            org.assertj.core.api.Assertions.assertThat(value.purpose()).isEqualTo(AppSourcePurpose.TEAM);
            org.assertj.core.api.Assertions.assertThat(value.retentionHours()).isEqualTo(6);
            org.assertj.core.api.Assertions.assertThat(value.confirmReplace()).isTrue();
        });
    }

    @Test
    void branchAndTreeReadsStayBehindTheBusinessService() {
        AppSourceApplicationService service = mock(AppSourceApplicationService.class);
        when(service.listBranches("app_1", "repo_1", USER_ID)).thenReturn(List.of("main", "release/2026"));
        when(service.listTree("app_1", "repo_1", "release/2026", "src", USER_ID)).thenReturn(List.of(
                new GitRemoteService.RemoteTreeNode(
                        "Main.java", "src/Main.java", GitRemoteService.NODE_TYPE_FILE, List.of())));
        WebTestClient client = client(service, List.of("USER"));

        client.get()
                .uri("/api/internal/platform/workspace-management/applications/app_1/"
                        + "app-source-repositories/repo_1/branches")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[1]").isEqualTo("release/2026");
        client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/internal/platform/workspace-management/applications/app_1/"
                                + "app-source-repositories/repo_1/tree")
                        .queryParam("branch", "release/2026")
                        .queryParam("path", "src")
                        .build())
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].path").isEqualTo("src/Main.java")
                .jsonPath("$.data[0].type").isEqualTo("file");
    }

    @Test
    void treeKeepsDefaultArrayAndReturnsCommitEnvelopeOnlyWhenRequested() {
        AppSourceApplicationService service = mock(AppSourceApplicationService.class);
        GitRemoteService.RemoteTreeNode node = new GitRemoteService.RemoteTreeNode(
                "Main.java", "src/Main.java", GitRemoteService.NODE_TYPE_FILE, List.of());
        when(service.listTree("app_1", "repo_1", "release/2026", "src", USER_ID))
                .thenReturn(List.of(node));
        when(service.getTreeSnapshot("app_1", "repo_1", "release/2026", "src", USER_ID))
                .thenReturn(new AppSourceApplicationService.TreeSnapshot(TREE_COMMIT, List.of(node)));
        WebTestClient client = client(service, List.of("USER"));

        client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/internal/platform/workspace-management/applications/app_1/"
                                + "app-source-repositories/repo_1/tree")
                        .queryParam("branch", "release/2026")
                        .queryParam("path", "src")
                        .build())
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data[0].path").isEqualTo("src/Main.java")
                .jsonPath("$.data.targetCommit").doesNotExist();

        client.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/internal/platform/workspace-management/applications/app_1/"
                                + "app-source-repositories/repo_1/tree")
                        .queryParam("branch", "release/2026")
                        .queryParam("path", "src")
                        .queryParam("includeCommit", true)
                        .build())
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.targetCommit").isEqualTo(TREE_COMMIT)
                .jsonPath("$.data.nodes[0].path").isEqualTo("src/Main.java")
                .jsonPath("$.data.nodes[0].type").isEqualTo("file");

        verify(service).listTree("app_1", "repo_1", "release/2026", "src", USER_ID);
        verify(service).getTreeSnapshot("app_1", "repo_1", "release/2026", "src", USER_ID);
    }

    @Test
    void retryOpenAndRecentEndpointsUseCurrentUserServerAndPersistedOperation() {
        AppSourceApplicationService service = mock(AppSourceApplicationService.class);
        AppSourceOperation retry = new AppSourceOperation(
                "aso_retry", new com.enterprise.testagent.domain.configuration.ApplicationId("app_1"),
                new com.enterprise.testagent.domain.configuration.CodeRepositoryId("repo_1"),
                4L, 4L, USER_ID, AppSourceOperationType.RETRY_REPLICAS, "request-hash",
                AppSourceOperationStatus.PENDING, TRACE_ID, NOW, null);
        when(service.retry(
                eq("app_1"), eq("repo_1"), org.mockito.ArgumentMatchers.any(),
                eq(USER_ID), eq(false), eq(TRACE_ID))).thenReturn(retry);
        when(service.getOperation("aso_retry", USER_ID, false))
                .thenReturn(operationSnapshot("aso_retry", AppSourceOperationStatus.PENDING));
        AppSourceApplicationService.OpenResult open = new AppSourceApplicationService.OpenResult(
                "app_1", "repo_1", 4L, AppSourcePurpose.TEAM,
                "wrk_source", "server-a", NOW.plusSeconds(3600));
        when(service.open("app_1", "repo_1", 4L, USER_ID, "server-a")).thenReturn(open);
        when(service.recent(USER_ID, "server-a")).thenReturn(Optional.of(open));
        WebTestClient client = client(service, List.of("USER"));

        client.post()
                .uri("/api/internal/platform/workspace-management/applications/app_1/"
                        + "app-source-repositories/repo_1/replica-retries")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"operationId\":\"aso_retry\",\"expectedGeneration\":4}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.operationId").isEqualTo("aso_retry");
        client.post()
                .uri("/api/internal/platform/workspace-management/applications/app_1/"
                        + "app-source-repositories/repo_1/open")
                .header("X-Trace-Id", TRACE_ID)
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"generation\":4}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.workspaceId").isEqualTo("wrk_source")
                .jsonPath("$.data.linuxServerId").isEqualTo("server-a");
        client.get()
                .uri("/api/internal/platform/workspace-management/recent-app-source")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.data.generation").isEqualTo(4);
        client.delete()
                .uri("/api/internal/platform/workspace-management/recent-app-source")
                .header("X-Trace-Id", TRACE_ID)
                .exchange()
                .expectStatus().isOk();

        verify(service).open("app_1", "repo_1", 4L, USER_ID, "server-a");
        verify(service).clearRecent(USER_ID, "server-a");
    }

    private WebTestClient client(AppSourceApplicationService service, List<String> roles) {
        AuthPrincipal principal = new AuthPrincipal(
                "token", USER_ID, "888888888", "测试用户", roles,
                NOW.minusSeconds(60), NOW.plusSeconds(3600));
        UserOpencodeProcessAssignmentService assignments = mock(UserOpencodeProcessAssignmentService.class);
        when(assignments.requireReadyProcess(USER_ID, "opencode", TRACE_ID))
                .thenReturn(new UserOpencodeProcessAssignment(
                        new ExecutionNode(
                                new ExecutionNodeId("node_1234567890abcdef"),
                                "http://server-a:4096",
                                ExecutionNodeStatus.READY,
                                0,
                                4,
                                100,
                                NOW.minusSeconds(60),
                                Set.of("opencode"),
                                NOW.minusSeconds(60),
                                NOW,
                                TRACE_ID),
                        "server-a"));
        return WebTestClient.bindToController(new AppSourceController(service, assignments))
                .webFilter(new TraceIdWebFilter())
                .webFilter((exchange, chain) -> {
                    exchange.getAttributes().put(AuthWebSupport.AUTH_ATTR, principal);
                    return chain.filter(exchange);
                })
                .controllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private AppSourceApplicationService.RepositorySummary summary(
            String repositoryId,
            AppSourceApplicationService.DownloadState state) {
        return new AppSourceApplicationService.RepositorySummary(
                repositoryId, "源码库", "billing-service", state, null, null, null,
                null, null, null, null, List.of(), null, false, false, false,
                state.name(), null, List.of());
    }

    private AppSourceApplicationService.OperationSnapshot operationSnapshot(
            String operationId,
            AppSourceOperationStatus status) {
        return new AppSourceApplicationService.OperationSnapshot(
                operationId, "app_1", "repo_1", 3L, 4L, AppSourceOperationType.DOWNLOAD,
                status, AppSourcePurpose.TEAM, "feature/source", "b".repeat(40),
                List.of(new AppSourceSelectedPath("src", AppSourcePathType.DIRECTORY)),
                NOW.plusSeconds(3600), TRACE_ID, NOW, null, List.of(), List.of());
    }
}
