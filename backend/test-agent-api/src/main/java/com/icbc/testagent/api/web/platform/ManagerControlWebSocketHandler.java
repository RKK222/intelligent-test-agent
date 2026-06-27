package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.ContainerManagerId;
import com.icbc.testagent.domain.opencodeprocess.OpencodeContainerId;
import com.icbc.testagent.observability.TraceConstants;
import com.icbc.testagent.observability.TraceIdSupport;
import com.icbc.testagent.opencode.runtime.process.socket.BackendJavaProcessLifecycleService;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerConnectionRegistry;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlApplicationService;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlMessage;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlMessageCodec;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlProtocol;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerPendingCommandRegistry;
import com.icbc.testagent.opencode.runtime.process.socket.OpencodeManagerConfigSyncService;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
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
                .doFinally(ignored -> {
                    OpencodeContainerId containerId = containerRef.get();
                    ContainerManagerId managerId = managerRef.get();
                    if (containerId != null) {
                        connections.disconnect(containerId);
                    }
                    if (managerId != null) {
                        controlService.disconnect(managerId, traceId);
                    }
                    outbound.tryEmitComplete();
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
            outbound.tryEmitNext(ManagerControlMessage.error("VALIDATION_ERROR", "manager 协议版本无效", message.traceId()));
            return Mono.empty();
        }
        if (ManagerControlProtocol.TYPE_REGISTER.equals(message.type())) {
            ManagerControlMessage registered = controlService.register(message);
            ContainerManagerId managerId = new ContainerManagerId(message.managerId());
            OpencodeContainerId containerId = new OpencodeContainerId(message.containerId());
            managerRef.set(managerId);
            containerRef.set(containerId);
            connections.register(managerId, containerId, backendLifecycle.backendProcessId(), outboundMessage -> outbound.tryEmitNext(outboundMessage));
            outbound.tryEmitNext(registered);
            // 连接入注册表后，立即把通用参数表中的最大进程数下发给该 manager，使其采用权威值而非 env 兜底。
            configSyncService.pushCurrentMaxTo(containerId);
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
            outbound.tryEmitNext(controlService.backendListResponse(message.traceId()));
            return Mono.empty();
        }
        if (ManagerControlProtocol.TYPE_COMMAND_RESULT.equals(message.type()) || ManagerControlProtocol.TYPE_ERROR.equals(message.type())) {
            if (message.commandId() != null && !message.commandId().isBlank()) {
                pendingCommands.complete(message.commandId(), message);
            }
            return Mono.empty();
        }
        outbound.tryEmitNext(ManagerControlMessage.error("VALIDATION_ERROR", "未知 manager 消息类型", message.traceId()));
        return Mono.empty();
    }

    private Mono<Void> sendErrorAndClose(WebSocketSession session, String code, String message, String traceId) {
        return session.send(Mono.just(session.textMessage(codec.encode(ManagerControlMessage.error(code, message, traceId)))))
                .then(session.close());
    }

    private String traceId(HttpHeaders headers) {
        return TraceIdSupport.resolve(headers.getFirst(TraceConstants.TRACE_ID_HEADER));
    }
}
