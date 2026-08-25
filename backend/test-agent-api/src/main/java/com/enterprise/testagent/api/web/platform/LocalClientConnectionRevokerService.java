package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRevoker;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionRoute;
import com.enterprise.testagent.domain.localclient.LocalClientConnectionStore;
import com.enterprise.testagent.domain.localclient.LocalClientInstance;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceRepository;
import com.enterprise.testagent.domain.localclient.LocalClientModelGrantStore;
import com.enterprise.testagent.domain.opencodeprocess.BackendJavaProcess;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientConnectionRegistry;
import com.enterprise.testagent.opencode.runtime.process.BackendJavaRouteResolver;
import com.enterprise.testagent.xxljob.XxlJobProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** 密钥轮换/撤销后的跨节点连接失效实现；路由先 fencing，再关闭物理连接。 */
@Service
public class LocalClientConnectionRevokerService implements LocalClientConnectionRevoker {

    private static final Logger LOGGER = LoggerFactory.getLogger(LocalClientConnectionRevokerService.class);

    private final LocalClientInstanceRepository instanceRepository;
    private final LocalClientConnectionStore connectionStore;
    private final LocalClientModelGrantStore grantStore;
    private final BackendJavaRouteResolver routeResolver;
    private final BackendHttpForwarder forwarder;
    private final LocalClientConnectionRegistry localConnections;
    private final XxlJobProperties properties;

    public LocalClientConnectionRevokerService(
            LocalClientInstanceRepository instanceRepository,
            LocalClientConnectionStore connectionStore,
            LocalClientModelGrantStore grantStore,
            BackendJavaRouteResolver routeResolver,
            BackendHttpForwarder forwarder,
            LocalClientConnectionRegistry localConnections,
            XxlJobProperties properties) {
        this.instanceRepository = Objects.requireNonNull(instanceRepository);
        this.connectionStore = Objects.requireNonNull(connectionStore);
        this.grantStore = Objects.requireNonNull(grantStore);
        this.routeResolver = Objects.requireNonNull(routeResolver);
        this.forwarder = Objects.requireNonNull(forwarder);
        this.localConnections = Objects.requireNonNull(localConnections);
        this.properties = Objects.requireNonNull(properties);
    }

    @Override
    public void revokeAll(UserId userId, String reason, String traceId) {
        for (LocalClientInstance instance : allOwnedInstances(userId)) {
            connectionStore.find(instance.clientInstanceId()).ifPresent(route -> revoke(route, reason, traceId));
        }
    }

    @Override
    public void revokeAllExcept(
            UserId userId,
            LocalClientInstanceId retainedClientInstanceId,
            long retainedGeneration,
            String reason,
            String traceId) {
        for (LocalClientInstance instance : allOwnedInstances(userId)) {
            connectionStore.find(instance.clientInstanceId())
                    .filter(route -> !route.clientInstanceId().equals(retainedClientInstanceId)
                            || route.connectionGeneration() != retainedGeneration)
                    .ifPresent(route -> revoke(route, reason, traceId));
        }
    }

    private List<LocalClientInstance> allOwnedInstances(UserId userId) {
        // 已替换实例也可能残留短 TTL 路由；撤销语义不能受用户投影过滤影响。
        return instanceRepository.findByUserIdIncludingReplaced(userId);
    }

    private void revoke(LocalClientConnectionRoute route, String reason, String traceId) {
        boolean deleted = connectionStore.delete(route.clientInstanceId(), route.connectionGeneration());
        grantStore.deleteForConnection(route.clientInstanceId(), route.connectionGeneration());
        if (deleted) {
            instanceRepository.markDisconnected(route.clientInstanceId(), Instant.now());
        }
        try {
            if (routeResolver.isCurrent(route.backendProcessId())) {
                localConnections.close(route.clientInstanceId(), route.connectionGeneration(), reason);
                return;
            }
            BackendJavaProcess backend = routeResolver.requireBackend(route.backendProcessId());
            ApiResponse<LocalClientInternalConnectionDtos.RevokeResponse> ignored = forwarder.forwardSystemTyped(
                    backend,
                    LocalClientInternalConnectionController.PATH,
                    new LocalClientInternalConnectionDtos.RevokeRequest(
                            route.clientInstanceId().value(), route.connectionGeneration(), reason),
                    new TypeReference<>() { },
                    traceId,
                    properties.getAccessToken());
        } catch (RuntimeException exception) {
            // Redis 路由已删除，远端旧连接下一帧也会因 fencing 关闭；保留告警供运维追踪。
            LOGGER.warn(
                    "local_client_remote_revoke_failed clientInstanceId={} generation={} backendProcessId={} traceId={}",
                    route.clientInstanceId().value(),
                    route.connectionGeneration(),
                    route.backendProcessId().value(),
                    traceId,
                    exception);
        }
    }
}
