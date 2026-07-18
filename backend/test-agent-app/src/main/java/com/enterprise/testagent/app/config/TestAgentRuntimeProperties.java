package com.enterprise.testagent.app.config;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * test-agent 运行时配置，集中绑定安全、限流、文件和 opencode 控制面配置。
 */
@Component
@ConfigurationProperties(prefix = "test-agent")
public class TestAgentRuntimeProperties {

    private final Deployment deployment = new Deployment();
    private final Security security = new Security();
    private final RateLimit rateLimit = new RateLimit();
    private final Files files = new Files();
    private final Opencode opencode = new Opencode();
    private final Terminal terminal = new Terminal();
    private final GitCloneCache gitCloneCache = new GitCloneCache();

    /**
     * 返回部署环境配置。
     */
    public Deployment getDeployment() {
        return deployment;
    }

    /**
     * 返回安全配置，包括 API token 和 CORS 来源白名单。
     */
    public Security getSecurity() {
        return security;
    }

    /**
     * 返回入口层限流配置。
     */
    public RateLimit getRateLimit() {
        return rateLimit;
    }

    /**
     * 返回工作区文件访问限制配置。
     */
    public Files getFiles() {
        return files;
    }

    /**
     * 返回 opencode 控制面配置。
     */
    public Opencode getOpencode() {
        return opencode;
    }

    /**
     * 返回受控 PTY 终端安全限制配置。
     */
    public Terminal getTerminal() {
        return terminal;
    }

    /**
     * 返回 Git 浅克隆缓存配置。
     */
    public GitCloneCache getGitCloneCache() {
        return gitCloneCache;
    }

    /**
     * 部署环境配置项。
     */
    public static class Deployment {
        /**
         * 部署模式：external（外部部署，默认）或 internal（企业内部部署）。
         */
        private String mode = "external";

        /**
         * 返回部署模式。
         */
        public String getMode() {
            return mode;
        }

        /**
         * 绑定部署模式。
         */
        public void setMode(String mode) {
            this.mode = mode;
        }

        /**
         * 判断是否为企业内部部署模式。
         */
        public boolean isInternal() {
            return "internal".equalsIgnoreCase(mode);
        }

        /**
         * 判断是否为外部部署模式。
         */
        public boolean isExternal() {
            return !isInternal();
        }
    }

    /**
     * 安全相关配置项。
     */
    public static class Security {
        private String apiToken;
        private List<String> corsAllowedOrigins = new ArrayList<>(List.of(
                "http://localhost:3000",
                "http://127.0.0.1:3000",
                "http://localhost:4173",
                "http://127.0.0.1:4173",
                "http://localhost:4177",
                "http://127.0.0.1:4177",
                "http://localhost:4187",
                "http://127.0.0.1:4187",
                "http://localhost:5173",
                "http://127.0.0.1:5173",
                "http://localhost:5174",
                "http://127.0.0.1:5174"));

        /**
         * 返回后端 API token，未配置时本地开发可关闭 token 校验。
         */
        public String getApiToken() {
            return apiToken;
        }

        /**
         * 绑定后端 API token。
         */
        public void setApiToken(String apiToken) {
            this.apiToken = apiToken;
        }

        /**
         * 返回允许跨域访问后端的前端来源。
         */
        public List<String> getCorsAllowedOrigins() {
            return corsAllowedOrigins;
        }

