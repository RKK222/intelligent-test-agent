package com.enterprise.testagent.opencode.runtime.process;

/**
 * 管理进程启动 opencode server 后返回的最小结果。
 */
public record OpencodeProcessStartResult(Long pid, String message, Boolean processCreated) {

    /** 兼容旧 manager/test double；缺少显式创建语义时保持 null，禁止推断为 fresh。 */
    public OpencodeProcessStartResult(Long pid, String message) {
        this(pid, message, null);
    }
}
