package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientConnectionRegistry;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.xxljob.XxlJobProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 新连接登记后通过公共 Java 路由精确关闭旧 generation。 */
@Service
public class LocalClientConnectionSupersessionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalClientConnectionSupersessionService.class);

    private final BackendJavaRouteResolver routeResolver;
    private final BackendHttpForwarder forwarder;
    private final LocalClientConnectionRegistry localConnections;
    private final XxlJobProperties properties;

    public LocalClientConnectionSupersessionService(
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder,
            LocalClientConnectionRegistry localConnections,
            XxlJobProperties properties) {
        this.routeResolver = Objects.requireNonNull(routeResolver);
        this.forwarder = Objects.requireNonNull(forwarder);
        this.localConnections = Objects.requireNonNull(localConnections);
        this.properties = Objects.requireNonNull(properties);
    }

    public void supersede(LocalClientConnectionRoute previous, LocalClientConnectionRoute current, String traceId) {
        if (previous == null || previous.connectionGeneration() == current.connectionGeneration()) {
            return;
        }
        if (routeResolver.isCurrent(previous.backendProcessId())) {
            localConnections.close(
                    previous.clientInstanceId(),
                    previous.connectionGeneration(),
                    "CONNECTION_SUPERSEDED");
            return;
        }
        try {
            BackendJavaProcess backend = routeResolver.requireBackend(previous.backendProcessId());
            ApiResponse<LocalClientInternalConnectionDtos.RevokeResponse> ignored = forwarder.forwardSystemTyped(
                    backend,
                    LocalClientInternalConnectionController.PATH,
                    new LocalClientInternalConnectionDtos.RevokeRequest(
                            previous.clientInstanceId().value(),
                            previous.connectionGeneration(),
                            "CONNECTION_SUPERSEDED"),
                    new TypeReference<>() { },
                    traceId,
                    properties.getAccessToken());
        } catch (RuntimeException exception) {
            // 新 route 已用更大 generation fencing；旧节点不可达时由其下一帧自关闭，不能阻塞新连接。
            LOGGER.warn(
                    "local_client_supersede_forward_failed clientInstanceId={} oldGeneration={} backendProcessId={} traceId={}",
                    previous.clientInstanceId().value(),
                    previous.connectionGeneration(),
                    previous.backendProcessId().value(),
                    traceId,
                    exception);
        }
    }
}
