package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.pagination.PageRequest;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessHeartbeatStore;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessManagementRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessFilter;
import com.icbc.testagent.domain.opencodeprocess.OpencodeServerProcessStatus;
import java.time.Clock;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 运行中 opencode server 进程心跳维护服务，定期做真实健康检测并刷新 Redis 活跃 key。
 */
@Service
public class OpencodeProcessHeartbeatMaintenanceService {

    private static final int PROCESS_SCAN_LIMIT = 200;

    private final OpencodeProcessManagementRepository repository;
    private final OpencodeProcessManagerGateway gateway;
    private final OpencodeProcessHeartbeatStore heartbeatStore;
    private final BackendJavaRouteResolver routeResolver;
    private final OpencodeProcessStatusQueryService statusQueryService;
    private final Clock clock;

    /**
     * 生产构造器使用 UTC 系统时钟。
     */
    @Autowired
    public OpencodeProcessHeartbeatMaintenanceService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            BackendJavaRouteResolver routeResolver,
            OpencodeProcessStatusQueryService statusQueryService) {
        this(repository, gateway, heartbeatStore, routeResolver, statusQueryService, Clock.systemUTC());
    }

    /**
     * 测试构造器允许固定时钟。
     */
    public OpencodeProcessHeartbeatMaintenanceService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            Clock clock) {
        this(repository, gateway, heartbeatStore, null, null, clock);
    }

    /**
     * 测试构造器允许固定时钟和显式路由解析器。
     */
    public OpencodeProcessHeartbeatMaintenanceService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            BackendJavaRouteResolver routeResolver,
            Clock clock) {
        this(repository, gateway, heartbeatStore, routeResolver, null, clock);
    }

    /**
     * 完整测试构造器允许固定时钟、显式路由解析器和公共状态查询服务。
     */
    OpencodeProcessHeartbeatMaintenanceService(
            OpencodeProcessManagementRepository repository,
            OpencodeProcessManagerGateway gateway,
            OpencodeProcessHeartbeatStore heartbeatStore,
            BackendJavaRouteResolver routeResolver,
            OpencodeProcessStatusQueryService statusQueryService,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.gateway = Objects.requireNonNull(gateway, "gateway must not be null");
        this.heartbeatStore = Objects.requireNonNull(heartbeatStore, "heartbeatStore must not be null");
        this.routeResolver = routeResolver;
        this.statusQueryService = statusQueryService == null
                ? new OpencodeProcessStatusQueryService(repository, gateway, heartbeatStore, clock)
                : statusQueryService;
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    /**
     * 扫描 RUNNING 进程并刷新心跳；健康检测失败的进程会标记为 UNHEALTHY，避免管理页继续展示僵死进程。
     */
    public void refreshRunningProcessHeartbeats(String traceId) {
        var page = repository.findOpencodeServerProcesses(
                new OpencodeServerProcessFilter(OpencodeServerProcessStatus.RUNNING, currentLinuxServerId(), null, null),
                new PageRequest(1, PROCESS_SCAN_LIMIT));
        for (var process : page.items()) {
            statusQueryService.query(process.processId(), traceId);
        }
    }

    /**
     * 清理 Redis 心跳索引中过期的 Java/opencode 进程 ID。
     */
    public void cleanupExpiredHeartbeats() {
        heartbeatStore.cleanupExpiredHeartbeats();
    }

    private com.icbc.testagent.domain.opencodeprocess.LinuxServerId currentLinuxServerId() {
        return routeResolver == null ? null : routeResolver.currentLinuxServerId();
    }

}
