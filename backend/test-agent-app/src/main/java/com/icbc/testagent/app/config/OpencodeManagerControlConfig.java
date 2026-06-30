package com.icbc.testagent.app.config;

import com.icbc.testagent.common.net.LinuxServerIpResolver;
import com.icbc.testagent.domain.configuration.CommonParameterValues;
import com.icbc.testagent.domain.opencodeprocess.LinuxServerId;
import com.icbc.testagent.observability.TraceIdSupport;
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
     * 探测当前后端所在 Linux 服务器真实内网 IPv4 地址，作为 listen-url 为本地地址时的回退来源。
     *
     * <p>启动时强制自动探测，探测失败直接抛异常让启动中断；最终服务器身份会优先采用
     * listen-url 中的非回环 IPv4。
     */
    @Bean
    LinuxServerIpResolver linuxServerIpResolver() {
        return new LinuxServerIpResolver();
    }

    /**
     * 将 app 配置转换为 runtime/API 可复用的控制面 settings。
     *
     * <p>Linux 服务器 ID 优先来自 listen-url 的非回环 IPv4；listen-url 是 127.0.0.1 /
     * localhost / 0.0.0.0 时回退到 {@link LinuxServerIpResolver} 的真实网卡探测结果。
     */
    @Bean
    ManagerControlSettings managerControlSettings(
            TestAgentRuntimeProperties properties, LinuxServerIpResolver linuxServerIpResolver) {
        TestAgentRuntimeProperties.ManagerControl control = properties.getOpencode().getManagerControl();
        return new ManagerControlSettings(
                control.getToken(),
                control.getListenUrl(),
                new LinuxServerId(linuxServerIpResolver.resolveForListenUrl(control.getListenUrl())),
                control.getHeartbeatInterval(),
                control.getBackendStaleAfter(),
                control.getCommandTimeout(),
                control.getBackendDiscoveryLimit());
    }

    /**
     * 创建服务器 IP 文件路径解析器，路径统一来自系统通用参数 SYS_DATA_ROOT_DIR。
     */
    @Bean
    ServerIpFilePathResolver serverIpFilePathResolver(CommonParameterValues commonParameterValues) {
        return new ServerIpFilePathResolver(commonParameterValues);
    }

    /**
     * 创建服务器 IP 文件写入器，供生产 socket 控制面启动时发布 .serverip。
     */
    @Bean
    ServerIpFileWriter serverIpFileWriter(ServerIpFilePathResolver serverIpFilePathResolver) {
        return new ServerIpFileWriter(serverIpFilePathResolver);
    }

    /**
     * 注册当前后端 Java 进程生命周期 runner。
     */
    @Bean
    BackendJavaProcessLifecycleRunner backendJavaProcessLifecycleRunner(
            BackendJavaProcessLifecycleService lifecycleService,
            OpencodeProcessHeartbeatMaintenanceService heartbeatMaintenanceService,
            ManagerControlSettings settings,
            ServerIpFileWriter serverIpFileWriter) {
        return new BackendJavaProcessLifecycleRunner(
                lifecycleService, heartbeatMaintenanceService, settings, serverIpFileWriter);
    }

    /**
     * 后端进程生命周期 runner，只负责启动/停止时机，不承载业务规则。
     */
    static class BackendJavaProcessLifecycleRunner implements ApplicationRunner, DisposableBean {

        private final BackendJavaProcessLifecycleService lifecycleService;
        private final OpencodeProcessHeartbeatMaintenanceService heartbeatMaintenanceService;
        private final ManagerControlSettings settings;
        private final ServerIpFileWriter serverIpFileWriter;
        private ScheduledExecutorService executor;

        BackendJavaProcessLifecycleRunner(
                BackendJavaProcessLifecycleService lifecycleService,
                OpencodeProcessHeartbeatMaintenanceService heartbeatMaintenanceService,
                ManagerControlSettings settings,
                ServerIpFileWriter serverIpFileWriter) {
            this.lifecycleService = lifecycleService;
            this.heartbeatMaintenanceService = heartbeatMaintenanceService;
            this.settings = settings;
            this.serverIpFileWriter = serverIpFileWriter;
        }

        @Override
        public void run(ApplicationArguments args) {
            serverIpFileWriter.write(settings.linuxServerId().value());
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
