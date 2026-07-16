package com.enterprise.testagent.opencode.runtime.process;

/**
 * 管理进程启动 opencode server 后返回的最小结果。
 */
public record OpencodeProcessStartResult(Long pid, String message) {
}
