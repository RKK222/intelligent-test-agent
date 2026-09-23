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
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceRepository;
import com.enterprise.testagent.domain.managedworkspace.UserWorkspacePreference;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskRepository;
import com.enterprise.testagent.domain.runtime.RuntimeKind;
import com.enterprise.testagent.domain.session.SessionRuntimeTargetRepository;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 本地工作区注册编排；服务端只保存已由客户端 toRealPath 校验过的根目录事实。 */
@Service
public class LocalWorkspaceApplicationService {

    private static final Map<String, Boolean> CAPABILITIES = capabilities();
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalWorkspaceApplicationService.class);

    private final WorkspaceRepository workspaceRepository;
    private final ManagedWorkspaceRepository managedWorkspaceRepository;
    private final LocalClientWorkspaceRepository localWorkspaceRepository;
    private final LocalClientInstanceRepository instanceRepository;
    private final LocalClientConnectionStore connectionStore;
    private final LocalClientWorkspaceFileGateway fileGateway;
    private final SessionRuntimeTargetRepository sessionRuntimeTargetRepository;
    private final NightExecutionTaskRepository nightExecutionTaskRepository;
    private final com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver routeResolver;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate reconnectTransaction;

    public LocalWorkspaceApplicationService(
            WorkspaceRepository workspaceRepository,
            ManagedWorkspaceRepository managedWorkspaceRepository,
            LocalClientWorkspaceRepository localWorkspaceRepository,
            LocalClientInstanceRepository instanceRepository,
            LocalClientConnectionStore connectionStore,
            LocalClientWorkspaceFileGateway fileGateway,
            SessionRuntimeTargetRepository sessionRuntimeTargetRepository,
            NightExecutionTaskRepository nightExecutionTaskRepository,
            com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver routeResolver,
            ObjectMapper objectMapper,
            PlatformTransactionManager transactionManager) {
        this.workspaceRepository = Objects.requireNonNull(workspaceRepository);
        this.managedWorkspaceRepository = Objects.requireNonNull(managedWorkspaceRepository);
        this.localWorkspaceRepository = Objects.requireNonNull(localWorkspaceRepository);
        this.instanceRepository = Objects.requireNonNull(instanceRepository);
        this.connectionStore = Objects.requireNonNull(connectionStore);
        this.fileGateway = Objects.requireNonNull(fileGateway);
        this.sessionRuntimeTargetRepository = Objects.requireNonNull(sessionRuntimeTargetRepository);
        this.nightExecutionTaskRepository = Objects.requireNonNull(nightExecutionTaskRepository);
        this.routeResolver = Objects.requireNonNull(routeResolver);
        this.objectMapper = Objects.requireNonNull(objectMapper);
        this.reconnectTransaction = new TransactionTemplate(Objects.requireNonNull(transactionManager));
        this.reconnectTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 激活并记录当前用户最近选择的本地工作区。
     *
     * <p>客户端进程重启时会丢失内存中的根目录注册；客户端重装又会产生新实例 ID。因此这里必须先让
     * 当前连接重新校验历史绝对路径：同实例恢复根映射；历史实例离线时，只允许唯一在线实例凭完全一致的
     * rootDigest + fileSystemIdentity 接管。校验成功后才保存最近工作区，避免页面先切换到不可读目录。</p>
     */
    public LocalWorkspaceView markRecent(UserId userId, WorkspaceId workspaceId, String traceId) {
        LocalClientConnectionRoute route = requireWorkspaceActivationRoute(userId, workspaceId);
        LocalWorkspaceView activated = activateWorkspace(userId, workspaceId, route, traceId);
        reconnectTransaction.executeWithoutResult(status -> managedWorkspaceRepository.savePreference(
                new UserWorkspacePreference(userId, null, workspaceId, Instant.now())));
        return activated;
    }

    /**
     * 客户端重连后恢复全局最近使用的本地工作区，不要求页面再次触发选择动作。
     *
     * <p>全局最近项也可能是服务器工作区；这种情况直接跳过。恢复仍执行客户端真实路径、摘要和文件系统身份校验，
     * 并用注册帧携带的 generation 限定当前物理连接，不能仅凭历史数据库路径自动接管。</p>
     */
    public Optional<LocalWorkspaceView> restoreRecentOnReconnect(
            UserId userId,
            LocalClientInstanceId clientInstanceId,
            long connectionGeneration,
            String traceId) {
        Optional<UserWorkspacePreference> preference = managedWorkspaceRepository.findGlobalPreference(userId);
        if (preference.isEmpty()) {
            return Optional.empty();
        }
        WorkspaceId workspaceId = preference.orElseThrow().workspaceId();
        Optional<LocalClientWorkspaceBinding> binding = localWorkspaceRepository.findByWorkspaceId(workspaceId)
                .filter(candidate -> candidate.userId().equals(userId));
        if (binding.isEmpty()) {
            return Optional.empty();
        }
        LocalClientConnectionRoute route = requireOwnedOnlineRoute(userId, clientInstanceId);
        if (route.connectionGeneration() != connectionGeneration) {
            throw new PlatformException(ErrorCode.CONFLICT, "本地客户端重连 generation 已失效");
        }
        return Optional.of(activateWorkspace(userId, workspaceId, route, traceId));
    }

    /**
     * 客户端重连后逐个恢复该用户仍存在的本地工作区。
     *
     * <p>旧版本安装可能留下不同的实例 ID，因此不能只恢复全局最近项。每个历史目录仍独立执行真实路径、
     * 摘要和文件系统身份校验；目录已删除或身份变化时仅跳过该项，不能阻塞同一用户的其它有效目录。</p>
     */
    public ReconnectRestoreResult restoreAvailableOnReconnect(
            UserId userId,
            LocalClientInstanceId clientInstanceId,
            long connectionGeneration,
            String traceId) {
        LocalClientConnectionRoute route = requireOwnedOnlineRoute(userId, clientInstanceId);
        if (route.connectionGeneration() != connectionGeneration) {
            throw new PlatformException(ErrorCode.CONFLICT, "本地客户端重连 generation 已失效");
        }
        requireCurrentConnection(route);

        // 最近项优先恢复，随后复用包含已替换实例的完整实例投影收集其余历史绑定。
        Map<WorkspaceId, LocalClientWorkspaceBinding> candidates = new LinkedHashMap<>();
        managedWorkspaceRepository.findGlobalPreference(userId)
                .flatMap(preference -> localWorkspaceRepository.findByWorkspaceId(preference.workspaceId()))
                .filter(binding -> binding.userId().equals(userId))
                .ifPresent(binding -> candidates.put(binding.workspaceId(), binding));
        instanceRepository.findByUserIdIncludingReplaced(userId).stream()
                .map(LocalClientInstance::clientInstanceId)
                .flatMap(instanceId -> localWorkspaceRepository.findByClientInstanceId(instanceId).stream())
                .filter(binding -> binding.userId().equals(userId))
                .sorted((left, right) -> right.updatedAt().compareTo(left.updatedAt()))
                .forEach(binding -> candidates.putIfAbsent(binding.workspaceId(), binding));

        int restored = 0;
        int unavailable = 0;
        for (LocalClientWorkspaceBinding binding : candidates.values()) {
            try {
                LocalClientConnectionRoute currentRoute = requireOwnedOnlineRoute(userId, clientInstanceId);
                if (currentRoute.connectionGeneration() != connectionGeneration) {
                    throw new PlatformException(ErrorCode.CONFLICT, "本地客户端重连 generation 已失效");
                }
                requireCurrentConnection(currentRoute);
                activateWorkspace(userId, binding.workspaceId(), currentRoute, traceId);
                restored++;
            } catch (RuntimeException exception) {
                unavailable++;
                // 单项失败不能在新客户端留下无权访问的临时根映射。
                bestEffortUnregister(
                        clientInstanceId, connectionGeneration, binding.workspaceId(), traceId);
                LOGGER.warn(
                        "event=local_workspace_reconnect_restore_skipped clientInstanceId={} workspaceId={} "
                                + "generation={} errorType={} traceId={}",
                        clientInstanceId.value(),
                        binding.workspaceId().value(),
                        connectionGeneration,
                        exception.getClass().getSimpleName(),
                        traceId);
            }
        }
        return new ReconnectRestoreResult(candidates.size(), restored, unavailable);
    }

    private LocalWorkspaceView activateWorkspace(
            UserId userId,
            WorkspaceId workspaceId,
            LocalClientConnectionRoute route,
            String traceId) {
        LocalClientWorkspaceBinding expectedBinding = requireOwnedBinding(userId, workspaceId);
        workspaceRepository.findById(workspaceId)
                .filter(candidate -> candidate.status() == WorkspaceStatus.ACTIVE)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "本地工作区不可用"));
        requireCurrentConnection(route);

        JsonNode input = objectMapper.createObjectNode()
                .put("absolutePath", expectedBinding.normalizedRootPath());
        // 客户端 RPC 必须全部发生在数据库锁和事务之外；回包完成后再用短事务加锁复核快照。
        RootRegistration validated = registration(fileGateway.invokeRootRegistration(
                route.clientInstanceId().value(),
                route.connectionGeneration(),
                null,
                "workspace.validateRoot",
                input,
                traceId));
        if (!expectedBinding.rootDigest().equals(validated.rootDigest())
                || !expectedBinding.fileSystemIdentity().equals(validated.fileSystemIdentity())) {
            throw new PlatformException(ErrorCode.CONFLICT, "历史本地工作区目录身份已变化，请重新选择目录注册");
        }

        RootRegistration registered = registerRoot(
                route.clientInstanceId(), route.connectionGeneration(), workspaceId, input, traceId);
        if (!validated.equals(registered)) {
            bestEffortUnregister(route.clientInstanceId(), route.connectionGeneration(), workspaceId, traceId);
            throw new PlatformException(ErrorCode.CONFLICT, "本地工作区根目录在恢复期间发生变化");
        }

        return reconnectTransaction.execute(status -> persistActivatedWorkspace(
                userId, expectedBinding, route, registered, traceId));
    }

    /** 加锁后只复核并持久化恢复结果，不再等待任何客户端或网络 RPC。 */
    private LocalWorkspaceView persistActivatedWorkspace(
            UserId userId,
            LocalClientWorkspaceBinding expectedBinding,
            LocalClientConnectionRoute expectedRoute,
            RootRegistration registered,
            String traceId) {
        LocalClientConnectionRoute currentRoute = requireOwnedOnlineRoute(userId, expectedRoute.clientInstanceId());
        if (currentRoute.connectionGeneration() != expectedRoute.connectionGeneration()) {
            throw new PlatformException(ErrorCode.CONFLICT, "本地客户端恢复 generation 已失效");
        }
        requireCurrentConnection(currentRoute);
        localWorkspaceRepository.lockRegistration(userId, currentRoute.clientInstanceId());

        LocalClientWorkspaceBinding currentBinding = requireOwnedBinding(userId, expectedBinding.workspaceId());
        if (!currentBinding.equals(expectedBinding)) {
            throw new PlatformException(ErrorCode.CONFLICT, "本地工作区绑定在恢复期间已变化");
        }
        if (currentBinding.clientInstanceId().equals(currentRoute.clientInstanceId())) {
            return persistRestoredWorkspace(currentBinding, registered);
        }
        if (connectionStore.find(currentBinding.clientInstanceId()).isPresent()) {
            throw new PlatformException(ErrorCode.CONFLICT, "历史本地工作区绑定的客户端已经重新上线");
        }
        return persistReclaimedWorkspace(
                currentBinding,
                currentRoute.clientInstanceId(),
                currentRoute.connectionGeneration(),
                registered,
                traceId);
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

    /**
     * 解析工作区激活应落到的连接：原绑定在线时固定原实例；原绑定离线时只接受该用户唯一在线实例。
     */
    public LocalClientConnectionRoute requireWorkspaceActivationRoute(
            UserId userId,
            WorkspaceId workspaceId) {
        LocalClientWorkspaceBinding binding = requireOwnedBinding(userId, workspaceId);
        Optional<LocalClientConnectionRoute> boundRoute = connectionStore.find(binding.clientInstanceId())
                .filter(route -> route.userId().equals(userId));
        if (boundRoute.isPresent()) {
            return boundRoute.orElseThrow();
        }
        List<LocalClientConnectionRoute> onlineRoutes = instanceRepository.findByUserId(userId).stream()
                .map(LocalClientInstance::clientInstanceId)
                .map(connectionStore::find)
                .flatMap(Optional::stream)
                .filter(route -> route.userId().equals(userId))
                .toList();
        if (onlineRoutes.isEmpty()) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "没有在线本地客户端可打开该工作区");
        }
        if (onlineRoutes.size() > 1) {
            throw new PlatformException(ErrorCode.CONFLICT, "检测到多个在线本地客户端，请等待连接收敛后重试");
        }
        return onlineRoutes.getFirst();
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
        RootRegistration validated = registration(fileGateway.invokeRootRegistration(
                clientInstanceId.value(), route.connectionGeneration(), null,
                "workspace.validateRoot", input, traceId));

        // 校验发生在用户桌面，随后按用户串行查重；同一实例重复选择时恢复根映射，实例 ID 因重装变化时
        // 只有目录摘要与文件系统身份都一致且旧实例离线，才允许保留 workspaceId 完成安全接管。
        localWorkspaceRepository.lockRegistration(userId, clientInstanceId);
        Optional<LocalClientWorkspaceBinding> existingBinding =
                localWorkspaceRepository.findByOwnerClientAndRootDigest(
                        userId, clientInstanceId, validated.rootDigest());
        if (existingBinding.isPresent()) {
            return restoreExistingWorkspace(
                    existingBinding.orElseThrow(), validated, input, route.connectionGeneration(), traceId);
        }
        List<LocalClientWorkspaceBinding> historicalBindings = localWorkspaceRepository
                .findByOwnerRootIdentity(
                        userId, validated.rootDigest(), validated.fileSystemIdentity()).stream()
                .filter(binding -> !binding.clientInstanceId().equals(clientInstanceId))
                .toList();
        if (historicalBindings.size() > 1) {
            throw new PlatformException(ErrorCode.CONFLICT, "同一本地目录存在多个历史客户端绑定，请先归档重复工作区");
        }
        if (historicalBindings.size() == 1) {
            LocalClientWorkspaceBinding historicalBinding = historicalBindings.getFirst();
            if (connectionStore.find(historicalBinding.clientInstanceId()).isPresent()) {
                throw new PlatformException(ErrorCode.CONFLICT, "该本地目录仍由在线旧客户端绑定，不能自动接管");
            }
            return reclaimExistingWorkspace(
                    historicalBinding,
                    clientInstanceId,
                    validated,
                    input,
                    route.connectionGeneration(),
                    traceId);
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

    private LocalWorkspaceView reclaimExistingWorkspace(
            LocalClientWorkspaceBinding historicalBinding,
            LocalClientInstanceId replacementClientInstanceId,
            RootRegistration validated,
            JsonNode input,
            long generation,
            String traceId) {
        RootRegistration registered = registerRoot(
                replacementClientInstanceId,
                generation,
                historicalBinding.workspaceId(),
                input,
                traceId);
        if (!validated.equals(registered)) {
            bestEffortUnregister(
                    replacementClientInstanceId, generation, historicalBinding.workspaceId(), traceId);
            throw new PlatformException(ErrorCode.CONFLICT, "本地工作区根目录在接管期间发生变化");
        }

        return persistReclaimedWorkspace(
                historicalBinding, replacementClientInstanceId, generation, registered, traceId);
    }

    /** 只保存已在事务外或既有调用点完成的根注册结果。 */
    private LocalWorkspaceView persistReclaimedWorkspace(
            LocalClientWorkspaceBinding historicalBinding,
            LocalClientInstanceId replacementClientInstanceId,
            long generation,
            RootRegistration registered,
            String traceId) {
        Workspace workspace = workspaceRepository.findById(historicalBinding.workspaceId())
                .filter(candidate -> candidate.status() == WorkspaceStatus.ACTIVE)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.CONFLICT, "本地工作区历史绑定存在，但 Workspace 已不可用"));

        Instant now = Instant.now();
        LocalClientWorkspaceBinding replacementBinding = new LocalClientWorkspaceBinding(
                historicalBinding.workspaceId(),
                historicalBinding.userId(),
                replacementClientInstanceId,
                registered.normalizedRootPath(),
                registered.rootDigest(),
                registered.fileSystemIdentity(),
                historicalBinding.createdAt(),
                now);
        registerRollbackCompensation(
                replacementClientInstanceId, generation, historicalBinding.workspaceId(), traceId);
        if (!localWorkspaceRepository.rebind(
                replacementBinding, historicalBinding.clientInstanceId())) {
            throw new PlatformException(ErrorCode.CONFLICT, "本地工作区绑定已被其它请求接管");
        }
        sessionRuntimeTargetRepository.rebindLocalClientTargets(
                historicalBinding.workspaceId(),
                historicalBinding.clientInstanceId(),
                replacementClientInstanceId);
        nightExecutionTaskRepository.rebindScheduledLocalClientTargets(
                historicalBinding.workspaceId(),
                historicalBinding.clientInstanceId(),
                replacementClientInstanceId,
                now);
        if (localWorkspaceRepository.findByClientInstanceId(
                historicalBinding.clientInstanceId()).isEmpty()) {
            instanceRepository.markReplaced(
                    historicalBinding.userId(),
                    historicalBinding.clientInstanceId(),
                    replacementClientInstanceId,
                    now);
        }
        return LocalWorkspaceView.from(workspace, replacementClientInstanceId, true);
    }

    private LocalWorkspaceView restoreExistingWorkspace(
            LocalClientWorkspaceBinding binding,
            RootRegistration validated,
            JsonNode input,
            long generation,
            String traceId) {
        RootRegistration registered = registerRoot(
                binding.clientInstanceId(), generation, binding.workspaceId(), input, traceId);
        if (!validated.equals(registered)) {
            bestEffortRestoreRoot(binding, generation, traceId);
            throw new PlatformException(ErrorCode.CONFLICT, "本地工作区根目录在注册期间发生变化");
        }
        return persistRestoredWorkspace(binding, registered);
    }

    /** 根注册完成后只刷新数据库投影，不再从事务内反向调用客户端。 */
    private LocalWorkspaceView persistRestoredWorkspace(
            LocalClientWorkspaceBinding binding,
            RootRegistration registered) {
        Workspace workspace = workspaceRepository.findById(binding.workspaceId())
                .filter(candidate -> candidate.status() == WorkspaceStatus.ACTIVE)
                .orElseThrow(() -> new PlatformException(
                        ErrorCode.CONFLICT, "本地工作区绑定存在，但 Workspace 已不可用"));
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
        return registration(fileGateway.invokeRootRegistration(
                clientInstanceId.value(), generation, workspaceId.value(),
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
        // 本地只开放用户自己的签名公共能力个人副本，不代表开放服务端 Git 公共配置。
        values.put("agentConfig", false);
        values.put("personalAgentConfig", true);
        values.put("protectedAgentExecution", true);
        values.put("attachments", true);
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

    public record ReconnectRestoreResult(int candidates, int restored, int unavailable) {
    }
}