        /**
         * 绑定 CORS 来源列表，null 会被规整为空列表。
         */
        public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
            this.corsAllowedOrigins = corsAllowedOrigins == null ? new ArrayList<>() : new ArrayList<>(corsAllowedOrigins);
        }
    }

    /**
     * HTTP 入口限流配置项。
     */
    public static class RateLimit {
        private boolean enabled = false;
        private int capacity = 120;
        private Duration window = Duration.ofMinutes(1);

        /**
         * 返回是否启用入口层限流。
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 绑定是否启用入口层限流。
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 返回单窗口最大请求数。
         */
        public int getCapacity() {
            return capacity;
        }

        /**
         * 绑定单窗口最大请求数。
         */
        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        /**
         * 返回限流时间窗口。
         */
        public Duration getWindow() {
            return window;
        }

        /**
         * 绑定限流时间窗口。
         */
        public void setWindow(Duration window) {
            this.window = window;
        }
    }

    /**
     * 文件读取和目录遍历限制配置项。
     */
    public static class Files {
        private long maxFileBytes = 1024 * 1024;
        private int maxDirectoryEntries = 1000;

        /**
         * 返回单文件最大读取字节数。
         */
        public long getMaxFileBytes() {
            return maxFileBytes;
        }

        /**
         * 绑定单文件最大读取字节数。
         */
        public void setMaxFileBytes(long maxFileBytes) {
            this.maxFileBytes = maxFileBytes;
        }

        /**
         * 返回单次目录列举最大条目数。
         */
        public int getMaxDirectoryEntries() {
            return maxDirectoryEntries;
        }

        /**
         * 绑定单次目录列举最大条目数。
         */
        public void setMaxDirectoryEntries(int maxDirectoryEntries) {
            this.maxDirectoryEntries = maxDirectoryEntries;
        }
    }

    /**
     * opencode 控制面配置集合。
     */
    public static class Opencode {
        private final ManagerControl managerControl = new ManagerControl();

        /**
         * 返回 opencode-manager 控制面配置。
         */
        public ManagerControl getManagerControl() {
            return managerControl;
        }

    }

    /**
     * opencode-manager 内部控制面配置项。
     */
    public static class ManagerControl {
        private String token = "";
        private Duration heartbeatInterval = Duration.ofSeconds(5);
        private Duration backendStaleAfter = Duration.ofSeconds(10);
        private Duration commandTimeout = Duration.ofSeconds(10);
        private int backendDiscoveryLimit = 100;

        /**
         * 返回 manager 控制面专用 token。
         */
        public String getToken() {
            return token;
        }

        /**
         * 绑定 manager 控制面专用 token。
         */
        public void setToken(String token) {
            this.token = token;
        }

        /**
         * 返回后端进程心跳间隔。
         */
        public Duration getHeartbeatInterval() {
            return heartbeatInterval;
        }

        /**
         * 绑定后端进程心跳间隔。
         */
        public void setHeartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
        }

        /**
         * 返回 Redis Java/manager 运行快照 TTL。
         */
        public Duration getBackendStaleAfter() {
            return backendStaleAfter;
        }

        /**
         * 绑定 Redis Java/manager 运行快照 TTL。
         */
        public void setBackendStaleAfter(Duration backendStaleAfter) {
            this.backendStaleAfter = backendStaleAfter;
        }

        /**
         * 返回后端等待 manager 命令响应的超时时间。
         */
        public Duration getCommandTimeout() {
            return commandTimeout;
        }

        /**
         * 绑定 manager 命令响应超时时间。
         */
        public void setCommandTimeout(Duration commandTimeout) {
            this.commandTimeout = commandTimeout;
        }

        /**
         * 返回 WebSocket 后端列表和兼容诊断端点最多返回的后端实例数。
         */
        public int getBackendDiscoveryLimit() {
            return backendDiscoveryLimit;
        }

        /**
         * 绑定 WebSocket 后端列表和兼容诊断端点返回上限。
         */
        public void setBackendDiscoveryLimit(int backendDiscoveryLimit) {
            this.backendDiscoveryLimit = backendDiscoveryLimit;
        }

    }

    /**
     * 受控 PTY 终端连接、输入、输出和 ticket 限制配置项。
     */
    public static class Terminal {
        private boolean serverRootEnabled;
        private Path serverWorkingDirectory = Path.of("/data/testagent");
        private String publicWebsocketBaseUrl = "";
        private int maxInputBytes = 16 * 1024;
        private int inputMessagesPerWindow = 64;
        private int resizeMessagesPerWindow = 10;
        private Duration rateLimitWindow = Duration.ofSeconds(1);
        private int maxOutputFrameBytes = 16 * 1024;
        private int maxOutputConnectionBytes = 1024 * 1024;
        private Duration idleTimeout = Duration.ofMinutes(10);
        private Duration hardTimeout = Duration.ofHours(2);
        private int ticketCapacity = 10;
        private Duration ticketWindow = Duration.ofMinutes(1);

        /** 返回是否开放超级管理员服务器 root 终端。 */
        public boolean isServerRootEnabled() {
            return serverRootEnabled;
        }

        /** 绑定是否开放超级管理员服务器 root 终端。 */
        public void setServerRootEnabled(boolean serverRootEnabled) {
            this.serverRootEnabled = serverRootEnabled;
        }

        /** 返回服务器终端固定工作目录。 */
        public Path getServerWorkingDirectory() {
            return serverWorkingDirectory;
        }

        /** 绑定服务器终端固定工作目录。 */
        public void setServerWorkingDirectory(Path serverWorkingDirectory) {
            this.serverWorkingDirectory = serverWorkingDirectory;
        }

        /** 返回对浏览器公开的 WSS 网关基址。 */
        public String getPublicWebsocketBaseUrl() {
            return publicWebsocketBaseUrl;
        }

        /** 绑定对浏览器公开的 WSS 网关基址。 */
        public void setPublicWebsocketBaseUrl(String publicWebsocketBaseUrl) {
            this.publicWebsocketBaseUrl = publicWebsocketBaseUrl;
        }

        /**
         * 返回单条输入消息允许的最大字节数。
         */
        public int getMaxInputBytes() {
            return maxInputBytes;
        }

        /**
         * 绑定单条输入消息允许的最大字节数。
         */
        public void setMaxInputBytes(int maxInputBytes) {
            this.maxInputBytes = maxInputBytes;
        }

        /**
         * 返回每个限流窗口允许的输入消息数。
         */
        public int getInputMessagesPerWindow() {
            return inputMessagesPerWindow;
        }

        /**
         * 绑定每个限流窗口允许的输入消息数。
         */
        public void setInputMessagesPerWindow(int inputMessagesPerWindow) {
            this.inputMessagesPerWindow = inputMessagesPerWindow;
        }

        /**
         * 返回每个限流窗口允许的 resize 消息数。
         */
        public int getResizeMessagesPerWindow() {
            return resizeMessagesPerWindow;
        }

        /**
         * 绑定每个限流窗口允许的 resize 消息数。
         */
        public void setResizeMessagesPerWindow(int resizeMessagesPerWindow) {
            this.resizeMessagesPerWindow = resizeMessagesPerWindow;
        }

        /**
         * 返回终端输入和 resize 的限流窗口。
         */
        public Duration getRateLimitWindow() {
            return rateLimitWindow;
        }

        /**
         * 绑定终端输入和 resize 的限流窗口。
         */
        public void setRateLimitWindow(Duration rateLimitWindow) {
            this.rateLimitWindow = rateLimitWindow;
        }

        /**
         * 返回单个输出 frame 的最大字节数。
         */
        public int getMaxOutputFrameBytes() {
            return maxOutputFrameBytes;
        }

        /**
         * 绑定单个输出 frame 的最大字节数。
         */
        public void setMaxOutputFrameBytes(int maxOutputFrameBytes) {
            this.maxOutputFrameBytes = maxOutputFrameBytes;
        }

        /**
         * 返回单条终端连接允许输出的最大累计字节数。
         */
        public int getMaxOutputConnectionBytes() {
            return maxOutputConnectionBytes;
        }

        /**
         * 绑定单条终端连接允许输出的最大累计字节数。
         */
        public void setMaxOutputConnectionBytes(int maxOutputConnectionBytes) {
            this.maxOutputConnectionBytes = maxOutputConnectionBytes;
        }

        /**
         * 返回终端无活动自动关闭时间。
         */
        public Duration getIdleTimeout() {
            return idleTimeout;
        }

        /**
         * 绑定终端无活动自动关闭时间。
         */
        public void setIdleTimeout(Duration idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        /**
         * 返回终端连接硬性最长存活时间。
         */
        public Duration getHardTimeout() {
            return hardTimeout;
        }

        /**
         * 绑定终端连接硬性最长存活时间。
         */
        public void setHardTimeout(Duration hardTimeout) {
            this.hardTimeout = hardTimeout;
        }

        /**
         * 返回单窗口可发放的终端 ticket 数。
         */
        public int getTicketCapacity() {
            return ticketCapacity;
        }

        /**
         * 绑定单窗口可发放的终端 ticket 数。
         */
        public void setTicketCapacity(int ticketCapacity) {
            this.ticketCapacity = ticketCapacity;
        }

        /**
         * 返回终端 ticket 发放限流窗口。
         */
        public Duration getTicketWindow() {
            return ticketWindow;
        }

        /**
         * 绑定终端 ticket 发放限流窗口。
         */
        public void setTicketWindow(Duration ticketWindow) {
            this.ticketWindow = ticketWindow;
        }
    }

    /**
     * Git 浅克隆缓存配置项。
     */
    public static class GitCloneCache {
        /**
         * 缓存根目录路径，默认为系统临时目录下的 git-clone-cache。
         */
        private Path cacheRoot = Path.of(System.getProperty("java.io.tmpdir"), "git-clone-cache");

        /**
         * 缓存过期时间，默认 1 小时。
         */
        private Duration cacheExpiry = Duration.ofHours(1);

        /**
         * 浅克隆超时时间，默认 5 分钟。
         */
        private Duration cloneTimeout = Duration.ofMinutes(5);

        /**
         * 返回缓存根目录路径。
         */
        public Path getCacheRoot() {
            return cacheRoot;
        }

        /**
         * 绑定缓存根目录路径。
         */
        public void setCacheRoot(Path cacheRoot) {
            this.cacheRoot = cacheRoot;
        }

        /**
         * 返回缓存过期时间。
         */
        public Duration getCacheExpiry() {
            return cacheExpiry;
        }

        /**
         * 绑定缓存过期时间。
         */
        public void setCacheExpiry(Duration cacheExpiry) {
            this.cacheExpiry = cacheExpiry;
        }

        /**
         * 返回浅克隆超时时间。
         */
        public Duration getCloneTimeout() {
            return cloneTimeout;
        }

        /**
         * 绑定浅克隆超时时间。
         */
        public void setCloneTimeout(Duration cloneTimeout) {
            this.cloneTimeout = cloneTimeout;
        }
    }
}
