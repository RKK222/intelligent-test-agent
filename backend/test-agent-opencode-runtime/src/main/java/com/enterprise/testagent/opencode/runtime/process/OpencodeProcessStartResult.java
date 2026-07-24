package com.enterprise.testagent.opencode.runtime.process;

import java.time.Instant;

/**
 * 管理进程启动 opencode server 后返回的最小结果。
 */
public record OpencodeProcessStartResult(
        Long pid,
        String message,
        Boolean processCreated,
        Instant startedAt) {

    /** 兼容只增加 processCreated 的调用；缺少 manager 启动时间时由公共启动服务失败关闭。 */
    public OpencodeProcessStartResult(Long pid, String message, Boolean processCreated) {
        this(pid, message, processCreated, null);
    }

    /** 兼容旧 manager/test double；缺少显式创建语义时保持 null，禁止推断为 fresh。 */
    public OpencodeProcessStartResult(Long pid, String message) {
        this(pid, message, null, null);
    }
}
