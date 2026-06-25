package com.icbc.testagent.opencode.runtime.process.socket;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessHealthCommand;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessHealthResult;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessManagerGateway;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessStartCommand;
import com.icbc.testagent.opencode.runtime.process.OpencodeProcessStartResult;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 本地开发态网关：直接把健康检测打到 opencode server 的 baseUrl，跳过 manager WebSocket。
 *
 * <p>仅在 {@code test-agent.opencode.manager-control.gateway-mode=local} 时启用。
 * 用于本地开发者在没有 opencode-manager 容器但已经手动启动了一个 opencode server（与 V17 迁移
 * 预置的容器、manager、user binding 配合）的情况下，让前端用户进程状态从 UNAVAILABLE/UNHEALTHY
 * 升级为 READY，命中本地 baseUrl。
 *
 * <p>约束：
 * <ul>
 *   <li>{@link #checkHealth} 用 HTTP GET 检查 baseUrl，返回 2xx 视为健康，否则视为不可用。</li>
 *   <li>{@link #startProcess} 不实际拉起 opencode 进程（假设本地已手动运行），直接返回成功占位，
 *       这样已通过迁移预置 binding + opencode_server_processes 的用户在调用 initialize 时不会被
 *       这里的网关阻塞。</li>
 *   <li>本实现不发送真实 manager 心跳，仍依赖 V17 自举的 manager-backend 连接行满足数据库层面
 *       拓扑查询，不替代生产环境部署。</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "test-agent.opencode.manager-control.gateway-mode", havingValue = "local")
public class LocalOpencodeProcessManagerGateway implements OpencodeProcessManagerGateway {

    private static final Logger log = LoggerFactory.getLogger(LocalOpencodeProcessManagerGateway.class);
    private final HttpClient httpClient;
    private final Duration healthTimeout;

    public LocalOpencodeProcessManagerGateway() {
        this(Duration.ofSeconds(3));
    }

    public LocalOpencodeProcessManagerGateway(Duration healthTimeout) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        this.healthTimeout = healthTimeout;
    }

    @Override
    public OpencodeProcessStartResult startProcess(OpencodeProcessStartCommand command) {
        // 本地开发假设 opencode server 已由 V17 迁移指向的进程（如手动启动的 127.0.0.1:4096）就绪，
        // 这里只做占位返回，避免 binding 已被迁移预置的 initialize 路径被卡住。
        log.info("local gateway 跳过真实拉起，直接放行 userId={} containerId={} linuxServerId={} port={}",
                command.userId() == null ? null : command.userId().value(),
                command.containerId() == null ? null : command.containerId().value(),
                command.linuxServerId() == null ? null : command.linuxServerId().value(),
                command.port());
        return new OpencodeProcessStartResult(0L, "local-skip");
    }

    @Override
    public OpencodeProcessHealthResult checkHealth(OpencodeProcessHealthCommand command) {
        if (command.baseUrl() == null || command.baseUrl().isBlank()) {
            return OpencodeProcessHealthResult.unhealthy("opencode baseUrl 为空，无法做本地健康检测");
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(command.baseUrl()))
                    .timeout(healthTimeout)
                    .GET()
                    .build();
            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            int status = response.statusCode();
            if (status >= 200 && status < 400) {
                return OpencodeProcessHealthResult.healthy("local-direct-http:" + status);
            }
            return OpencodeProcessHealthResult.unhealthy("opencode baseUrl 状态码非 2xx/3xx: " + status);
        } catch (Exception exception) {
            // 本地开发态把网络异常统一包装为不可用，避免把任意异常抛给前端。
            PlatformException unavailable = new PlatformException(
                    ErrorCode.OPENCODE_UNAVAILABLE,
                    "本地 opencode server " + command.baseUrl() + " 无法访问: " + exception.getMessage());
            if (log.isDebugEnabled()) {
                log.debug("local gateway health 失败 baseUrl={} message={}", command.baseUrl(), unavailable.getMessage());
            }
            return OpencodeProcessHealthResult.unhealthy(unavailable.getMessage());
        }
    }
}
