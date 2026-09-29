package com.enterprise.testagent.opencode.client;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * OpenCode V1 到 V2 的受控路由适配表。
 *
 * <p>运行时模块仍然使用平台稳定的内部路径，只有 client 模块知道原生 V2
 * 路由。这样可以避免把 generated SDK 的 DTO 和 V2 路径泄漏到业务模块。</p>
 */
final class OpencodeV2RouteMapper {

    private static final Pattern SESSION_PATH = Pattern.compile("^/session/([^/]+)(/.*)?$");
    private static final Pattern PROVIDER_PATH = Pattern.compile("^/provider/([^/]+)(/.*)?$");
    private static final Pattern AUTH_PATH = Pattern.compile("^/auth/([^/]+)$");
    private static final Pattern MCP_AUTH_PATH = Pattern.compile("^/mcp/([^/]+)/auth(?:/(.*))?$");

    private static final Map<String, String> EXACT = Map.ofEntries(
            Map.entry("/agent", "/api/agent"),
            Map.entry("/session", "/api/session"),
            Map.entry("/command", "/api/command"),
            Map.entry("/config", "/api/config"),
            Map.entry("/api/config", "/api/config"),
            Map.entry("/api/info", "/api/info"),
            Map.entry("/global/health", "/api/info"),
            Map.entry("/global/config", "/api/config"),
            Map.entry("/global/dispose", "/api/location/reload"),
            // V2 没有独立 LSP 查询；/api/config 仍包含当前 LSP 配置和状态投影。
            Map.entry("/lsp", "/api/config"),
            // V2 没有旧工具目录 endpoint，由受管插件 RPC 返回当前注册工具。
            Map.entry("/experimental/tool", "/api/rpc/testagent.runtime/tools"),
            Map.entry("/experimental/tool/ids", "/api/rpc/testagent.runtime/tools"),
            Map.entry("/session/status", "/api/session/active"),
            Map.entry("/provider/auth", "/api/integration"),
            Map.entry("/file", "/api/fs/list"),
            Map.entry("/find/file", "/api/fs/find"),
            Map.entry("/file/content", "/api/fs/read"),
            Map.entry("/vcs/status", "/api/vcs/status"),
            Map.entry("/vcs/diff", "/api/vcs/diff"),
            Map.entry("/mcp", "/api/mcp"),
            Map.entry("/experimental/resource", "/api/mcp/resource"),
            Map.entry("/experimental/worktree", "/api/worktree"),
            Map.entry("/experimental/worktree/reset", "/api/worktree/refresh"),
            Map.entry("/permission", "/api/permission/request"),
            Map.entry("/question", "/api/form"),
            Map.entry("/provider", "/api/provider"),
            Map.entry("/model", "/api/model"),
            Map.entry("/reference", "/api/reference"),
            Map.entry("/pty", "/api/pty"),
            Map.entry("/shell", "/api/shell"),
            Map.entry("/worktree", "/api/worktree"));

    private OpencodeV2RouteMapper() {}

    static String map(String path) {
        if (path == null || path.isBlank() || path.startsWith("/api/")) {
            return path;
        }
        String exact = EXACT.get(path);
        if (exact != null) {
            return exact;
        }
        Matcher matcher = SESSION_PATH.matcher(path);
        if (matcher.matches()) {
            String id = matcher.group(1);
            String suffix = matcher.group(2);
            if (suffix == null || suffix.isBlank()) {
                return "/api/session/" + id;
            }
            return switch (suffix) {
                case "/abort" -> "/api/session/" + id + "/interrupt";
                case "/summarize", "/compact" -> "/api/session/" + id + "/compact";
                case "/wait" -> "/api/experimental/session/" + id + "/wait";
                case "/revert" -> "/api/session/" + id + "/revert/stage";
                case "/unrevert" -> "/api/session/" + id + "/revert";
                case "/fork" -> "/api/session/" + id + "/fork";
                case "/message" -> "/api/session/" + id + "/message";
                case "/diff" -> "/api/session/" + id + "/diff";
                case "/command" -> "/api/session/" + id + "/command";
                case "/shell" -> "/api/session/" + id + "/shell";
                case "/permission" -> "/api/session/" + id + "/permission";
                case "/question" -> "/api/session/" + id + "/form";
                // V2 children 使用 /api/session?parentID=...，调用方负责补 query。
                case "/children" -> "/api/session";
                default -> "/api/session/" + id + suffix;
            };
        }
        matcher = PROVIDER_PATH.matcher(path);
        if (matcher.matches()) {
            String providerId = matcher.group(1);
            String suffix = matcher.group(2);
            if ("/oauth/authorize".equals(suffix) || "/oauth/callback".equals(suffix)) {
                return "/api/integration/" + providerId + "/connect/oauth";
            }
            return "/api/integration/" + providerId + (suffix == null ? "" : suffix);
        }
        matcher = AUTH_PATH.matcher(path);
        if (matcher.matches()) {
            return "/api/integration/" + matcher.group(1) + "/connect/key";
        }
        matcher = MCP_AUTH_PATH.matcher(path);
        if (matcher.matches()) {
            String server = matcher.group(1);
            String suffix = matcher.group(2);
            if ("auth".equals(suffix)) {
                return "/api/experimental/mcp/" + server + "/connect";
            }
            if (suffix == null || suffix.isBlank()) {
                return "/api/experimental/mcp/" + server + "/connect";
            }
            if ("callback".equals(suffix) || "authenticate".equals(suffix)) {
                return "/api/experimental/mcp/" + server + "/connect";
            }
        }
        if (path.startsWith("/mcp/") && path.endsWith("/auth/disconnect")) {
            String server = path.substring("/mcp/".length(), path.length() - "/auth/disconnect".length());
            return "/api/experimental/mcp/" + server + "/disconnect";
        }
        return path;
    }
}
