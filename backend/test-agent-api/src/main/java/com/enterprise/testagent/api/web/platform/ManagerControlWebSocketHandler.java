package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.opencodeprocess.ContainerManagerId;
import com.enterprise.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.enterprise.testagent.observability.TraceConstants;
import com.enterprise.testagent.observability.TraceIdSupport;
import com.enterprise.testagent.opencode.runtime.process.socket.BackendJavaProcessLifecycleService;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerConnectionRegistry;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlApplicationService;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlMessage;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlMessageCodec;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlProtocol;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import com.enterprise.testagent.opencode.runtime.process.socket.ManagerPendingCommandRegistry;
import com.enterprise.testagent.opencode.runtime.process.socket.OpencodeManagerConfigSyncService;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/**
 * opencode-manager 控制面 WebSocket 入口，负责鉴权、注册、心跳和命令响应分发。
 */
@Component
public class ManagerControlWebSocketHandler implements WebSocketHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ManagerControlWebSocketHandler.class);

    private final ManagerControlSettings settings;
    private final ManagerControlMessageCodec codec;
    private final ManagerControlApplicationService controlService;
    private final BackendJavaProcessLifecycleService backendLifecycle;
    private final ManagerConnectionRegistry connections;
    private final ManagerPendingCommandRegistry pendingCommands;
    private final OpencodeManagerConfigSyncService configSyncService;

    /**
     * 注入控制面依赖，业务写库和命令等待均委托 runtime 模块。
     */
    public ManagerControlWebSocketHandler(
            ManagerControlSettings settings,
            ManagerControlMessageCodec codec,
            ManagerControlApplicationService controlService,
            BackendJavaProcessLifecycleService backendLifecycle,
            ManagerConnectionRegistry connections,
            ManagerPendingCommandRegistry pendingCommands,
            OpencodeManagerConfigSyncService configSyncService) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.controlService = Objects.requireNonNull(controlService, "controlService must not be null");
        this.backendLifecycle = Objects.requireNonNull(backendLifecycle, "backendLifecycle must not be null");
        this.connections = Objects.requireNonNull(connections, "connections must not be null");
        this.pendingCommands = Objects.requireNonNull(pendingCommands, "pendingCommands must not be null");
        this.configSyncService = Objects.requireNonNull(configSyncService, "configSyncService must not be null");
    }

    /**
     * 处理 manager WebSocket 生命周期，只有独立 manager token 通过后才接受消息。
     */
    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String traceId = traceId(session.getHandshakeInfo().getHeaders());
        if (!settings.tokenMatches(session.getHandshakeInfo().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))) {
            return sendErrorAndClose(session, ErrorCode.UNAUTHENTICATED.name(), "manager token 无效", traceId);
        }
        Sinks.Many<ManagerControlMessage> outbound = Sinks.many().unicast().onBackpressureBuffer();
        AtomicReference<ContainerManagerId> managerRef = new AtomicReference<>();
        AtomicReference<OpencodeContainerId> containerRef = new AtomicReference<>();

        Mono<Void> inbound = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .map(codec::decode)
                .concatMap(message -> handleMessage(outbound, managerRef, containerRef, message))
                .doOnError(exception -> LOGGER.warn(
                        "manager WebSocket 入站处理失败 managerId={} containerId={} traceId={}",
                        managerRef.get(),
                        containerRef.get(),
                        traceId,
                        exception))
                .doFinally(ignored -> {
                    OpencodeContainerId containerId = containerRef.get();
                    ContainerManagerId managerId = managerRef.get();
                    if (containerId != null) {
                        connections.disconnect(containerId);
                    }
                    if (managerId != null) {
                        controlService.disconnect(managerId, traceId);
                    }
                    completeOutbound(outbound);
                })
                .then();
        Mono<Void> outboundMono = session.send(outbound.asFlux()
                .map(codec::encode)
                .map(session::textMessage));
        return Mono.when(inbound, outboundMono);
    }

    private Mono<Void> handleMessage(
            Sinks.Many<ManagerControlMessage> outbound,
            AtomicReference<ContainerManagerId> managerRef,
            AtomicReference<OpencodeContainerId> containerRef,
            ManagerControlMessage message) {
        if (!ManagerControlProtocol.VERSION.equals(message.protocolVersion())) {
            emitOutbound(outbound, ManagerControlMessage.error("VALIDATION_ERROR", "manager 协议版本无效", message.traceId()));
            return Mono.empty();
        }
        if (ManagerControlProtocol.TYPE_REGISTER.equals(message.type())) {
            ManagerControlMessage registered = controlService.register(message);
            ContainerManagerId managerId = new ContainerManagerId(message.managerId());
            OpencodeContainerId containerId = new OpencodeContainerId(message.containerId());
            managerRef.set(managerId);
            containerRef.set(containerId);
            connections.register(
                    managerId,
                    containerId,
                    backendLifecycle.backendProcessId(),
                    outboundMessage -> emitOutbound(outbound, outboundMessage));
            emitOutbound(outbound, registered);
            return Mono.empty();
        }
        if (ManagerControlProtocol.TYPE_HEARTBEAT.equals(message.type())) {
            controlService.heartbeat(message);
            return Mono.empty();
        }
        if (ManagerControlProtocol.TYPE_MANAGER_HEARTBEAT.equals(message.type())) {
            controlService.managerHeartbeat(message);
            return Mono.empty();
        }
        if (ManagerControlProtocol.TYPE_BACKEND_LIST_REQUEST.equals(message.type())) {
            LOGGER.debug("忽略 manager backendListRequest：manager 已收敛为只连接本服务器 Java traceId={}", message.traceId());
            return Mono.empty();
        }
        if (ManagerControlProtocol.TYPE_CONFIG_REQUEST.equals(message.type())) {
            emitOutbound(outbound, configSyncService.configUpdateMessage(message.traceId())
                    .orElseGet(() -> ManagerControlMessage.error(
                            "OPENCODE_UNAVAILABLE", "manager 运行公共参数未配置", message.traceId())));
            return Mono.empty();
        }
        if (ManagerControlProtocol.TYPE_COMMAND_RESULT.equals(message.type()) || ManagerControlProtocol.TYPE_ERROR.equals(message.type())) {
            if (message.commandId() != null && !message.commandId().isBlank()) {
                pendingCommands.complete(message.commandId(), message);
            }
            return Mono.empty();
        }
        emitOutbound(outbound, ManagerControlMessage.error("VALIDATION_ERROR", "未知 manager 消息类型", message.traceId()));
        return Mono.empty();
    }

    /**
     * 同一 manager 连接可能同时收到多个 bounded-elastic 线程下发的控制命令。
     * Reactor unicast sink 不允许并发 emission，因此必须在连接级锁内发送并检查结果，
     * 避免 FAIL_NON_SERIALIZED 被忽略后让调用方一直等待 command timeout。
     */
    private void emitOutbound(Sinks.Many<ManagerControlMessage> outbound, ManagerControlMessage message) {
        synchronized (outbound) {
            Sinks.EmitResult result = outbound.tryEmitNext(message);
            if (result == Sinks.EmitResult.OK) {
                return;
            }
            LOGGER.warn(
                    "manager_outbound_emit_failed traceId={} type={} result={}",
                    message.traceId(),
                    message.type(),
                    result);
            throw new PlatformException(ErrorCode.OPENCODE_BAD_GATEWAY, "TestAgent 管理进程控制消息发送失败");
        }
    }

    /** 连接结束与命令发送复用同一把锁，避免 complete 和 next 竞争。 */
    private void completeOutbound(Sinks.Many<ManagerControlMessage> outbound) {
        synchronized (outbound) {
            Sinks.EmitResult result = outbound.tryEmitComplete();
            if (result != Sinks.EmitResult.OK && result != Sinks.EmitResult.FAIL_TERMINATED) {
                LOGGER.debug("manager_outbound_complete_failed result={}", result);
            }
        }
    }

    private Mono<Void> sendErrorAndClose(WebSocketSession session, String code, String message, String traceId) {
        return session.send(Mono.just(session.textMessage(codec.encode(ManagerControlMessage.error(code, message, traceId)))))
                .then(session.close());
    }

    private String traceId(HttpHeaders headers) {
        return TraceIdSupport.resolve(headers.getFirst(TraceConstants.TRACE_ID_HEADER));
    }
}
