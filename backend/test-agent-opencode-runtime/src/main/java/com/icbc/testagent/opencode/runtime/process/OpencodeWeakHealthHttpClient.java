package com.icbc.testagent.opencode.runtime.process;

/**
 * 弱健康检查的 HTTP 端口，便于单测验证不经过 manager 强健康链路。
 */
public interface OpencodeWeakHealthHttpClient {

    /**
     * 访问 opencode server 的 /global/health；baseUrl 不包含具体 path。
     */
    OpencodeWeakHealthHttpResult check(String baseUrl, String traceId);
}
