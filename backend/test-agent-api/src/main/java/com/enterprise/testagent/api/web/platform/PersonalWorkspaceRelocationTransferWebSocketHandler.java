package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.workspace.PersonalWorkspaceRelocationArchiveUpload;
import com.enterprise.testagent.workspace.PersonalWorkspaceRelocationReceiveService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 仅接受 ticket 绑定搬迁归档的二进制帧，禁止通用路径和任意文件 RPC。 */
@Component
public class PersonalWorkspaceRelocationTransferWebSocketHandler implements WebSocketHandler {

    private final PersonalWorkspaceRelocationTransferTicketService ticketService;
    private final PersonalWorkspaceRelocationReceiveService receiveService;
    private final ObjectMapper objectMapper;

    public PersonalWorkspaceRelocationTransferWebSocketHandler(
            PersonalWorkspaceRelocationTransferTicketService ticketService,
            PersonalWorkspaceRelocationReceiveService receiveService,
            ObjectMapper objectMapper) {
        this.ticketService = Objects.requireNonNull(ticketService);
        this.receiveService = Objects.requireNonNull(receiveService);
        this.objectMapper = Objects.requireNonNull(objectMapper);
    }

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        PersonalWorkspaceRelocationTransferTicket ticket;
        try {
            ticket = ticketService.consume(
                    session.getHandshakeInfo().getHeaders().getFirst(
                            PersonalWorkspaceRelocationTransferTicketStore.TICKET_HEADER),
                    session.getHandshakeInfo().getHeaders().getOrigin(),
                    session.getHandshakeInfo().getHeaders().getFirst(
                            PersonalWorkspaceRelocationTransferTicketStore.SOURCE_SERVER_HEADER));
        } catch (RuntimeException exception) {
            return sendFailure(session, exception);
        }
        return Mono.fromCallable(() -> receiveService.begin(
                        ticket.relocationId(),
                        ticket.sourceLinuxServerId(),
                        ticket.targetLinuxServerId(),
                        ticket.snapshotSha256(),
                        ticket.archiveSizeBytes(),
                        ticket.traceId()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(upload -> receiveArchive(session, upload))
                .onErrorResume(exception -> sendFailure(session, exception));
    }

    private Mono<Void> receiveArchive(
            WebSocketSession session, PersonalWorkspaceRelocationArchiveUpload upload) {
        AtomicBoolean completeMessageSeen = new AtomicBoolean();
        AtomicBoolean applied = new AtomicBoolean();
        Mono<PersonalWorkspaceRelocationReceiveService.ReceiveResult> result = session.receive()
                // Netty 的 DataBuffer 不能跨异步线程持有；先在接收线程复制帧，再把磁盘写入放到弹性线程池。
                .concatMap(message -> {
                    IncomingFrame frame = copyFrame(message);
                    return Mono.fromCallable(() -> consumeFrame(upload, frame, completeMessageSeen))
                            .subscribeOn(Schedulers.boundedElastic());
                })
                .takeUntil(Boolean::booleanValue)
                .then(Mono.defer(() -> {
                    if (!completeMessageSeen.get()) {
                        return Mono.error(new PlatformException(
                                ErrorCode.CONFLICT, "个人工作区搬迁连接提前结束"));
                    }
                    return Mono.fromCallable(upload::complete).subscribeOn(Schedulers.boundedElastic());
                }))
                .doOnSuccess(ignored -> applied.set(true))
                .doFinally(ignored -> {
                    if (!applied.get()) {
                        upload.abort();
                    }
                });
        return result.flatMap(received -> send(session,
                PersonalWorkspaceRelocationTransferDtos.TransferResponse.success(
                        received.relocationId(), received.headCommit())));
    }

    private IncomingFrame copyFrame(WebSocketMessage message) {
        if (message.getType() == WebSocketMessage.Type.BINARY) {
            return new IncomingFrame(message.getType(), bytes(message.getPayload()), null);
        }
        if (message.getType() == WebSocketMessage.Type.TEXT) {
            return new IncomingFrame(message.getType(), null, message.getPayloadAsText());
        }
        return new IncomingFrame(message.getType(), null, null);
    }

    private boolean consumeFrame(
            PersonalWorkspaceRelocationArchiveUpload upload,
            IncomingFrame frame,
            AtomicBoolean completeMessageSeen) throws Exception {
        if (frame.type() == WebSocketMessage.Type.BINARY) {
            if (completeMessageSeen.get()) {
                throw new PlatformException(ErrorCode.CONFLICT, "搬迁完成帧之后不能继续上传");
            }
            upload.append(frame.binary());
            return false;
        }
        if (frame.type() == WebSocketMessage.Type.TEXT) {
            JsonNode root = objectMapper.readTree(frame.text());
            if (!"complete".equals(root.path("op").asText())
                    || !completeMessageSeen.compareAndSet(false, true)) {
                throw new PlatformException(ErrorCode.VALIDATION_ERROR, "个人工作区搬迁控制帧无效");
            }
            return true;
        }
        throw new PlatformException(ErrorCode.VALIDATION_ERROR, "个人工作区搬迁帧类型无效");
    }

    private Mono<Void> sendFailure(WebSocketSession session, Throwable throwable) {
        Throwable cause = unwrap(throwable);
        String code = cause instanceof PlatformException platform
                ? platform.errorCode().name()
                : ErrorCode.INTERNAL_ERROR.name();
        String message = cause instanceof PlatformException platform
                ? platform.getMessage()
                : "个人工作区搬迁失败";
        return send(session, PersonalWorkspaceRelocationTransferDtos.TransferResponse.failure(code, message));
    }

    private Mono<Void> send(
            WebSocketSession session, PersonalWorkspaceRelocationTransferDtos.TransferResponse response) {
        try {
            return session.send(Mono.just(session.textMessage(objectMapper.writeValueAsString(response))));
        } catch (Exception exception) {
            return session.close();
        }
    }

    private byte[] bytes(DataBuffer buffer) {
        byte[] bytes = new byte[buffer.readableByteCount()];
        buffer.read(bytes);
        return bytes;
    }

    private record IncomingFrame(WebSocketMessage.Type type, byte[] binary, String text) {
    }

    private Throwable unwrap(Throwable throwable) {
        Throwable current = throwable;
        while ((current instanceof java.util.concurrent.CompletionException
                || current instanceof java.util.concurrent.ExecutionException)
                && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}
