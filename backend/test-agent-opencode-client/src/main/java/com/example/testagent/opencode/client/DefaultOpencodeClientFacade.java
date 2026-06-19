package com.example.testagent.opencode.client;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.domain.event.RunEventDraft;
import com.example.testagent.domain.node.ExecutionNode;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

/**
 * 默认 opencode facade，集中处理超时、有限重试、错误映射和事件转换。
 */
@Service
public class DefaultOpencodeClientFacade implements OpencodeClientFacade {

    private final OpencodeSdkGateway gateway;
    private final OpencodeRunEventMapper eventMapper;
    private final Duration timeout;
    private final int maxRetries;
    private final Duration retryBackoff;

    @Autowired
    public DefaultOpencodeClientFacade(OpencodeSdkGateway gateway, OpencodeRunEventMapper eventMapper) {
        this(gateway, eventMapper, Duration.ofSeconds(30), 1, Duration.ofMillis(200));
    }

    public DefaultOpencodeClientFacade(
            OpencodeSdkGateway gateway,
            OpencodeRunEventMapper eventMapper,
            Duration timeout,
            int maxRetries,
            Duration retryBackoff) {
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.eventMapper = Objects.requireNonNull(eventMapper, "eventMapper must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        this.maxRetries = Math.max(0, maxRetries);
        this.retryBackoff = Objects.requireNonNull(retryBackoff, "retryBackoff must not be null");
    }

    @Override
    public Mono<OpencodeHealthResult> health(OpencodeHealthCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return applyPolicy(
                Mono.defer(() -> gateway.health(command.node(), command.traceId())),
                "health",
                command.node());
    }

    @Override
    public Mono<OpencodeCreateSessionResult> createSession(OpencodeCreateSessionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return applyPolicy(
                Mono.defer(() -> gateway.createSession(
                        command.node(),
                        command.directory(),
                        command.workspace(),
                        command.title(),
                        command.traceId())),
                "createSession",
                command.node());
    }

    @Override
    public Mono<OpencodeCancelResult> cancelSession(OpencodeCancelCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return applyPolicy(
                Mono.defer(() -> gateway.cancelSession(
                        command.node(),
                        command.opencodeSessionId(),
                        command.directory(),
                        command.workspace(),
                        command.traceId())),
                "cancelSession",
                command.node());
    }

    @Override
    public Mono<OpencodeStartRunResult> startRun(OpencodeStartRunCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return applyPolicy(
                Mono.defer(() -> gateway.startRun(
                        command.node(),
                        command.opencodeSessionId(),
                        command.directory(),
                        command.workspace(),
                        command.prompt(),
                        command.parts(),
                        command.messageId(),
                        command.agent(),
                        command.modelProviderId(),
                        command.modelId(),
                        command.variant(),
                        command.traceId())),
                "startRun",
                command.node());
    }

    @Override
    public Flux<RunEventDraft> streamRunEvents(OpencodeStreamEventsCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return applyPolicy(
                        Flux.defer(() -> gateway.streamEvents(
                                command.node(),
                                command.directory(),
                                command.workspace(),
                                command.traceId())),
                        "streamRunEvents",
                        command.node())
                .map(rawEvent -> eventMapper.toDraft(rawEvent, command.runId(), command.traceId()));
    }

    @Override
    public Mono<OpencodeDiffResult> getDiff(OpencodeDiffCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return applyPolicy(
                Mono.defer(() -> gateway.getDiff(
                        command.node(),
                        command.opencodeSessionId(),
                        command.directory(),
                        command.workspace(),
                        command.messageId(),
                        command.traceId())),
                "getDiff",
                command.node());
    }

    @Override
    public Mono<OpencodeRejectDiffResult> rejectDiff(OpencodeRejectDiffCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return applyPolicy(
                Mono.defer(() -> gateway.rejectDiff(
                        command.node(),
                        command.opencodeSessionId(),
                        command.directory(),
                        command.workspace(),
                        command.messageId(),
                        command.partId(),
                        command.traceId())),
                "rejectDiff",
                command.node());
    }

    @Override
    public Mono<OpencodeRuntimeResult> runtime(OpencodeRuntimeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return applyPolicy(
                Mono.defer(() -> gateway.runtime(
                        command.node(),
                        command.method(),
                        command.path(),
                        command.directory(),
                        command.workspace(),
                        command.query(),
                        command.body(),
                        command.traceId())),
                "runtime",
                command.node());
    }

    @Override
    public Mono<OpencodeSessionMessagesResult> sessionMessages(OpencodeSessionMessagesCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return applyPolicy(
                Mono.defer(() -> gateway.sessionMessages(
                        command.node(),
                        command.opencodeSessionId(),
                        command.limit(),
                        command.order(),
                        command.cursor(),
                        command.traceId())),
                "sessionMessages",
                command.node());
    }

    private <T> Mono<T> applyPolicy(Mono<T> source, String operation, ExecutionNode node) {
        Mono<T> protectedSource = source.timeout(timeout);
        if (maxRetries > 0) {
            protectedSource = protectedSource.retryWhen(retrySpec());
        }
        return protectedSource.onErrorMap(error -> toPlatformException(error, operation, node));
    }

    private <T> Flux<T> applyPolicy(Flux<T> source, String operation, ExecutionNode node) {
        Flux<T> protectedSource = source.timeout(timeout);
        if (maxRetries > 0) {
            protectedSource = protectedSource.retryWhen(retrySpec());
        }
        return protectedSource.onErrorMap(error -> toPlatformException(error, operation, node));
    }

    private Retry retrySpec() {
        return Retry.fixedDelay(maxRetries, retryBackoff)
                .filter(this::isRetryable);
    }

    private boolean isRetryable(Throwable error) {
        Throwable current = unwrapRetry(error);
        if (current instanceof WebClientResponseException exception) {
            int status = exception.getStatusCode().value();
            return status == 502 || status == 503 || (status >= 500 && status < 600);
        }
        return hasCause(current, ConnectException.class);
    }

    private PlatformException toPlatformException(Throwable error, String operation, ExecutionNode node) {
        Throwable current = unwrapRetry(error);
        if (current instanceof PlatformException exception) {
            return exception;
        }
        if (current instanceof TimeoutException) {
            return platformException(ErrorCode.OPENCODE_TIMEOUT, operation, node, null, current);
        }
        if (current instanceof WebClientResponseException exception) {
            int status = exception.getStatusCode().value();
            if (status == 408 || status == 504) {
                return platformException(ErrorCode.OPENCODE_TIMEOUT, operation, node, status, current);
            }
            if (status == 503) {
                return platformException(ErrorCode.OPENCODE_UNAVAILABLE, operation, node, status, current);
            }
            return platformException(ErrorCode.OPENCODE_BAD_GATEWAY, operation, node, status, current);
        }
        if (hasCause(current, ConnectException.class)) {
            return platformException(ErrorCode.OPENCODE_UNAVAILABLE, operation, node, null, current);
        }
        return platformException(ErrorCode.OPENCODE_BAD_GATEWAY, operation, node, null, current);
    }

    private PlatformException platformException(
            ErrorCode errorCode,
            String operation,
            ExecutionNode node,
            Integer status,
            Throwable cause) {
        Map<String, Object> details = status == null
                ? Map.of("operation", operation, "nodeId", node.executionNodeId().value(), "baseUrl", node.baseUrl())
                : Map.of(
                        "operation", operation,
                        "nodeId", node.executionNodeId().value(),
                        "baseUrl", node.baseUrl(),
                        "status", status);
        return new PlatformException(errorCode, errorCode.defaultMessage(), details, cause);
    }

    private Throwable unwrapRetry(Throwable error) {
        if (Exceptions.isRetryExhausted(error) && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    private boolean hasCause(Throwable error, Class<? extends Throwable> causeType) {
        Throwable current = error;
        while (current != null) {
            if (causeType.isInstance(current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
