package com.enterprise.testagent.opencode.runtime.protectedagent;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.hub.ProtectedAgentDefinitionResolver;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceBinding;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceRepository;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientWorkspaceFileGateway;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 为服务器 Agent 签发仅限单 Run 的本地文件授权；正文不会发往本地客户端，文件调用复用现有 WSS RPC。
 */
@Service
public class ProtectedAgentFileGrantService {

    private static final Duration GRANT_TTL = Duration.ofHours(4);
    private static final int MAX_GRANTS = 2_000;
    private static final int MAX_RESOURCE_CHARS = 2 * 1024 * 1024;
    private final LocalClientWorkspaceRepository workspaceRepository;
    private final LocalClientConnectionStore connectionStore;
    private final RunRepository runRepository;
    private final LocalClientWorkspaceFileGateway fileGateway;
    private final BackendJavaRouteResolver backendRouteResolver;
    private final Clock clock;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, Grant> grants = new ConcurrentHashMap<>();

    @Autowired
    public ProtectedAgentFileGrantService(
            LocalClientWorkspaceRepository workspaceRepository,
            LocalClientConnectionStore connectionStore,
            RunRepository runRepository,
            LocalClientWorkspaceFileGateway fileGateway,
            BackendJavaRouteResolver backendRouteResolver) {
        this(workspaceRepository, connectionStore, runRepository, fileGateway, backendRouteResolver, Clock.systemUTC());
    }

    ProtectedAgentFileGrantService(
            LocalClientWorkspaceRepository workspaceRepository,
            LocalClientConnectionStore connectionStore,
            RunRepository runRepository,
            LocalClientWorkspaceFileGateway fileGateway,
            BackendJavaRouteResolver backendRouteResolver,
            Clock clock) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository);
        this.connectionStore = Objects.requireNonNull(connectionStore);
        this.runRepository = Objects.requireNonNull(runRepository);
        this.fileGateway = Objects.requireNonNull(fileGateway);
        this.backendRouteResolver = Objects.requireNonNull(backendRouteResolver);
        this.clock = Objects.requireNonNull(clock);
    }

    /** 以当前连接 generation 和根摘要冻结授权；连接变化后旧 Token 立即失效。 */
    public IssuedGrant issue(
            UserId userId,
            RunId runId,
            WorkspaceId workspaceId,
            ProtectedAgentDefinitionResolver.Definition definition) {
        cleanupExpired();
        if (grants.size() >= MAX_GRANTS) {
            throw new PlatformException(ErrorCode.RUNTIME_STATE_UNAVAILABLE, "受保护 Agent 文件授权容量已满");
        }
        LocalClientWorkspaceBinding binding = workspaceRepository.findByWorkspaceId(workspaceId)
                .filter(item -> item.userId().equals(userId))
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.CONFLICT,
                        "受保护 Agent 只能处理当前用户的本地工作区"));
        LocalClientConnectionRoute route = connectionStore.find(binding.clientInstanceId())
                .filter(item -> item.userId().equals(userId) && backendRouteResolver.isCurrent(item.backendProcessId()))
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.LOCAL_CLIENT_DISCONNECTED,
                        "本地 OpenCode 客户端离线"));
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = "pag_" + Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expiresAt = clock.instant().plus(GRANT_TTL);
        Grant grant = new Grant(
                runId,
                userId,
                workspaceId,
                binding,
                route.connectionGeneration(),
                definition.revisionId(),
                definition.contentSha256(),
                protectedResources(definition),
                expiresAt);
        grants.put(fingerprint(token), grant);
        return new IssuedGrant(token, grant);
    }

    public Grant authenticate(String authorization) {
        String token = bearerToken(authorization);
        Grant grant = grants.get(fingerprint(token));
        if (grant == null || !clock.instant().isBefore(grant.expiresAt())) {
            throw unauthenticated();
        }
        Run run = runRepository.findById(grant.runId())
                .orElseThrow(this::unauthenticated);
        if (run.status().isTerminal()
                || !run.workspaceId().equals(grant.workspaceId())
                || !Objects.equals(run.triggeredByUserId(), grant.userId())) {
            grants.remove(fingerprint(token), grant);
            throw unauthenticated();
        }
        LocalClientWorkspaceBinding binding = workspaceRepository.findByWorkspaceId(grant.workspaceId())
                .orElseThrow(this::unauthenticated);
        LocalClientConnectionRoute route = connectionStore.find(grant.binding().clientInstanceId())
                .orElseThrow(this::unauthenticated);
        boolean current = binding.userId().equals(grant.userId())
                && binding.clientInstanceId().equals(grant.binding().clientInstanceId())
                && binding.rootDigest().equals(grant.binding().rootDigest())
                && route.userId().equals(grant.userId())
                && backendRouteResolver.isCurrent(route.backendProcessId())
                && route.connectionGeneration() == grant.connectionGeneration();
        if (!current) {
            grants.remove(fingerprint(token), grant);
            throw unauthenticated();
        }
        return grant;
    }

    public JsonNode invokeLocal(Grant grant, String operation, JsonNode parameters, String traceId) {
        if (!ALLOWED_LOCAL_OPERATIONS.contains(operation)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "受保护 Agent 文件工具操作不允许");
        }
        return fileGateway.invoke(
                grant.binding().clientInstanceId().value(),
                grant.connectionGeneration(),
                grant.workspaceId().value(),
                grant.binding().rootDigest(),
                operation,
                parameters,
                traceId);
    }

    private Map<String, String> protectedResources(ProtectedAgentDefinitionResolver.Definition definition) {
        Map<String, String> resources = new LinkedHashMap<>();
        for (ProtectedAgentDefinitionResolver.SkillDefinition skill : definition.skills()) {
            for (Map.Entry<String, String> file : skill.files().entrySet()) {
                String value = file.getValue();
                if (value != null && value.length() <= MAX_RESOURCE_CHARS) {
                    resources.put("skills/" + skill.technicalId() + "/" + file.getKey(), value);
                }
            }
        }
        return Map.copyOf(resources);
    }

    private void cleanupExpired() {
        Instant now = clock.instant();
        grants.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().expiresAt()));
    }

    private String bearerToken(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw unauthenticated();
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (!token.startsWith("pag_") || token.length() > 128) {
            throw unauthenticated();
        }
        return token;
    }

    private String fingerprint(String token) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private PlatformException unauthenticated() {
        return new PlatformException(ErrorCode.UNAUTHENTICATED, "受保护 Agent 文件授权无效或已过期");
    }

    private static final List<String> ALLOWED_LOCAL_OPERATIONS = List.of(
            "workspace.list",
            "workspace.search",
            "workspace.read",
            "workspace.read.chunk",
            "workspace.read.binary.chunk",
            "workspace.write",
            "workspace.copy",
            "workspace.move",
            "workspace.rename",
            "workspace.status",
            "workspace.delete",
            "workspace.mkdir");

    public record IssuedGrant(String token, Grant grant) {
    }

    public record Grant(
            RunId runId,
            UserId userId,
            WorkspaceId workspaceId,
            LocalClientWorkspaceBinding binding,
            long connectionGeneration,
            String agentRevisionId,
            String agentContentSha256,
            Map<String, String> protectedResources,
            Instant expiresAt) {

        public Grant {
            protectedResources = protectedResources == null ? Map.of() : Map.copyOf(protectedResources);
        }
    }
}
