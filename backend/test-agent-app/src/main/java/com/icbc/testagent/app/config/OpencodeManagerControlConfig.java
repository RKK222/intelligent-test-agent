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
import org.springframework.beans.factory.annotation.Value;
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
     * 探测当前后端所在服务器真实内网 IPv4 地址，作为 advertised host 默认值。
     */
    @Bean
    LinuxServerIpResolver linuxServerIpResolver() {
        return new LinuxServerIpResolver();
    }

    /**
     * 解析稳定服务器身份：环境变量优先，缺失时读取 Java 主机名。
     */
    @Bean
    ServerIdentityResolver serverIdentityResolver() {
        return new ServerIdentityResolver();
    }

    /**
     * 解析服务器可访问地址：环境变量优先，缺失时复用内网 IP 探测。
     */
    @Bean
    ServerAdvertisedHostResolver serverAdvertisedHostResolver(LinuxServerIpResolver linuxServerIpResolver) {
        return new ServerAdvertisedHostResolver(linuxServerIpResolver);
    }

    /**
     * 将 app 配置转换为 runtime/API 可复用的控制面 settings。
     *
     * <p>linuxServerId 表示稳定服务器身份；listenUrl 使用 advertised host 生成，二者互不绑定。
     */
    @Bean
    ManagerControlSettings managerControlSettings(
            TestAgentRuntimeProperties properties,
            ServerIdentityResolver serverIdentityResolver,
            ServerAdvertisedHostResolver advertisedHostResolver,
            @Value("${server.port:8080}") int serverPort) {
        TestAgentRuntimeProperties.ManagerControl control = properties.getOpencode().getManagerControl();
        LinuxServerId linuxServerId = serverIdentityResolver.resolve();
        String advertisedHost = advertisedHostResolver.resolve();
        String listenUrl = "http://" + advertisedHost + ":" + effectiveServerPort(serverPort);
        return new ManagerControlSettings(
                control.getToken(),
                listenUrl,
                linuxServerId,
                advertisedHost,
                control.getHeartbeatInterval(),
                control.getBackendStaleAfter(),
                control.getCommandTimeout(),
                control.getBackendDiscoveryLimit());
    }

    private int effectiveServerPort(int serverPort) {
        return serverPort > 0 ? serverPort : 8080;
    }

    /**
     * 创建服务器身份/地址文件路径解析器，路径统一来自系统通用参数 SYS_DATA_ROOT_DIR。
     */
    @Bean
    ServerIdentityFilePathResolver serverIdentityFilePathResolver(CommonParameterValues commonParameterValues) {
        return new ServerIdentityFilePathResolver(commonParameterValues);
    }

    /**
     * 创建服务器身份/地址文件写入器，供生产 socket 控制面启动时发布 .serverid/.serverhost。
     */
    @Bean
    ServerIdentityFileWriter serverIdentityFileWriter(ServerIdentityFilePathResolver serverIdentityFilePathResolver) {
        return new ServerIdentityFileWriter(serverIdentityFilePathResolver);
    }

    /**
     * 注册当前后端 Java 进程生命周期 runner。
     */
    @Bean
    BackendJavaProcessLifecycleRunner backendJavaProcessLifecycleRunner(
            BackendJavaProcessLifecycleService lifecycleService,
            OpencodeProcessHeartbeatMaintenanceService heartbeatMaintenanceService,
            ManagerControlSettings settings,
            ServerIdentityFileWriter serverIdentityFileWriter) {
        return new BackendJavaProcessLifecycleRunner(
                lifecycleService, heartbeatMaintenanceService, settings, serverIdentityFileWriter);
    }

    /**
     * 后端进程生命周期 runner，只负责启动/停止时机，不承载业务规则。
     */
    static class BackendJavaProcessLifecycleRunner implements ApplicationRunner, DisposableBean {

        private final BackendJavaProcessLifecycleService lifecycleService;
        private final OpencodeProcessHeartbeatMaintenanceService heartbeatMaintenanceService;
        private final ManagerControlSettings settings;
        private final ServerIdentityFileWriter serverIdentityFileWriter;
        private ScheduledExecutorService executor;

        BackendJavaProcessLifecycleRunner(
                BackendJavaProcessLifecycleService lifecycleService,
                OpencodeProcessHeartbeatMaintenanceService heartbeatMaintenanceService,
                ManagerControlSettings settings,
                ServerIdentityFileWriter serverIdentityFileWriter) {
            this.lifecycleService = lifecycleService;
            this.heartbeatMaintenanceService = heartbeatMaintenanceService;
            this.settings = settings;
            this.serverIdentityFileWriter = serverIdentityFileWriter;
        }

        @Override
        public void run(ApplicationArguments args) {
            serverIdentityFileWriter.write(settings.linuxServerId().value(), settings.advertisedHost());
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
