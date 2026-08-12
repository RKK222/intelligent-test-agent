package com.enterprise.testagent.opencode.runtime.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameCodec;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import com.enterprise.testagent.localclient.protocol.LocalClientPayloads;
import com.enterprise.testagent.localclient.protocol.LocalClientProtocol;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/** 服务端向本地客户端发起生命周期、HTTP/SSE 和文件 RPC 的统一隧道网关。 */
@Service
public class LocalClientTunnelGateway {

    private final LocalClientConnectionRegistry connections;
    private final LocalClientPendingRequestRegistry pendingRequests;
    private final LocalClientFrameCodec codec;

    public LocalClientTunnelGateway(
            LocalClientConnectionRegistry connections,
            LocalClientPendingRequestRegistry pendingRequests) {
        this.connections = Objects.requireNonNull(connections, "connections must not be null");
        this.pendingRequests = Objects.requireNonNull(pendingRequests, "pendingRequests must not be null");
        this.codec = new LocalClientFrameCodec();
    }

    public Mono<LocalClientFrame> request(
            LocalClientInstanceId clientInstanceId,
            long generation,
            LocalClientFrameType type,
            Object payload,
            String traceId,
            Duration timeout) {
        return Mono.defer(() -> {
            Outbound outbound = open(clientInstanceId, generation, type, payload, traceId);
            try {
                connections.send(clientInstanceId, generation, outbound.frame());
            } catch (RuntimeException exception) {
                pendingRequests.cancel(outbound.requestId());
                return Mono.error(exception);
            }
            return outbound.exchange().frames()
                    .next()
                    .timeout(requireTimeout(timeout))
                    .switchIfEmpty(Mono.error(new PlatformException(
                            ErrorCode.OPENCODE_BAD_GATEWAY, "本地客户端未返回响应")))
                    .flatMap(this::errorOrFrame)
                    .doFinally(signal -> {
                        if (signal == SignalType.CANCEL || signal == SignalType.ON_ERROR) {
                            cancelRemote(clientInstanceId, generation, outbound.requestId(), traceId);
                        }
                        pendingRequests.cancel(outbound.requestId());
                    });
        });
    }

    public Flux<LocalClientFrame> stream(
            LocalClientInstanceId clientInstanceId,
            long generation,
            LocalClientFrameType type,
            Object payload,
            String traceId,
            Duration idleTimeout) {
        return Flux.defer(() -> {
            Outbound outbound = open(clientInstanceId, generation, type, payload, traceId);
            try {
                connections.send(clientInstanceId, generation, outbound.frame());
            } catch (RuntimeException exception) {
                pendingRequests.cancel(outbound.requestId());
                return Flux.error(exception);
            }
            return outbound.exchange().frames()
                    .timeout(requireTimeout(idleTimeout))
                    .concatMap(frame -> errorOrFrame(frame).flux())
                    .doFinally(signal -> {
                        if (signal == SignalType.CANCEL || signal == SignalType.ON_ERROR) {
                            cancelRemote(clientInstanceId, generation, outbound.requestId(), traceId);
                        }
                        pendingRequests.cancel(outbound.requestId());
                    });
        });
    }

    public boolean accept(LocalClientInstanceId clientInstanceId, LocalClientFrame frame) {
        return pendingRequests.accept(clientInstanceId, frame);
    }

    public void failConnection(LocalClientInstanceId clientInstanceId, long generation, Throwable error) {
        pendingRequests.failConnection(clientInstanceId, generation, error);
    }

    private Outbound open(
            LocalClientInstanceId clientInstanceId,
            long generation,
            LocalClientFrameType type,
            Object payload,
            String traceId) {
        String requestId = "lcr_" + UUID.randomUUID().toString().replace("-", "");
        LocalClientPendingRequestRegistry.PendingExchange exchange = pendingRequests.open(
                requestId, clientInstanceId, generation);
        LocalClientFrame frame = new LocalClientFrame(
                LocalClientProtocol.VERSION,
                type,
                requestId,
                traceId,
                generation,
                codec.payload(payload));
        return new Outbound(requestId, frame, exchange);
    }

    private Mono<LocalClientFrame> errorOrFrame(LocalClientFrame frame) {
        if (frame.type() != LocalClientFrameType.ERROR) {
            return Mono.just(frame);
        }
        LocalClientPayloads.Error error = codec.payload(frame, LocalClientPayloads.Error.class);
        return Mono.error(new PlatformException(
                ErrorCode.OPENCODE_BAD_GATEWAY,
                error.message() == null || error.message().isBlank() ? "本地客户端调用失败" : error.message(),
                error.details() == null ? Map.of("reason", error.code()) : error.details()));
    }

    private void cancelRemote(
            LocalClientInstanceId clientInstanceId,
            long generation,
            String targetRequestId,
            String traceId) {
        try {
            LocalClientFrame cancel = new LocalClientFrame(
                    LocalClientProtocol.VERSION,
                    LocalClientFrameType.CANCEL,
                    "lcc_" + UUID.randomUUID().toString().replace("-", ""),
                    traceId,
                    generation,
                    codec.payload(new LocalClientPayloads.Cancel(targetRequestId, "SERVER_CANCELLED")));
            connections.send(clientInstanceId, generation, cancel);
        } catch (RuntimeException ignored) {
            // 连接已断开时取消消息无法送达，pending 状态仍由本机清理。
        }
    }

    private static Duration requireTimeout(Duration timeout) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
        return timeout;
    }

    private record Outbound(
            String requestId,
            LocalClientFrame frame,
            LocalClientPendingRequestRegistry.PendingExchange exchange) {
    }
}
