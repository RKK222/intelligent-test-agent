package com.icbc.testagent.app.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * test-agent 运行时配置，集中绑定安全、限流、文件、目录选择、Redis 和 opencode 节点配置。
 */
@Component
@ConfigurationProperties(prefix = "test-agent")
public class TestAgentRuntimeProperties {

    private final Security security = new Security();
    private final RateLimit rateLimit = new RateLimit();
    private final Files files = new Files();
    private final WorkspacePicker workspacePicker = new WorkspacePicker();
    private final Redis redis = new Redis();
    private final Opencode opencode = new Opencode();
    private final Terminal terminal = new Terminal();
    private final GitCloneCache gitCloneCache = new GitCloneCache();

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
     * 返回工作区目录选择器配置。
     */
    public WorkspacePicker getWorkspacePicker() {
        return workspacePicker;
    }

    /**
     * 返回可选 Redis 连接配置。
     */
    public Redis getRedis() {
        return redis;
    }

    /**
     * 返回 opencode 运行节点配置。
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
     * 工作区目录选择器安全边界配置项。
     */
    public static class WorkspacePicker {
        private List<String> allowedRoots = new ArrayList<>(List.of(
                Path.of(System.getProperty("user.home"), "workspace").toString()));

        /**
         * 返回允许前端浏览和选择的本机目录根。
         */
        public List<String> getAllowedRoots() {
            return allowedRoots;
        }

        /**
         * 绑定允许目录根，null 会被规整为空列表以便部署显式关闭默认值。
         */
        public void setAllowedRoots(List<String> allowedRoots) {
            this.allowedRoots = allowedRoots == null ? new ArrayList<>() : new ArrayList<>(allowedRoots);
        }
    }

    /**
     * 可选 Redis 健康检查和后续缓存能力配置项。
     */
    public static class Redis {
        private boolean enabled = false;
        private String host = "127.0.0.1";
        private int port = 6379;
        private String password;
        private Duration timeout = Duration.ofSeconds(1);

        /**
         * 返回是否启用 Redis 探测。
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * 绑定是否启用 Redis 探测。
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * 返回 Redis 主机名。
         */
        public String getHost() {
            return host;
        }

        /**
         * 绑定 Redis 主机名。
         */
        public void setHost(String host) {
            this.host = host;
        }

        /**
         * 返回 Redis 端口。
         */
        public int getPort() {
            return port;
        }

        /**
         * 绑定 Redis 端口。
         */
        public void setPort(int port) {
            this.port = port;
        }

        /**
         * 返回 Redis 密码。
         */
        public String getPassword() {
            return password;
        }

        /**
         * 绑定 Redis 密码。
         */
        public void setPassword(String password) {
            this.password = password;
        }

        /**
         * 返回 Redis TCP 探测超时时间。
         */
        public Duration getTimeout() {
            return timeout;
        }

        /**
         * 绑定 Redis TCP 探测超时时间。
         */
        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    /**
     * opencode 运行节点配置集合。
     */
    public static class Opencode {
        private List<Node> nodes = new ArrayList<>();
        private final ManagerControl managerControl = new ManagerControl();
        /**
         * 本地开发短路开关：true 时直接走预设的 baseUrl，不再校验 database 拓扑 /
         * binding / manager 健康检测。生产必须保持 false。
         */
        private boolean localDirect = false;
        /**
         * 本地开发短路使用的 opencode server baseUrl（默认 127.0.0.1:4096）。
         */
        private String localDirectBaseUrl = "http://127.0.0.1:4096";

        /**
         * 返回配置化 opencode 节点列表。
         */
        public List<Node> getNodes() {
            return nodes;
        }

        /**
         * 绑定 opencode 节点列表，null 会被规整为空列表。
         */
        public void setNodes(List<Node> nodes) {
            this.nodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
        }

        /**
         * 返回 opencode-manager 控制面配置。
         */
        public ManagerControl getManagerControl() {
            return managerControl;
        }

        /**
         * 返回是否启用本地开发短路模式（跳过 database 校验、直连 baseUrl）。
         */
        public boolean isLocalDirect() {
            return localDirect;
        }

        /**
         * 绑定是否启用本地开发短路模式。生产必须保持 false。
         */
        public void setLocalDirect(boolean localDirect) {
            this.localDirect = localDirect;
        }

