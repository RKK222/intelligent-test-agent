package com.example.testagent.app.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * test-agent 运行时配置，集中绑定安全、限流、文件、Redis 和 opencode 节点配置。
 */
@Component
@ConfigurationProperties(prefix = "test-agent")
public class TestAgentRuntimeProperties {

    private final Security security = new Security();
    private final RateLimit rateLimit = new RateLimit();
    private final Files files = new Files();
    private final Redis redis = new Redis();
    private final Opencode opencode = new Opencode();

    public Security getSecurity() {
        return security;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    public Files getFiles() {
        return files;
    }

    public Redis getRedis() {
        return redis;
    }

    public Opencode getOpencode() {
        return opencode;
    }

    public static class Security {
        private String apiToken;
        private List<String> corsAllowedOrigins = new ArrayList<>(List.of("http://localhost:3000"));

        public String getApiToken() {
            return apiToken;
        }

        public void setApiToken(String apiToken) {
            this.apiToken = apiToken;
        }

        public List<String> getCorsAllowedOrigins() {
            return corsAllowedOrigins;
        }

        public void setCorsAllowedOrigins(List<String> corsAllowedOrigins) {
            this.corsAllowedOrigins = corsAllowedOrigins == null ? new ArrayList<>() : new ArrayList<>(corsAllowedOrigins);
        }
    }

    public static class RateLimit {
        private boolean enabled = false;
        private int capacity = 120;
        private Duration window = Duration.ofMinutes(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }

        public Duration getWindow() {
            return window;
        }

        public void setWindow(Duration window) {
            this.window = window;
        }
    }

    public static class Files {
        private long maxFileBytes = 1024 * 1024;
        private int maxDirectoryEntries = 1000;

        public long getMaxFileBytes() {
            return maxFileBytes;
        }

        public void setMaxFileBytes(long maxFileBytes) {
            this.maxFileBytes = maxFileBytes;
        }

        public int getMaxDirectoryEntries() {
            return maxDirectoryEntries;
        }

        public void setMaxDirectoryEntries(int maxDirectoryEntries) {
            this.maxDirectoryEntries = maxDirectoryEntries;
        }
    }

    public static class Redis {
        private boolean enabled = false;
        private String host = "127.0.0.1";
        private int port = 6379;
        private Duration timeout = Duration.ofSeconds(1);

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration timeout) {
            this.timeout = timeout;
        }
    }

    public static class Opencode {
        private List<Node> nodes = new ArrayList<>();

        public List<Node> getNodes() {
            return nodes;
        }

        public void setNodes(List<Node> nodes) {
            this.nodes = nodes == null ? new ArrayList<>() : new ArrayList<>(nodes);
        }
    }

    public static class Node {
        private String id = "node_local_opencode";
        private String baseUrl = "http://127.0.0.1:4096";
        private int maxRuns = 4;
        private int weight = 100;
        private List<String> capabilities = new ArrayList<>(List.of("chat", "diff", "test"));

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public int getMaxRuns() {
            return maxRuns;
        }

        public void setMaxRuns(int maxRuns) {
            this.maxRuns = maxRuns;
        }

        public int getWeight() {
            return weight;
        }

        public void setWeight(int weight) {
            this.weight = weight;
        }

        public List<String> getCapabilities() {
            return capabilities;
        }

        public void setCapabilities(List<String> capabilities) {
            this.capabilities = capabilities == null ? new ArrayList<>() : new ArrayList<>(capabilities);
        }
    }
}
