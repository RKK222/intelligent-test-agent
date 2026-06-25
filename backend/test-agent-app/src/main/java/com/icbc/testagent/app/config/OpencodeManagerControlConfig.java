package com.icbc.testagent.app.config;

import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.observability.TraceIdSupport;
import com.icbc.testagent.opencode.runtime.process.LocalDirectSettings;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessHeartbeatMaintenanceService;
import com.icbc.testagent.opencode.runtime.process.socket.BackendJavaProcessLifecycleService;
import com.icbc.testagent.opencode.runtime.process.socket.ManagerControlSettings;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * opencode-manager 控制面运行装配，负责配置绑定和当前后端实例心跳 runner。
 */
@Configuration
public class OpencodeManagerControlConfig {

    /**
     * 将 app 配置转换为 runtime/API 可复用的控制面 settings。
     */
    @Bean
    ManagerControlSettings managerControlSettings(TestAgentRuntimeProperties properties) {
        TestAgentRuntimeProperties.ManagerControl control = properties.getOpencode().getManagerControl();
        return new ManagerControlSettings(
                control.getToken(),
                control.getListenUrl(),
                new LinuxServerId(control.getLinuxServerId()),
                control.getHeartbeatInterval(),
                control.getBackendStaleAfter(),
                control.getCommandTimeout(),
                control.getBackendDiscoveryLimit());
    }

    /**
     * 将 app 配置中的本地开发短路开关 + baseUrl 绑定为 runtime 可注入的 settings。
     *
     * <p>本地开发者开启后，{@link com.icbc.testagent.opencode.runtime.process.UserOpencodeProcessAssignmentService}
     * 会跳过 database topology / binding / manager 健康检测，直接合成指向 baseUrl 的 READY 进程对象。
     * 生产必须保持 false。
     */
    @Bean
    LocalDirectSettings localDirectSettings(TestAgentRuntimeProperties properties) {
        TestAgentRuntimeProperties.Opencode opencode = properties.getOpencode();
        return new LocalDirectSettings(opencode.isLocalDirect(), opencode.getLocalDirectBaseUrl());
    }

    /**
     * 注册当前后端 Java 进程生命周期 runner。
     */
    @Bean
    BackendJavaProcessLifecycleRunner backendJavaProcessLifecycleRunner(
            BackendJavaProcessLifecycleService lifecycleService,
            OpencodeProcessHeartbeatMaintenanceService heartbeatMaintenanceService,
            ManagerControlSettings settings) {
        return new BackendJavaProcessLifecycleRunner(lifecycleService, heartbeatMaintenanceService, settings);
    }

    /**
     * 后端进程生命周期 runner，只负责启动/停止时机，不承载业务规则。
     */
    static class BackendJavaProcessLifecycleRunner implements ApplicationRunner, DisposableBean {

        private final BackendJavaProcessLifecycleService lifecycleService;
        private final OpencodeProcessHeartbeatMaintenanceService heartbeatMaintenanceService;
        private final ManagerControlSettings settings;
        private ScheduledExecutorService executor;

        BackendJavaProcessLifecycleRunner(
                BackendJavaProcessLifecycleService lifecycleService,
                OpencodeProcessHeartbeatMaintenanceService heartbeatMaintenanceService,
                ManagerControlSettings settings) {
            this.lifecycleService = lifecycleService;
            this.heartbeatMaintenanceService = heartbeatMaintenanceService;
            this.settings = settings;
        }

        @Override
        public void run(ApplicationArguments args) {
            lifecycleService.registerHeartbeat(TraceIdSupport.generate());
            executor = Executors.newScheduledThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "opencode-manager-backend-heartbeat");
                thread.setDaemon(true);
                return thread;
            });
            executor.scheduleAtFixedRate(
                    () -> runSafely(() -> lifecycleService.registerHeartbeat(TraceIdSupport.generate())),
                    settings.heartbeatInterval().toMillis(),
                    settings.heartbeatInterval().toMillis(),
                    TimeUnit.MILLISECONDS);
            executor.scheduleAtFixedRate(
                    () -> runSafely(() -> heartbeatMaintenanceService.refreshRunningProcessHeartbeats(TraceIdSupport.generate())),
                    3,
                    3,
                    TimeUnit.MINUTES);
            executor.scheduleAtFixedRate(
                    () -> runSafely(heartbeatMaintenanceService::cleanupExpiredHeartbeats),
                    5,
                    5,
                    TimeUnit.MINUTES);
        }

        @Override
        public void destroy() {
            if (executor != null) {
                executor.shutdownNow();
            }
            lifecycleService.markOffline(TraceIdSupport.generate());
        }

        private void runSafely(Runnable runnable) {
            try {
                runnable.run();
            } catch (RuntimeException ignored) {
                // 周期性心跳失败不能终止调度线程；下一轮会重新写入或清理。
            }
        }
    }
}
