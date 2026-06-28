package com.icbc.testagent.opencode.runtime.process.socket;

import com.icbc.testagent.domain.broadcast.ServerBroadcastPublisher;
import com.icbc.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 当前后端 Java 实例身份实现；复用 manager 控制面设置与后端进程生命周期服务，避免身份来源分裂。
 */
@Service
public class ManagerBackendInstanceIdentity implements BackendInstanceIdentity {

    private final ManagerControlSettings settings;
    private final BackendJavaProcessLifecycleService lifecycleService;
    private final ServerBroadcastPublisher broadcastPublisher;

    public ManagerBackendInstanceIdentity(
            ManagerControlSettings settings,
            BackendJavaProcessLifecycleService lifecycleService,
            ServerBroadcastPublisher broadcastPublisher) {
        this.settings = Objects.requireNonNull(settings, "settings must not be null");
        this.lifecycleService = Objects.requireNonNull(lifecycleService, "lifecycleService must not be null");
        this.broadcastPublisher = Objects.requireNonNull(broadcastPublisher, "broadcastPublisher must not be null");
    }

    @Override
    public String instanceId() {
        return broadcastPublisher.instanceId();
    }

    @Override
    public String linuxServerId() {
        return settings.linuxServerId().value();
    }

    @Override
    public String backendProcessId() {
        return lifecycleService.backendProcessId().value();
    }

    @Override
    public String listenUrl() {
        return settings.listenUrl();
    }
}