        /**
         * 返回本地开发短路使用的 opencode server baseUrl。
         */
        public String getLocalDirectBaseUrl() {
            return localDirectBaseUrl;
        }

        /**
         * 绑定本地开发短路使用的 baseUrl；空值会回退到默认 127.0.0.1:4096。
         */
        public void setLocalDirectBaseUrl(String localDirectBaseUrl) {
            this.localDirectBaseUrl = (localDirectBaseUrl == null || localDirectBaseUrl.isBlank())
                    ? "http://127.0.0.1:4096"
                    : localDirectBaseUrl.trim();
        }
    }

    /**
     * opencode-manager 内部控制面配置项。
     */
    public static class ManagerControl {
        private String token = "";
        private String listenUrl = "http://127.0.0.1:8080";
        private Duration heartbeatInterval = Duration.ofSeconds(10);
        private Duration backendStaleAfter = Duration.ofSeconds(30);
        private Duration commandTimeout = Duration.ofSeconds(10);
        private int backendDiscoveryLimit = 100;
        /**
         * 控制面网关模式：socket=生产 WebSocket；local=本地直连 baseUrl 的开发态占位，
         * 不依赖 opencode-manager 长连接。默认 socket。
         */
        private String gatewayMode = "socket";

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
         * 返回当前后端实例可被 manager 直连的 HTTP 地址。
         */
        public String getListenUrl() {
            return listenUrl;
        }

        /**
         * 绑定当前后端实例直连 HTTP 地址。
         */
        public void setListenUrl(String listenUrl) {
            this.listenUrl = listenUrl;
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
         * 返回 manager discovery 认为后端实例仍可用的心跳窗口。
         */
        public Duration getBackendStaleAfter() {
            return backendStaleAfter;
        }

        /**
         * 绑定后端实例心跳过期窗口。
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
         * 返回 discovery API 最多返回的后端实例数。
         */
        public int getBackendDiscoveryLimit() {
            return backendDiscoveryLimit;
        }

        /**
         * 绑定 discovery API 返回上限。
         */
        public void setBackendDiscoveryLimit(int backendDiscoveryLimit) {
            this.backendDiscoveryLimit = backendDiscoveryLimit;
        }

        /**
         * 返回控制面网关模式：socket（生产 WebSocket）/ local（本地直连）。
         */
        public String getGatewayMode() {
            return gatewayMode;
        }

        /**
         * 绑定控制面网关模式，未知值会被规整为 socket。
         */
        public void setGatewayMode(String gatewayMode) {
            this.gatewayMode = (gatewayMode == null || gatewayMode.isBlank()) ? "socket" : gatewayMode.trim();
        }
    }

    /**
     * 受控 PTY 终端连接、输入、输出和 ticket 限制配置项。
     */
    public static class Terminal {
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
     * 单个 opencode 执行节点配置。
     */
    public static class Node {
        private String id = "node_local_opencode";
        private String baseUrl = "http://127.0.0.1:4096";
        private int maxRuns = 4;
        private int weight = 100;
        private List<String> capabilities = new ArrayList<>(List.of("chat", "diff", "test"));

        /**
         * 返回执行节点 ID。
         */
        public String getId() {
            return id;
        }

        /**
         * 绑定执行节点 ID。
         */
        public void setId(String id) {
            this.id = id;
        }

        /**
         * 返回 opencode server baseUrl。
         */
        public String getBaseUrl() {
            return baseUrl;
        }

        /**
         * 绑定 opencode server baseUrl。
         */
        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        /**
         * 返回该节点最大并发运行数。
         */
        public int getMaxRuns() {
            return maxRuns;
        }

        /**
         * 绑定该节点最大并发运行数。
         */
        public void setMaxRuns(int maxRuns) {
            this.maxRuns = maxRuns;
        }

        /**
         * 返回路由权重，负载相同时权重越高越优先。
         */
        public int getWeight() {
            return weight;
        }

        /**
         * 绑定路由权重。
         */
        public void setWeight(int weight) {
            this.weight = weight;
        }

        /**
         * 返回节点能力标签。
         */
        public List<String> getCapabilities() {
            return capabilities;
        }

        /**
         * 绑定节点能力标签，null 会被规整为空列表。
         */
        public void setCapabilities(List<String> capabilities) {
            this.capabilities = capabilities == null ? new ArrayList<>() : new ArrayList<>(capabilities);
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
