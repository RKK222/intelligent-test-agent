package com.enterprise.testagent.opencode.runtime.localclient;

import com.enterprise.testagent.domain.broadcast.ServerBroadcastEvent;
import com.enterprise.testagent.domain.broadcast.ServerBroadcastHandler;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.workspace.WorkspaceGitAccessCheck;
import com.enterprise.testagent.domain.workspace.WorkspaceGitAccessCheckRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceGitAccessInspectionEvents;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** 响应全局巡检广播，只检查当前 Java 实际持有连接的本地客户端工作空间。 */
@Component
public class LocalWorkspaceGitAccessInspectionHandler implements ServerBroadcastHandler {

    static final String CAPABILITY = "WORKSPACE_GIT_ACCESS_V1";
    static final int PAGE_SIZE = 200;
    private static final Logger LOGGER =
            LoggerFactory.getLogger(LocalWorkspaceGitAccessInspectionHandler.class);

    private final WorkspaceGitAccessCheckRepository checks;
    private final LocalClientInstanceRepository instances;
    private final LocalClientConnectionStore connections;
    private final BackendJavaRouteResolver routeResolver;
    private final LocalClientWorkspaceFileGateway fileGateway;
    private final Clock clock;

    public LocalWorkspaceGitAccessInspectionHandler(
            WorkspaceGitAccessCheckRepository checks,
            LocalClientInstanceRepository instances,
            LocalClientConnectionStore connections,
            BackendJavaRouteResolver routeResolver,
            LocalClientWorkspaceFileGateway fileGateway,
            Clock clock) {
        this.checks = Objects.requireNonNull(checks);
        this.instances = Objects.requireNonNull(instances);
        this.connections = Objects.requireNonNull(connections);
        this.routeResolver = Objects.requireNonNull(routeResolver);
        this.fileGateway = Objects.requireNonNull(fileGateway);
        this.clock = Objects.requireNonNull(clock);
    }

    @Override
    public boolean supports(String type) {
        return WorkspaceGitAccessInspectionEvents.LOCAL_CLIENT_INSPECTION_REQUESTED.equals(type);
    }

    @Override
    public void handle(ServerBroadcastEvent event) {
        if (!supports(event.type())) {
            return;
        }
        InspectionResult result = inspectConnected(event.traceId());
        LOGGER.info(
                "event=local_workspace_git_access_inspection_completed checked={} accessible={} inaccessible={} unknown={}",
                result.checked(), result.accessible(), result.inaccessible(), result.unknown());
    }

    /** 分页扫描全部绑定，但只有精确路由属于本 Java 的在线客户端才发反向 RPC。 */
    InspectionResult inspectConnected(String traceId) {
        int checked = 0;
        int accessible = 0;
        int inaccessible = 0;
        int unknown = 0;
        String afterUserId = null;
        String afterWorkspaceId = null;
        while (true) {
            var page = checks.findLocalWorkspaceCandidatesAfter(
                    afterUserId, afterWorkspaceId, PAGE_SIZE);
            if (page.isEmpty()) {
                break;
            }
            for (var candidate : page) {
                LocalClientInstanceId clientInstanceId =
                        new LocalClientInstanceId(candidate.clientInstanceId());
                var instance = instances.findById(clientInstanceId).orElse(null);
                var route = connections.find(clientInstanceId)
                        .filter(value -> value.userId().equals(candidate.userId()))
                        .filter(value -> routeResolver.isCurrent(value.backendProcessId()))
                        .orElse(null);
                if (route == null) {
                    continue;
                }
                WorkspaceGitAccessCheck check;
                if (instance == null || !instance.selfUpdateCapabilities().contains(CAPABILITY)) {
                    check = unknown(candidate, "LOCAL_CLIENT_VERSION_UNSUPPORTED",
                            "本地客户端版本暂不支持 Git 权限巡检", clock.instant());
                } else {
                    check = inspect(candidate, route.backendProcessId(), route.connectionGeneration(), traceId);
                }
                checks.save(check);
                checked++;
                switch (check.status()) {
                    case ACCESSIBLE -> accessible++;
                    case INACCESSIBLE -> inaccessible++;
                    case UNKNOWN -> unknown++;
                }
            }
            var last = page.get(page.size() - 1);
            afterUserId = last.userId().value();
            afterWorkspaceId = last.workspaceId().value();
            if (page.size() < PAGE_SIZE) {
                break;
            }
        }
        return new InspectionResult(checked, accessible, inaccessible, unknown);
    }

    private WorkspaceGitAccessCheck inspect(
            WorkspaceGitAccessCheckRepository.LocalWorkspaceCandidate candidate,
            BackendProcessId backendProcessId,
            long generation,
            String traceId) {
        Instant checkedAt = clock.instant();
        try {
            // backendProcessId 已由公共路由器确认属于本 Java；仅用于执行前再次表达 fencing 事实。
            if (!routeResolver.isCurrent(backendProcessId)) {
                return unknown(candidate, "LOCAL_CLIENT_UNAVAILABLE", "本地客户端连接已变化", checkedAt);
            }
            JsonNode result = fileGateway.checkGitAccess(
                    candidate.clientInstanceId(),
                    generation,
                    candidate.workspaceId().value(),
                    candidate.rootDigest(),
                    traceId);
            String status = required(result, "status");
            if ("ACCESSIBLE".equals(status)) {
                return new WorkspaceGitAccessCheck(
                        candidate.userId(),
                        WorkspaceGitAccessCheck.TargetKind.LOCAL_WORKSPACE,
                        candidate.workspaceId().value(),
                        WorkspaceGitAccessCheck.Status.ACCESSIBLE,
                        null,
                        null,
                        checkedAt);
            }
            WorkspaceGitAccessCheck.Status mapped = WorkspaceGitAccessCheck.Status.valueOf(status);
            if (mapped == WorkspaceGitAccessCheck.Status.ACCESSIBLE) {
                throw new IllegalArgumentException("invalid local Git access status");
            }
            return new WorkspaceGitAccessCheck(
                    candidate.userId(),
                    WorkspaceGitAccessCheck.TargetKind.LOCAL_WORKSPACE,
                    candidate.workspaceId().value(),
                    mapped,
                    required(result, "reason"),
                    required(result, "message"),
                    checkedAt);
        } catch (RuntimeException exception) {
            LOGGER.warn(
                    "event=local_workspace_git_access_inspection_unknown errorType={}",
                    exception.getClass().getSimpleName());
            return unknown(candidate, "LOCAL_CLIENT_UNAVAILABLE", "本地客户端 Git 权限巡检暂时无法完成", checkedAt);
        }
    }

    private WorkspaceGitAccessCheck unknown(
            WorkspaceGitAccessCheckRepository.LocalWorkspaceCandidate candidate,
            String reason,
            String message,
            Instant checkedAt) {
        return new WorkspaceGitAccessCheck(
                candidate.userId(),
                WorkspaceGitAccessCheck.TargetKind.LOCAL_WORKSPACE,
                candidate.workspaceId().value(),
                WorkspaceGitAccessCheck.Status.UNKNOWN,
                reason,
                message,
                checkedAt);
    }

    private static String required(JsonNode result, String field) {
        JsonNode value = result == null ? null : result.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank()) {
            throw new IllegalArgumentException("local Git access response is missing " + field);
        }
        return value.asText();
    }

    record InspectionResult(int checked, int accessible, int inaccessible, int unknown) {
    }
}
