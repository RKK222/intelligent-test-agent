package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.run.RunResendId;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.opencode.runtime.run.RunResendDispatchBatchResult;
import com.enterprise.testagent.opencode.runtime.run.RunResendDispatchGateway;
import com.enterprise.testagent.opencode.runtime.run.RunResendDispatchService;
import com.enterprise.testagent.xxljob.XxlJobProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 使用公共 Java 路由解析器和转发器，把到期重发固定发送到记录中的目标服务器。 */
@Service
public class HttpRunResendDispatchGateway implements RunResendDispatchGateway {

    private final BackendJavaRouteResolver routeResolver;
    private final BackendHttpForwarder forwarder;
    private final RunResendDispatchService localService;
    private final XxlJobProperties properties;

    public HttpRunResendDispatchGateway(
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder,
            RunResendDispatchService localService,
            XxlJobProperties properties) {
        this.routeResolver = Objects.requireNonNull(routeResolver);
        this.forwarder = Objects.requireNonNull(forwarder);
        this.localService = Objects.requireNonNull(localService);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public Mono<RunResendDispatchBatchResult> dispatch(
            String linuxServerId,
            List<RunResendId> resendIds,
            String traceId) {
        BackendJavaProcess backend = routeResolver.requireBackend(linuxServerId);
        if (routeResolver.isCurrent(backend.backendProcessId())) {
            return localService.dispatchBatch(linuxServerId, resendIds, traceId);
        }
        var request = new RunResendInternalDispatchDtos.Request(
                linuxServerId, resendIds.stream().map(RunResendId::value).toList());
        return Mono.fromCallable(() -> {
                    ApiResponse<RunResendInternalDispatchDtos.Response> response =
                            forwarder.forwardSystemTyped(
                                    backend,
                                    RunResendInternalDispatchController.PATH,
                                    request,
                                    new TypeReference<>() { },
                                    traceId,
                                    properties.getAccessToken());
                    return response.data().toDomain();
                })
                .subscribeOn(Schedulers.boundedElastic());
    }
}
