package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 协调节点的一次性 shard WebSocket 接收器，只允许二进制内容和最终摘要控制帧。 */
@Component
public class TeamWorkspaceExportShardWebSocketHandler implements WebSocketHandler {

    private final TeamWorkspaceExportShardTicketStore tickets;
    private final ObjectMapper objectMapper;

    public TeamWorkspaceExportShardWebSocketHandler(
            TeamWorkspaceExportShardTicketStore tickets,
            ObjectMapper objectMapper) {
        this.tickets = Objects.requireNonNull(tickets);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        TeamWorkspaceExportShardTicket ticket;
        try {
            ticket = tickets.consume(
                    session.getHandshakeInfo().getHeaders().getFirst(
                            TeamWorkspaceExportShardTicketStore.TICKET_HEADER),
                    session.getHandshakeInfo().getHeaders().getOrigin(),
                    session.getHandshakeInfo().getHeaders().getFirst(
                            TeamWorkspaceExportShardTicketStore.SOURCE_SERVER_HEADER));
        } catch (RuntimeException exception) {
            return sendFailure(session, exception);
        }
        return Mono.fromCallable(() -> new Upload(ticket))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(upload -> receive(session, upload))
                .onErrorResume(exception -> sendFailure(session, exception));
    }

    private Mono<Void> receive(WebSocketSession session, Upload upload) {
        AtomicBoolean complete = new AtomicBoolean();
        AtomicBoolean succeeded = new AtomicBoolean();
        return session.receive()
                .concatMap(message -> {
                    Frame frame = copy(message);
                    return Mono.fromCallable(() -> consume(upload, frame, complete))
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .takeUntil(Boolean::booleanValue)
                .then(Mono.fromCallable(() -> {
                    if (!complete.get()) throw new PlatformException(ErrorCode.CONFLICT, "shard 连接提前结束");
                    upload.complete();
                    succeeded.set(true);
                    return true;
                }).subscribeOn(Schedulers.boundedElastic()))
                .flatMap(ignored -> send(session, TeamWorkspaceExportShardDtos.ReceiveResponse.accepted()))
                .doFinally(ignored -> {
                    if (!succeeded.get()) upload.abort();
                });
    }

    private Frame copy(WebSocketMessage message) {
        if (message.getType() == WebSocketMessage.Type.BINARY) {
            DataBuffer buffer = message.getPayload();
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return new Frame(message.getType(), bytes, null);
        }
        if (message.getType() == WebSocketMessage.Type.TEXT) {
            return new Frame(message.getType(), null, message.getPayloadAsText());
        }
        return new Frame(message.getType(), null, null);
    }

    private boolean consume(Upload upload, Frame frame, AtomicBoolean complete) throws Exception {
        if (frame.type() == WebSocketMessage.Type.BINARY) {
            if (complete.get()) throw new PlatformException(ErrorCode.CONFLICT, "完成帧后不能继续上传 shard");
            upload.append(frame.binary());
            return false;
        }
        if (frame.type() == WebSocketMessage.Type.TEXT) {
            JsonNode value = objectMapper.readTree(frame.text());
            if (!"complete".equals(value.path("op").asText()) || !complete.compareAndSet(false, true)) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "shard 完成控制帧无效");
            }
            upload.expected(value.path("sha256").asText(), value.path("archiveBytes").asLong(-1));
            return true;
        }
        throw new PlatformException(ErrorCode.VALIDATION_ERROR, "shard 帧类型无效");
    }

    private Mono<Void> sendFailure(WebSocketSession session, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        String code = cause instanceof PlatformException platform
                ? platform.errorCode().name() : ErrorCode.INTERNAL_ERROR.name();
        String message = cause instanceof PlatformException platform
                ? platform.getMessage() : "协调节点接收 shard 失败";
        return send(session, TeamWorkspaceExportShardDtos.ReceiveResponse.failure(code, message));
    }

    private Mono<Void> send(WebSocketSession session, TeamWorkspaceExportShardDtos.ReceiveResponse response) {
        try {
            return session.send(Mono.just(session.textMessage(objectMapper.writeValueAsString(response))));
        } catch (Exception exception) {
            return session.close();
        }
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) current = current.getCause();
        return current;
    }

    private record Frame(WebSocketMessage.Type type, byte[] binary, String text) {
    }

    /** 单连接顺序写文件并同时计算 SHA-256；失败时只删除当前 item 临时文件。 */
    private static final class Upload {
        private final TeamWorkspaceExportShardTicket ticket;
        private final MessageDigest digest;
        private final OutputStream output;
        private long bytes;
        private String expectedSha256;
        private long expectedBytes = -1;

        private Upload(TeamWorkspaceExportShardTicket ticket) throws Exception {
            this.ticket = ticket;
            Files.createDirectories(ticket.target().getParent());
            this.digest = MessageDigest.getInstance("SHA-256");
            this.output = new BufferedOutputStream(Files.newOutputStream(ticket.target()));
        }

        private void append(byte[] chunk) throws Exception {
            bytes += chunk.length;
            if (bytes > ticket.maxArchiveBytes()) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "shard 压缩包超过接收上限");
            }
            output.write(chunk);
            digest.update(chunk);
        }

        private void expected(String sha256, long archiveBytes) {
            this.expectedSha256 = sha256;
            this.expectedBytes = archiveBytes;
        }

        private void complete() throws Exception {
            output.close();
            String actual = HexFormat.of().formatHex(digest.digest());
            if (expectedBytes < 0 || expectedBytes != bytes || expectedSha256 == null
                    || !actual.equals(expectedSha256)) {
                Files.deleteIfExists(ticket.target());
                throw new PlatformException(ErrorCode.CONFLICT, "shard 大小或摘要校验失败");
            }
        }

        private void abort() {
            try {
                output.close();
            } catch (Exception ignored) {
            }
            try {
                Files.deleteIfExists(ticket.target());
            } catch (Exception ignored) {
            }
        }
    }
}
