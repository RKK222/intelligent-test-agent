package com.enterprise.testagent.opencode.runtime.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstance;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceBinding;
import com.enterprise.testagent.domain.localclient.LocalClientWorkspaceRepository;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 本地工作区注册编排；服务端只保存已由客户端 toRealPath 校验过的根目录事实。 */
@Service
public class LocalWorkspaceApplicationService {

    private static final Map<String, Boolean> CAPABILITIES = capabilities();

    private final WorkspaceRepository workspaceRepository;
    private final LocalClientWorkspaceRepository localWorkspaceRepository;
    private final LocalClientInstanceRepository instanceRepository;
    private final LocalClientConnectionStore connectionStore;
    private final LocalClientWorkspaceFileGateway fileGateway;
    private final com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver routeResolver;
    private final ObjectMapper objectMapper;

    public LocalWorkspaceApplicationService(
            WorkspaceRepository workspaceRepository,
            LocalClientWorkspaceRepository localWorkspaceRepository,
            LocalClientInstanceRepository instanceRepository,
            LocalClientConnectionStore connectionStore,
            LocalClientWorkspaceFileGateway fileGateway,
            com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver routeResolver,
            ObjectMapper objectMapper) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository);
        this.localWorkspaceRepository = Objects.requireNonNull(localWorkspaceRepository);
        this.instanceRepository = Objects.requireNonNull(instanceRepository);
        this.connectionStore = Objects.requireNonNull(connectionStore);
        this.fileGateway = Objects.requireNonNull(fileGateway);
        this.routeResolver = Objects.requireNonNull(routeResolver);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    public LocalClientConnectionRoute requireOwnedOnlineRoute(
            UserId userId,
            LocalClientInstanceId clientInstanceId) {
        requireOwnedInstance(userId, clientInstanceId);
        LocalClientConnectionRoute route = connectionStore.find(clientInstanceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "本地客户端离线"));
        if (!route.userId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "本地客户端连接不属于当前用户");
        }
        return route;
    }

    public Optional<LocalClientConnectionRoute> findOwnedWorkspaceOnlineRoute(
            UserId userId,
            WorkspaceId workspaceId) {
        LocalClientWorkspaceBinding binding = requireOwnedBinding(userId, workspaceId);
        return connectionStore.find(binding.clientInstanceId())
                .filter(route -> route.userId().equals(userId));
    }

    @Transactional
    public LocalWorkspaceView create(
            UserId userId,
            LocalClientInstanceId clientInstanceId,
            String name,
            String absoluteRootPath,
            String traceId) {
        // 所有本地校验必须先于客户端根目录注册，避免参数错误留下孤儿根映射。
        String workspaceName = requiredText(name, "name", 255);
        String normalizedInputRoot = requiredText(absoluteRootPath, "rootPath", 4096);
        LocalClientConnectionRoute route = requireOwnedOnlineRoute(userId, clientInstanceId);
        requireCurrentConnection(route);
        JsonNode input = objectMapper.createObjectNode().put("absolutePath", normalizedInputRoot);
        RootRegistration validated = registration(fileGateway.invoke(
                clientInstanceId.value(), route.connectionGeneration(), null, null,
                "workspace.validateRoot", input, traceId));

        // 校验发生在用户桌面，随后用客户端实例行锁串行查重；同一路径重复选择时复用既有 Workspace，
        // 同时重新下发 registerRoot，以修复客户端重装或状态文件丢失后的本地根映射。
        localWorkspaceRepository.lockRegistration(userId, clientInstanceId);
        Optional<LocalClientWorkspaceBinding> existingBinding =
                localWorkspaceRepository.findByOwnerClientAndRootDigest(
                        userId, clientInstanceId, validated.rootDigest());
        if (existingBinding.isPresent()) {
            return restoreExistingWorkspace(
                    existingBinding.orElseThrow(), validated, input, route.connectionGeneration(), traceId);
        }

        WorkspaceId workspaceId = new WorkspaceId(RuntimeIdGenerator.workspaceId());
        RootRegistration registered = registerRoot(
                clientInstanceId, route.connectionGeneration(), workspaceId, input, traceId);
        if (!validated.equals(registered)) {
            bestEffortUnregister(clientInstanceId, route.connectionGeneration(), workspaceId, traceId);
            throw new PlatformException(ErrorCode.CONFLICT, "本地工作区根目录在注册期间发生变化");
        }

        Instant now = Instant.now();
        Workspace workspace = new Workspace(
                workspaceId,
                workspaceName,
                registered.normalizedRootPath(),
                WorkspaceStatus.ACTIVE,
                now,
                now,
                null,
                traceId);
        registerRollbackCompensation(clientInstanceId, route.connectionGeneration(), workspaceId, traceId);
        workspaceRepository.save(workspace);
        localWorkspaceRepository.save(new LocalClientWorkspaceBinding(
                workspaceId,
                userId,
                clientInstanceId,
                registered.normalizedRootPath(),
                registered.rootDigest(),
                registered.fileSystemIdentity(),
                now,
                now));
        return LocalWorkspaceView.from(workspace, clientInstanceId, true);
    }

    private LocalWorkspaceView restoreExistingWorkspace(
            LocalClientWorkspaceBinding binding,
            RootRegistration validated,
            JsonNode input,
            long generation,
            String traceId) {
        Workspace workspace = workspaceRepository.findById(binding.workspaceId())
                .filter(candidate -> candidate.status() == WorkspaceStatus.ACTIVE)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.CONFLICT, "本地工作区绑定存在，但 Workspace 已不可用"));
        RootRegistration registered = registerRoot(
                binding.clientInstanceId(), generation, binding.workspaceId(), input, traceId);
        if (!validated.equals(registered)) {
            bestEffortRestoreRoot(binding, generation, traceId);
            throw new PlatformException(ErrorCode.CONFLICT, "本地工作区根目录在注册期间发生变化");
        }
        if (!binding.normalizedRootPath().equals(registered.normalizedRootPath())
                || !binding.fileSystemIdentity().equals(registered.fileSystemIdentity())) {
            Instant now = Instant.now();
            localWorkspaceRepository.save(new LocalClientWorkspaceBinding(
                    binding.workspaceId(),
                    binding.userId(),
                    binding.clientInstanceId(),
                    registered.normalizedRootPath(),
                    registered.rootDigest(),
                    registered.fileSystemIdentity(),
                    binding.createdAt(),
                    now));
        }
        return LocalWorkspaceView.from(workspace, binding.clientInstanceId(), true);
    }

    private RootRegistration registerRoot(
            LocalClientInstanceId clientInstanceId,
            long generation,
            WorkspaceId workspaceId,
            JsonNode input,
            String traceId) {
        return registration(fileGateway.invoke(
                clientInstanceId.value(), generation, workspaceId.value(), null,
                "workspace.registerRoot", input, traceId));
    }

    private void bestEffortRestoreRoot(
            LocalClientWorkspaceBinding binding,
            long generation,
            String traceId) {
        try {
            JsonNode previousRoot = objectMapper.createObjectNode()
                    .put("absolutePath", binding.normalizedRootPath());
            registerRoot(
                    binding.clientInstanceId(), generation, binding.workspaceId(), previousRoot, traceId);
        } catch (RuntimeException ignored) {
            // 无法恢复旧根时移除客户端映射，使后续文件请求失败关闭而不是继续访问竞态后的目录。
            bestEffortUnregister(
                    binding.clientInstanceId(), generation, binding.workspaceId(), traceId);
        }
    }

    @Transactional
    public LocalWorkspaceDeleted archive(UserId userId, WorkspaceId workspaceId, String traceId) {
        LocalClientWorkspaceBinding binding = requireOwnedBinding(userId, workspaceId);
        Workspace workspace = workspaceRepository.findById(workspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Workspace 不存在"));
        Instant now = Instant.now();
        workspaceRepository.save(new Workspace(
                workspace.workspaceId(),
                workspace.name(),
                workspace.rootPath(),
                WorkspaceStatus.ARCHIVED,
                workspace.createdAt(),
                now,
                workspace.linuxServerId(),
                traceId));
        if (!localWorkspaceRepository.deleteByWorkspaceId(workspaceId)) {
            throw new PlatformException(ErrorCode.CONFLICT, "本地工作区绑定已变化");
        }
        afterCommit(() -> connectionStore.find(binding.clientInstanceId())
                .filter(route -> route.userId().equals(userId))
                .filter(route -> routeResolver.isCurrent(route.backendProcessId()))
                .ifPresent(route -> bestEffortUnregister(
                        binding.clientInstanceId(), route.connectionGeneration(), workspaceId, traceId)));
        return new LocalWorkspaceDeleted(workspaceId.value(), false);
    }

    private LocalClientInstance requireOwnedInstance(UserId userId, LocalClientInstanceId clientInstanceId) {
        LocalClientInstance instance = instanceRepository.findById(clientInstanceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "本地客户端实例不存在"));
        if (!instance.userId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "本地客户端实例不属于当前用户");
        }
        return instance;
    }

    private LocalClientWorkspaceBinding requireOwnedBinding(UserId userId, WorkspaceId workspaceId) {
        LocalClientWorkspaceBinding binding = localWorkspaceRepository.findByWorkspaceId(workspaceId)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "本地工作区不存在"));
        if (!binding.userId().equals(userId)) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "本地工作区不属于当前用户");
        }
        return binding;
    }

    private void requireCurrentConnection(LocalClientConnectionRoute route) {
        if (!routeResolver.isCurrent(route.backendProcessId())) {
            throw new PlatformException(ErrorCode.CONFLICT, "请求必须转发到持有本地客户端连接的 Java");
        }
    }

    private RootRegistration registration(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "本地根目录校验响应无效");
        }
        return new RootRegistration(
                requiredNodeText(node, "normalizedRootPath"),
                requiredNodeText(node, "rootDigest"),
                requiredNodeText(node, "fileSystemIdentity"));
    }

    private void registerRollbackCompensation(
            LocalClientInstanceId clientInstanceId,
            long generation,
            WorkspaceId workspaceId,
            String traceId) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != TransactionSynchronization.STATUS_COMMITTED) {
                    bestEffortUnregister(clientInstanceId, generation, workspaceId, traceId);
                }
            }
        });
    }

    private void afterCommit(Runnable callback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            callback.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                callback.run();
            }
        });
    }

    private void bestEffortUnregister(
            LocalClientInstanceId clientInstanceId,
            long generation,
            WorkspaceId workspaceId,
            String traceId) {
        try {
            fileGateway.invoke(
                    clientInstanceId.value(), generation, workspaceId.value(), null,
                    "workspace.unregister", objectMapper.createObjectNode(), traceId);
        } catch (RuntimeException ignored) {
            // 平台绑定已不存在时，客户端残留映射不再能取得 ticket；后续同目录注册会覆盖或由运维清理。
        }
    }

    private static String requiredNodeText(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "本地根目录校验响应缺少 " + field);
        }
        return value.asText();
    }

    private static String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, field + " 无效");
        }
        return value.trim();
    }

    private static Map<String, Boolean> capabilities() {
        Map<String, Boolean> values = new LinkedHashMap<>();
        values.put("chat", true);
        values.put("fileManagement", true);
        values.put("nightExecution", true);
        values.put("terminal", false);
        values.put("gitPublish", false);
        values.put("agentConfig", false);
        values.put("protectedAgentExecution", true);
        values.put("attachments", false);
        values.put("collaboration", false);
        return Map.copyOf(values);
    }

    private record RootRegistration(
            String normalizedRootPath,
            String rootDigest,
            String fileSystemIdentity) {
    }

    public record LocalWorkspaceView(
            String workspaceId,
            String name,
            String rootPath,
            RuntimeKind runtimeKind,
            String localClientInstanceId,
            boolean online,
            Map<String, Boolean> capabilities) {

        static LocalWorkspaceView from(
                Workspace workspace,
                LocalClientInstanceId clientInstanceId,
                boolean online) {
            return new LocalWorkspaceView(
                    workspace.workspaceId().value(),
                    workspace.name(),
                    workspace.rootPath(),
                    RuntimeKind.LOCAL_CLIENT,
                    clientInstanceId.value(),
                    online,
                    CAPABILITIES);
        }
    }

    public record LocalWorkspaceDeleted(String workspaceId, boolean localDirectoryDeleted) {
    }
}
