package com.enterprise.testagent.xxljob;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * XXL-JOB 独立 Admin、executor 与 SSO 配置。敏感字段只向子上下文传递，不写入日志或健康详情。
 */
@Component
@ConfigurationProperties(prefix = "test-agent.xxl-job")
public class XxlJobProperties {

    private boolean enabled;
    private final Admin admin = new Admin();
    private final Mysql mysql = new Mysql();
    private final Executor executor = new Executor();
    private final Sso sso = new Sso();
    private String accessToken = "";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Admin getAdmin() {
        return admin;
    }

    public Mysql getMysql() {
        return mysql;
    }

    public Executor getExecutor() {
        return executor;
    }

    public Sso getSso() {
        return sso;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken == null ? "" : accessToken.trim();
    }

    public static final class Admin {
        private int port = 18080;
        private String contextPath = "/xxl-job-admin";
        private Duration retryInitialDelay = Duration.ofSeconds(5);
        private Duration retryMaxDelay = Duration.ofSeconds(60);

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = validPort(port, "admin.port");
        }

        public String getContextPath() {
            return contextPath;
        }

        public void setContextPath(String contextPath) {
            String value = requireText(contextPath, "admin.contextPath");
            this.contextPath = value.startsWith("/") ? value : "/" + value;
        }

        public Duration getRetryInitialDelay() {
            return retryInitialDelay;
        }

        public void setRetryInitialDelay(Duration retryInitialDelay) {
            this.retryInitialDelay = positive(retryInitialDelay, "admin.retryInitialDelay");
        }

        public Duration getRetryMaxDelay() {
            return retryMaxDelay;
        }

        public void setRetryMaxDelay(Duration retryMaxDelay) {
            this.retryMaxDelay = positive(retryMaxDelay, "admin.retryMaxDelay");
        }
    }

    public static final class Mysql {
        private String url = "jdbc:mysql://127.0.0.1:13306/xxl_job?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai";
        private String username = "xxl_job";
        private String password = "";

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            String value = requireText(url, "mysql.url");
            if (!value.startsWith("jdbc:mysql:")) {
                throw new IllegalArgumentException("mysql.url must use jdbc:mysql");
            }
            this.url = value;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = requireText(username, "mysql.username");
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password == null ? "" : password;
        }
    }

    public static final class Executor {
        private String appName = "test-agent-backend";
        private int port = 9999;
        private String logPath = "logs/xxl-job";
        private int logRetentionDays = 30;

        public String getAppName() {
            return appName;
        }

        public void setAppName(String appName) {
            this.appName = requireText(appName, "executor.appName");
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = validPort(port, "executor.port");
        }

        public String getLogPath() {
            return logPath;
        }

        public void setLogPath(String logPath) {
            this.logPath = requireText(logPath, "executor.logPath");
        }

        public int getLogRetentionDays() {
            return logRetentionDays;
        }

        public void setLogRetentionDays(int logRetentionDays) {
            if (logRetentionDays < 1) {
                throw new IllegalArgumentException("executor.logRetentionDays must be positive");
            }
            this.logRetentionDays = logRetentionDays;
        }
    }

    public static final class Sso {
        private Duration ticketTtl = Duration.ofSeconds(60);

        public Duration getTicketTtl() {
            return ticketTtl;
        }

        public void setTicketTtl(Duration ticketTtl) {
            this.ticketTtl = positive(ticketTtl, "sso.ticketTtl");
        }
    }

    private static int validPort(int port, String field) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException(field + " must be between 1 and 65535");
        }
        return port;
    }

    private static Duration positive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
