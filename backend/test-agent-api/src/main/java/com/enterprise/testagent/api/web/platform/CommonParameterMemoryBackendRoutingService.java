package com.enterprise.testagent.api.web.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.configuration.management.CommonParameterMemoryApplicationService;
import com.enterprise.testagent.configuration.management.CommonParameterMemoryResponses.ClusterResponse;
import com.enterprise.testagent.configuration.management.CommonParameterMemoryResponses.ProcessResponse;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.opencodeprocess.BackendProcessId;
import com.enterprise.testagent.domain.opencodeprocess.BackendRuntimeSnapshot;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 通用参数内存值的跨 Java 聚合服务，按 backendProcessId 精确路由且不合并同服务器进程。
 */
@Service
class CommonParameterMemoryBackendRoutingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommonParameterMemoryBackendRoutingService.class);
    private static final String BASE_PATH =
            "/api/internal/platform/configuration-management/common-parameters/memory-values";
    private static final int MAX_ONLINE_JAVA = 500;
    private static final int FORWARD_CONCURRENCY = 8;
    private static final Duration PROCESS_TIMEOUT = Duration.ofSeconds(10);
    private static final Comparator<ProcessResponse> PROCESS_ORDER = Comparator
            .comparing(ProcessResponse::linuxServerId, Comparator.nullsLast(String::compareTo))
            .thenComparing(ProcessResponse::backendProcessId, Comparator.nullsLast(String::compareTo));

    private final CommonParameterMemoryApplicationService localService;
    private final BackendJavaRouteResolver routeResolver;
    private final BackendHttpForwarder forwarder;
    private final Clock clock;
    private final Duration processTimeout;

    /** 生产环境使用固定的进程数、并发数和单进程超时上限。 */
    @Autowired
    CommonParameterMemoryBackendRoutingService(
            CommonParameterMemoryApplicationService localService,
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder) {
        this(localService, routeResolver, forwarder, Clock.systemUTC(), PROCESS_TIMEOUT);
    }

    /** 测试构造器允许固定采集时钟和缩短超时。 */
    CommonParameterMemoryBackendRoutingService(
            CommonParameterMemoryApplicationService localService,
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder,
            Clock clock,
            Duration processTimeout) {
        this.localService = Objects.requireNonNull(localService, "localService must not be null");
        this.routeResolver = Objects.requireNonNull(routeResolver, "routeResolver must not be null");
        this.forwarder = Objects.requireNonNull(forwarder, "forwarder must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.processTimeout = Objects.requireNonNull(processTimeout, "processTimeout must not be null");
    }

    /** 聚合全部在线 Java 的当前内存值，不触发数据库读取。 */
    Mono<ClusterResponse> queryAll(ServerWebExchange exchange) {
        return aggregate(exchange, false, null);
    }

    /** 按数据库当前值刷新全部在线 Java 的注册项，不发布二次广播。 */
    Mono<ClusterResponse> refreshAll(ServerWebExchange exchange, String traceId) {
        return aggregate(exchange, true, traceId)
                .doOnNext(response -> LOGGER.info(
                        "集群 JVM 内存通用参数手工刷新结束 traceId={} total={} success={} partial={} failed={}",
                        traceId,
                        response.totalProcesses(),
                        response.successfulProcesses(),
                        response.partiallySuccessfulProcesses(),
                        response.failedProcesses()));
    }

    /** 查询指定 Java 的当前内存值；未知、离线或超时统一返回不可用错误。 */
    Mono<ProcessResponse> queryOne(ServerWebExchange exchange, BackendProcessId backendProcessId) {
        return withTimeout(invoke(exchange, backendProcessId, false, null), backendProcessId);
    }

    /** 按数据库当前值刷新指定 Java 的全部注册项。 */
    Mono<ProcessResponse> refreshOne(
            ServerWebExchange exchange,
            BackendProcessId backendProcessId,
            String traceId) {
        return withTimeout(invoke(exchange, backendProcessId, true, traceId), backendProcessId);
    }

    private Mono<ClusterResponse> aggregate(
            ServerWebExchange exchange,
            boolean refresh,
            String traceId) {
        List<BackendJavaProcess> targets = onlineProcesses();
        return Flux.fromIterable(targets)
                .flatMap(target -> invoke(exchange, target.backendProcessId(), refresh, traceId)
                        .timeout(processTimeout)
                        .onErrorResume(error -> Mono.just(unavailable(target, error))),
                        FORWARD_CONCURRENCY)
                .collectSortedList(PROCESS_ORDER)
                .map(processes -> ClusterResponse.from(Instant.now(clock), processes));
    }

    private Mono<ProcessResponse> invoke(
            ServerWebExchange exchange,
            BackendProcessId backendProcessId,
            boolean refresh,
            String traceId) {
        return Mono.defer(() -> {
            BackendJavaProcess target = routeResolver.requireBackend(backendProcessId);
            if (routeResolver.isCurrent(backendProcessId)) {
                return Mono.fromCallable(() -> refresh ? localService.refresh(traceId) : localService.current())
                        .subscribeOn(Schedulers.boundedElastic());
            }
            if (exchange.getRequest().getHeaders().getFirst(BackendHttpForwarder.ROUTED_HEADER) != null) {
                return Mono.error(unavailableException(backendProcessId));
            }
            String path = BASE_PATH + "/" + backendProcessId.value() + (refresh ? "/refresh" : "");
            String method = refresh ? "POST" : "GET";
            return Mono.fromCallable(() -> {
                        ApiResponse<ProcessResponse> response = forwarder.forwardTyped(
                                exchange,
                                target,
                                path,
                                method,
                                null,
                                new TypeReference<>() { });
                        return Objects.requireNonNull(response.data(), "remote process response data must not be null");
                    })
                    .subscribeOn(Schedulers.boundedElastic());
        });
    }

    private Mono<ProcessResponse> withTimeout(
            Mono<ProcessResponse> source,
            BackendProcessId backendProcessId) {
        return source.timeout(processTimeout)
                // 单进程契约统一为 503，且不把 HTTP/数据库底层 cause 交给统一日志切面。
                .onErrorMap(ignored -> unavailableException(backendProcessId));
    }

    private List<BackendJavaProcess> onlineProcesses() {
        Map<String, BackendJavaProcess> latestByProcessId = new LinkedHashMap<>();
        for (BackendRuntimeSnapshot snapshot : routeResolver.liveBackendSnapshots(MAX_ONLINE_JAVA)) {
            BackendJavaProcess backend = snapshot.backendProcess();
            latestByProcessId.merge(
                    backend.backendProcessId().value(),
                    backend,
                    (left, right) -> right.lastHeartbeatAt().isAfter(left.lastHeartbeatAt()) ? right : left);
        }
        return latestByProcessId.values().stream()
                .sorted(Comparator.comparing((BackendJavaProcess item) -> item.linuxServerId().value())
                        .thenComparing(item -> item.backendProcessId().value()))
                .toList();
    }

    private ProcessResponse unavailable(BackendJavaProcess target, Throwable error) {
        boolean timeout = error instanceof TimeoutException;
        return ProcessResponse.unavailable(
                target.backendProcessId().value(),
                target.linuxServerId().value(),
                target.listenUrl(),
                Instant.now(clock),
                timeout ? "PROCESS_TIMEOUT" : "PROCESS_UNAVAILABLE",
                timeout ? "Java 进程响应超时" : "Java 进程不可用");
    }

    private PlatformException unavailableException(BackendProcessId backendProcessId) {
        return new PlatformException(
                ErrorCode.OPENCODE_UNAVAILABLE,
                "目标 Java 进程不可用",
                Map.of("backendProcessId", backendProcessId.value()));
    }
}
