package com.icbc.testagent.opencode.client;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.event.RunEventDraft;
import com.icbc.testagent.domain.event.RunEventScopeContext;
import com.icbc.testagent.domain.node.ExecutionNode;
import java.net.ConnectException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultOpencodeClientFacade.class);
    private static final Duration LONG_RUNNING_COMMAND_TIMEOUT = Duration.ofHours(24);

    private final OpencodeSdkGateway gateway;
    private final OpencodeRunEventMapper eventMapper;
    private final Duration timeout;
    private final int maxRetries;
    private final Duration retryBackoff;

    /**
     * 使用线上默认超时和一次有限重试创建 facade。
     */
    @Autowired
    public DefaultOpencodeClientFacade(OpencodeSdkGateway gateway, OpencodeRunEventMapper eventMapper) {
        this(gateway, eventMapper, Duration.ofSeconds(30), 1, Duration.ofMillis(200));
    }

    /**
     * 创建可配置策略的 facade，测试可注入更短超时和禁用重试。
     */
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

    /**
     * 检查指定执行节点的 opencode 健康状态，并套用统一超时、重试和错误转换策略。
     */
    @Override
    public Mono<OpencodeHealthResult> health(OpencodeHealthCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return applyPolicy(
                Mono.defer(() -> gateway.health(command.node(), command.traceId())),
                "health",
                command.node());
    }

    /**
     * 创建远端 opencode session，只返回平台稳定的 session id 投影。
     */
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

    /**
     * 校验远端 opencode session 是否仍存在；历史绑定跨端口失效时 404 只表示需要重建绑定。
     */
    @Override
    public Mono<Boolean> sessionExists(OpencodeSessionExistsCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return applyPolicy(
                Mono.defer(() -> gateway.sessionExists(
                                command.node(),
                                command.opencodeSessionId(),
                                command.traceId()))
                        .onErrorResume(
                                this::isNotFoundResponse,
                                ignored -> Mono.just(false)),
                "sessionExists",
                command.node());
    }

    /**
     * 取消远端 opencode session，并把远端响应归一为平台取消结果。
     */
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

    /**
     * 通过 opencode prompt_async 启动 Run，generated 请求体只在 gateway 内构造。
     */
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
                        command.system(),
                        command.modelProviderId(),
                        command.modelId(),
                        command.variant(),
                        command.traceId())),
                "startRun",
                command.node());
    }

    /**
     * 技能命令可能持续数分钟，不能套用普通 30 秒超时，也不能重试造成同一命令重复执行。
     * 后端 Run 取消会调用 session abort，让该请求自然结束；24 小时硬上限避免异常连接永久占用资源。
     */
    @Override
    public Mono<OpencodeStartRunResult> startCommand(OpencodeStartCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String nodeId = command.node().executionNodeId().value();
        LOGGER.debug("Opencode call started, operation=startCommand, nodeId={}, baseUrl={}",
                nodeId, command.node().baseUrl());
        return Mono.defer(() -> gateway.startCommand(
                        command.node(),
                        command.opencodeSessionId(),
                        command.directory(),
                        command.workspace(),
                        command.command(),
                        command.arguments(),
                        command.parts(),
                        command.messageId(),
                        command.agent(),
                        command.modelProviderId(),
                        command.modelId(),
                        command.variant(),
                        command.traceId()))
                .timeout(LONG_RUNNING_COMMAND_TIMEOUT)
                .doOnSuccess(result -> LOGGER.debug(
                        "Opencode call completed, operation=startCommand, nodeId={}", nodeId))
                .onErrorMap(error -> toPlatformException(error, "startCommand", command.node()));
    }

    /**
     * 订阅 opencode raw event 流，并在 facade 层转换为平台 RunEventDraft。
     */
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
                .flatMapIterable(rawEvent -> eventMapper.toDrafts(
                        rawEvent,
                        command.runId(),
                        command.traceId(),
                        RunEventScopeContext.root(command.runId(), command.opencodeSessionId())));
    }

    /**
     * 为旁路问答打开可观测握手流；旧 streamRunEvents 保持原重试链路，不改变主 Run 行为。
     */
    @Override
    public OpencodeRunEventStream openRunEventStream(OpencodeStreamEventsCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        OpencodeEventStream opened = gateway.openEventStream(
                command.node(),
                command.directory(),
                command.workspace(),
                command.traceId());
        Mono<Void> ready = applyObservableStreamReady(
                opened.ready(), "openRunEventStreamReady", command.node());
        Flux<RunEventDraft> events = applyObservableStreamEvents(
                        opened.events(), "openRunEventStream", command.node())
                .flatMapIterable(rawEvent -> eventMapper.toDrafts(
                        rawEvent,
                        command.runId(),
                        command.traceId(),
                        RunEventScopeContext.root(command.runId(), command.opencodeSessionId())));
        return new OpencodeRunEventStream(ready, events);
    }

    /**
     * 可观测 SSE 的握手只允许一次连接：保留响应头超时和统一错误映射，但禁止重试旧 response/body。
     */
    private Mono<Void> applyObservableStreamReady(
            Mono<Void> source, String operation, ExecutionNode node) {
        String nodeId = node.executionNodeId().value();
        LOGGER.debug("Opencode stream handshake started, operation={}, nodeId={}, baseUrl={}",
                operation, nodeId, node.baseUrl());
        return source.timeout(timeout)
                .doOnSuccess(ignored -> LOGGER.debug(
                        "Opencode stream handshake completed, operation={}, nodeId={}", operation, nodeId))
                .onErrorMap(error -> toPlatformException(error, operation, node));
    }

    /**
     * 已打开 response 的 body 不能重订阅，也不使用逐事件 idle timeout；绝对时限由上层任务控制。
     */
    private <T> Flux<T> applyObservableStreamEvents(
            Flux<T> source, String operation, ExecutionNode node) {
        String nodeId = node.executionNodeId().value();
        LOGGER.debug("Opencode observable stream started, operation={}, nodeId={}, baseUrl={}",
                operation, nodeId, node.baseUrl());
        return source
                .doOnNext(event -> LOGGER.trace(
                        "Opencode observable stream event, operation={}, nodeId={}", operation, nodeId))
                .onErrorMap(error -> toPlatformException(error, operation, node));
    }

    /**
     * 查询远端 session Diff，并保持返回值为平台稳定 Diff DTO。
     */
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

    /**
     * 拒绝远端 Diff，映射到 opencode session revert 能力。
     */
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

    /**
     * 受控转发 opencode Web App runtime API，返回稳定 JsonNode projection。
     */
    @Override
    public Mono<OpencodeRuntimeResult> runtime(OpencodeRuntimeCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        String path = command.path() != null ? command.path() : "";
        Duration customTimeout = null;
        if (path.endsWith("/command") || path.endsWith("/shell")) {
            customTimeout = Duration.ofSeconds(120);
        }
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
                command.node(),
                customTimeout);
    }

    /**
     * 读取远端 projected messages，用于断线或刷新后的消息投影恢复。
     */
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

    /**
     * 为 Mono 外部调用统一增加超时、有限重试和平台错误码映射。
     */
    private <T> Mono<T> applyPolicy(Mono<T> source, String operation, ExecutionNode node) {
        return applyPolicy(source, operation, node, null);
    }

    private <T> Mono<T> applyPolicy(Mono<T> source, String operation, ExecutionNode node, Duration customTimeout) {
        String nodeId = node.executionNodeId().value();
        LOGGER.debug("Opencode call started, operation={}, nodeId={}, baseUrl={}", operation, nodeId, node.baseUrl());
        Mono<T> protectedSource = source.timeout(customTimeout != null ? customTimeout : timeout);
        if (maxRetries > 0) {
            protectedSource = protectedSource.retryWhen(retrySpec(operation, nodeId));
        }
        return protectedSource
                .doOnSuccess(result -> LOGGER.debug("Opencode call completed, operation={}, nodeId={}", operation, nodeId))
                .onErrorMap(error -> toPlatformException(error, operation, node));
    }

    /**
     * 为 Flux 外部调用统一增加超时、有限重试和平台错误码映射。
     */
    private <T> Flux<T> applyPolicy(Flux<T> source, String operation, ExecutionNode node) {
        String nodeId = node.executionNodeId().value();
        LOGGER.debug("Opencode stream started, operation={}, nodeId={}, baseUrl={}", operation, nodeId, node.baseUrl());
        Flux<T> protectedSource = source.timeout(timeout);
        if (maxRetries > 0) {
            protectedSource = protectedSource.retryWhen(retrySpec(operation, nodeId));
        }
        return protectedSource
                .doOnNext(event -> LOGGER.trace("Opencode stream event, operation={}, nodeId={}", operation, nodeId))
                .onErrorMap(error -> toPlatformException(error, operation, node));
    }

    /**
     * 创建固定间隔重试策略，只允许网络或 5xx 类错误重试。
     */
    private Retry retrySpec(String operation, String nodeId) {
        return Retry.fixedDelay(maxRetries, retryBackoff)
                .filter(this::isRetryable)
                .doBeforeRetry(signal -> LOGGER.warn(
                        "Opencode call retrying, operation={}, nodeId={}, attempt={}/{}, error={}",
                        operation,
                        nodeId,
                        signal.totalRetries() + 1,
                        maxRetries,
                        signal.failure().getMessage()));
    }

    /**
     * 判断异常是否适合重试，避免对参数错误或业务冲突重复调用远端。
     */
    private boolean isRetryable(Throwable error) {
        Throwable current = unwrapRetry(error);
        if (current instanceof WebClientResponseException exception) {
            int status = exception.getStatusCode().value();
            return status == 502 || status == 503 || (status >= 500 && status < 600);
        }
        return hasCause(current, ConnectException.class);
    }

    private boolean isNotFoundResponse(Throwable error) {
        Throwable current = unwrapRetry(error);
        return current instanceof WebClientResponseException exception
                && exception.getStatusCode().value() == 404;
    }

    /**
     * 将 gateway 或 WebClient 异常转换为平台统一错误码，防止第三方异常穿透到入口层。
     */
    private PlatformException toPlatformException(Throwable error, String operation, ExecutionNode node) {
        Throwable current = unwrapRetry(error);
        if (current instanceof PlatformException exception) {
            return exception;
        }
        if (current instanceof TimeoutException) {
            LOGGER.error("Opencode call timeout, operation={}, nodeId={}, baseUrl={}",
                    operation, node.executionNodeId().value(), node.baseUrl());
            return platformException(ErrorCode.OPENCODE_TIMEOUT, operation, node, null, current);
        }
        if (current instanceof WebClientResponseException exception) {
            int status = exception.getStatusCode().value();
            LOGGER.error("Opencode call failed, operation={}, nodeId={}, baseUrl={}, httpStatus={}",
                    operation, node.executionNodeId().value(), node.baseUrl(), status);
            if (status == 408 || status == 504) {
                return platformException(ErrorCode.OPENCODE_TIMEOUT, operation, node, status, current);
            }
            if (status == 503) {
                return platformException(ErrorCode.OPENCODE_UNAVAILABLE, operation, node, status, current);
            }
            return platformException(ErrorCode.OPENCODE_BAD_GATEWAY, operation, node, status, current);
        }
        if (hasCause(current, ConnectException.class)) {
            LOGGER.error("Opencode connection refused, operation={}, nodeId={}, baseUrl={}",
                    operation, node.executionNodeId().value(), node.baseUrl());
            return platformException(ErrorCode.OPENCODE_UNAVAILABLE, operation, node, null, current);
        }
        LOGGER.error("Opencode call error, operation={}, nodeId={}, baseUrl={}, error={}",
                operation, node.executionNodeId().value(), node.baseUrl(), current.getMessage());
        return platformException(ErrorCode.OPENCODE_BAD_GATEWAY, operation, node, null, current);
    }

    /**
     * 构造带安全 details 的 PlatformException，details 只包含节点、操作和可公开 HTTP 状态。
     */
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

    /**
     * Reactor 重试耗尽时会包一层异常，这里还原原始失败原因用于错误码判断。
     */
    private Throwable unwrapRetry(Throwable error) {
        if (Exceptions.isRetryExhausted(error) && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    /**
     * 沿 cause 链查找指定异常类型，兼容 WebClient 包装后的连接异常。
     */
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
