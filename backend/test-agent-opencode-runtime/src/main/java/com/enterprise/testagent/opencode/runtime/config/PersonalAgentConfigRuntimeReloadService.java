package com.enterprise.testagent.opencode.runtime.config;

import com.enterprise.testagent.agent.runtime.AgentRuntime;
import com.enterprise.testagent.agent.runtime.AgentRuntimeCommand;
import com.enterprise.testagent.agent.runtime.AgentRuntimeRegistry;
import com.enterprise.testagent.agent.runtime.AgentRuntimeResult;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.PersonalAgentConfigRuntimeReloadResult;
import com.enterprise.testagent.domain.configuration.PersonalAgentConfigRuntimeReloader;
import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import com.enterprise.testagent.domain.node.ExecutionNodeStatus;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcess;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.process.OpencodeProcessConfigLinkService;
import com.enterprise.testagent.opencode.runtime.session.UserRuntimeDisposeCoordinator;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 公共个人 worktree 的当前用户运行态重载实现。
 *
 * <p>服务只允许在 worktree 与用户进程属于同一服务器时执行；先把 Git 外固定指针切到个人 worktree，
 * 再调用 OpenCode 原生 {@code /global/dispose}，不会修改共享公共目录或其它用户进程。
 */
@Service
public class PersonalAgentConfigRuntimeReloadService implements PersonalAgentConfigRuntimeReloader {

    private static final String OPENCODE_AGENT_ID = "opencode";
    private static final Duration RUNTIME_TIMEOUT = Duration.ofSeconds(10);

    private final OpencodeProcessManagementRepository repository;
    private final BackendInstanceIdentity backendIdentity;
    private final OpencodeProcessConfigLinkService configLinkService;
    private final AgentRuntime runtime;
    private UserRuntimeDisposeCoordinator userRuntimeDisposeCoordinator;

    public PersonalAgentConfigRuntimeReloadService(
            OpencodeProcessManagementRepository repository,
            BackendInstanceIdentity backendIdentity,
            OpencodeProcessConfigLinkService configLinkService,
            AgentRuntimeRegistry runtimeRegistry) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.backendIdentity = Objects.requireNonNull(backendIdentity, "backendIdentity must not be null");
        this.configLinkService = Objects.requireNonNull(configLinkService, "configLinkService must not be null");
        this.runtime = Objects.requireNonNull(runtimeRegistry, "runtimeRegistry must not be null")
                .require(AgentRuntimeRegistry.DEFAULT_AGENT_ID);
    }

    /** 公共个人重载与应用 dispose 共用用户级跨 Session 空闲闸门。 */
    @Autowired(required = false)
    void configureUserRuntimeDisposeCoordinator(UserRuntimeDisposeCoordinator coordinator) {
        this.userRuntimeDisposeCoordinator = Objects.requireNonNull(
                coordinator, "coordinator must not be null");
    }

    @Override
    public PersonalAgentConfigRuntimeReloadResult reloadPublicPreview(
            UserId userId,
            String linuxServerId,
            String sourceConfigPath,
            String traceId) {
        Objects.requireNonNull(userId, "userId must not be null");
        String targetServer = requireText(linuxServerId, "公共 Agent worktree 缺少服务器归属");
        if (!targetServer.equals(backendIdentity.linuxServerId())) {
            throw new PlatformException(ErrorCode.CONFLICT, "公共 Agent 个人调试必须在 worktree 所属服务器执行");
        }
        var binding = repository.findUserBinding(userId, OPENCODE_AGENT_ID);
        if (binding.isEmpty()) {
            return new PersonalAgentConfigRuntimeReloadResult(false, "当前用户 TestAgent 进程尚未初始化，请初始化后再次保存以加载个人配置");
        }
        OpencodeServerProcess process = repository.findOpencodeServerProcessById(binding.get().processId())
                .orElseThrow(() -> new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "当前用户 TestAgent 进程不存在"));
        requireOwnedRunningProcess(userId, targetServer, process);
        if (!configLinkService.isManagedConfigPath(process.sessionPath(), process.configPath())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "当前用户 TestAgent 进程仍使用旧版共享配置路径，请通过平台受管方式重启一次后再保存调试");
        }

        Runnable reload = () -> {
            configLinkService.switchTo(sourceConfigPath, process.configPath());
            JsonNode disposed = runtime.runtime(new AgentRuntimeCommand(
                            executionNode(process),
                            "POST",
                            "/global/dispose",
                            null,
                            null,
                            Map.of(),
                            Map.of(),
                            traceId))
                    .map(AgentRuntimeResult::body)
                    .block(RUNTIME_TIMEOUT);
            if (disposed == null || !disposed.isBoolean() || !disposed.booleanValue()) {
                throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "当前用户 TestAgent 运行态重新加载失败");
            }
        };
        if (userRuntimeDisposeCoordinator == null) {
            reload.run();
        } else {
            userRuntimeDisposeCoordinator.withUserIdle(userId, traceId, () -> {
                reload.run();
                return Boolean.TRUE;
            });
        }
        return new PersonalAgentConfigRuntimeReloadResult(true, "已加载公共个人 worktree 配置并重新加载当前用户运行态");
    }

    private void requireOwnedRunningProcess(UserId userId, String linuxServerId, OpencodeServerProcess process) {
        if (!userId.equals(process.userId())) {
            throw new PlatformException(ErrorCode.FORBIDDEN, "无权重载该 TestAgent 进程");
        }
        if (!linuxServerId.equals(process.linuxServerId().value())) {
            throw new PlatformException(
                    ErrorCode.CONFLICT,
                    "当前用户 TestAgent 进程与公共 Agent worktree 不在同一服务器，请在进程所在服务器创建或选择个人 worktree");
        }
        if (process.status() != OpencodeServerProcessStatus.RUNNING) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "当前用户 TestAgent 进程未运行");
        }
    }

    private ExecutionNode executionNode(OpencodeServerProcess process) {
        Instant now = Instant.now();
        return new ExecutionNode(
                new ExecutionNodeId("node_" + process.processId().value()),
                process.baseUrl(),
                ExecutionNodeStatus.READY,
                0,
                1,
                100,
                now,
                java.util.Set.of("opencode", "user-process"),
                now,
                now,
                process.traceId());
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, message);
        }
        return value.trim();
    }
}
