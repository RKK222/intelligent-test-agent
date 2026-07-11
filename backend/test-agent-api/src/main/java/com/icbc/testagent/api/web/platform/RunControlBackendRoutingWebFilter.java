package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.icbc.testagent.domain.run.RunId;
import com.icbc.testagent.opencode.runtime.run.RunEventSseRouteService;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * Run 写操作专用的跨 Java 路由过滤器。
 *
 * <p>取消请求必须回到 Run 创建时的生产服务器执行。与允许本机回放的 SSE 不同，
 * 路由或转发失败会直接转换为统一平台错误，禁止随机入口 Java 继续执行取消副作用。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 27)
class RunControlBackendRoutingWebFilter implements WebFilter {

    private static final String PLATFORM_RUN_PREFIX = "/api/internal/platform/opencode-runtime/runs/";
    private static final String AGENT_PREFIX = "/api/internal/agent/";
    private static final String RUNS_SEGMENT = "/runs/";
    private static final String CANCEL_SUFFIX = "/cancel";

    private final RunEventSseRouteService routeService;
    private final BackendHttpForwarder forwarder;
    private final BackendRoutingErrorWriter errorWriter;

    RunControlBackendRoutingWebFilter(
            RunEventSseRouteService routeService,
            BackendHttpForwarder forwarder,
            BackendRoutingErrorWriter errorWriter) {
        this.routeService = Objects.requireNonNull(routeService, "routeService must not be null");
        this.forwarder = Objects.requireNonNull(forwarder, "forwarder must not be null");
        this.errorWriter = Objects.requireNonNull(errorWriter, "errorWriter must not be null");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Optional<RunId> runId = runId(exchange);
        if (runId.isEmpty()) {
            return chain.filter(exchange);
        }
        return Mono.fromCallable(() -> routeService.forwardTargetStrict(runId.get()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(target -> target
                        .map(backend -> forward(exchange, backend))
                        .orElseGet(() -> chain.filter(exchange)))
                .onErrorResume(
                        com.icbc.testagent.common.error.PlatformException.class,
                        exception -> errorWriter.writePlatformError(exchange, exception));
    }

    private Mono<Void> forward(ServerWebExchange exchange, BackendJavaProcess backend) {
        // 普通 HTTP 公共转发器会透传认证、trace、query/body，并写入统一防循环头。
        return forwarder.forwardRaw(exchange, backend)
                .onErrorMap(exception -> forwardingFailure(backend, exception));
    }

    private Throwable forwardingFailure(BackendJavaProcess backend, Throwable exception) {
        if (exception instanceof PlatformException) {
            return exception;
        }
        // 网络异常只暴露稳定目标服务器身份，禁止把连接地址或底层错误文本回显给浏览器。
        return new PlatformException(
                ErrorCode.OPENCODE_UNAVAILABLE,
                "目标服务器后端不可用",
                Map.of("linuxServerId", backend.linuxServerId().value()),
                exception);
    }

    private Optional<RunId> runId(ServerWebExchange exchange) {
        if (!HttpMethod.POST.equals(exchange.getRequest().getMethod())) {
            return Optional.empty();
        }
        // ROUTED_HEADER 可由外部客户端伪造，cancel 写路径必须每跳重新解析生产 Java；
        // 请求到达当前被选中的生产 Java 后，strict resolver 自然返回 empty，不依赖客户端头防循环。
        return runIdFromPath(exchange.getRequest().getURI().getRawPath())
                .flatMap(this::parseRunId);
    }

    private Optional<String> runIdFromPath(String path) {
        Optional<String> platformRunId = runIdAfterPrefix(path, PLATFORM_RUN_PREFIX);
        if (platformRunId.isPresent()) {
            return platformRunId;
        }
        if (path == null || !path.startsWith(AGENT_PREFIX) || !path.endsWith(CANCEL_SUFFIX)) {
            return Optional.empty();
        }
        String suffix = path.substring(AGENT_PREFIX.length());
        int runsIndex = suffix.indexOf(RUNS_SEGMENT);
        if (runsIndex <= 0 || suffix.substring(0, runsIndex).contains("/")) {
            return Optional.empty();
        }
        return cleanRunId(suffix.substring(
                runsIndex + RUNS_SEGMENT.length(),
                suffix.length() - CANCEL_SUFFIX.length()));
    }

    private Optional<String> runIdAfterPrefix(String path, String prefix) {
        if (path == null || !path.startsWith(prefix) || !path.endsWith(CANCEL_SUFFIX)) {
            return Optional.empty();
        }
        return cleanRunId(path.substring(prefix.length(), path.length() - CANCEL_SUFFIX.length()));
    }

    private Optional<String> cleanRunId(String value) {
        return value == null || value.isBlank() || value.contains("/") ? Optional.empty() : Optional.of(value);
    }

    private Optional<RunId> parseRunId(String value) {
        try {
            return Optional.of(new RunId(value));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

}
