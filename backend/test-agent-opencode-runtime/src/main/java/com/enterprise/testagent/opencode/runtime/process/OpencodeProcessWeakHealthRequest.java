package com.enterprise.testagent.opencode.runtime.process;

/**
 * 前端弱健康检查请求；字段必须来自最近一次 /processes/me 返回的进程分配信息。
 */
public record OpencodeProcessWeakHealthRequest(
        String linuxServerId,
        String containerId,
        int port) {
}
