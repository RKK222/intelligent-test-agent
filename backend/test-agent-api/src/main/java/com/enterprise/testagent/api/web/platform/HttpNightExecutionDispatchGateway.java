package com.enterprise.testagent.api.web.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.nightexecution.NightExecutionTaskId;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionDispatchBatchResult;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionDispatchGateway;
import com.enterprise.testagent.opencode.runtime.night.NightExecutionDispatchService;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.xxljob.XxlJobProperties;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 使用公共 Java 路由解析器和转发器实现 XXL 扫描到目标 Java 的批量调用。 */
@Service
public class HttpNightExecutionDispatchGateway implements NightExecutionDispatchGateway {

    private final BackendJavaRouteResolver routeResolver;
    private final BackendHttpForwarder forwarder;
    private final NightExecutionDispatchService localService;
    private final XxlJobProperties properties;

    public HttpNightExecutionDispatchGateway(
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder,
            NightExecutionDispatchService localService,
            XxlJobProperties properties) {
        this.routeResolver = Objects.requireNonNull(routeResolver);
        this.forwarder = Objects.requireNonNull(forwarder);
        this.localService = Objects.requireNonNull(localService);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public Mono<NightExecutionDispatchBatchResult> dispatch(
            String linuxServerId,
            List<NightExecutionTaskId> taskIds,
            String traceId) {
        BackendJavaProcess backend = routeResolver.requireBackend(linuxServerId);
        if (routeResolver.isCurrent(backend.backendProcessId())) {
            return localService.dispatchBatch(linuxServerId, taskIds, traceId);
        }
        NightExecutionInternalDispatchDtos.Request request = new NightExecutionInternalDispatchDtos.Request(
                linuxServerId,
                taskIds.stream().map(NightExecutionTaskId::value).toList());
        return Mono.fromCallable(() -> {
                    ApiResponse<NightExecutionInternalDispatchDtos.Response> response =
                            forwarder.forwardSystemTyped(
                                    backend,
                                    NightExecutionInternalDispatchController.PATH,
                                    request,
                                    new TypeReference<>() { },
                                    traceId,
                                    properties.getAccessToken());
                    return response.data().toDomain();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
