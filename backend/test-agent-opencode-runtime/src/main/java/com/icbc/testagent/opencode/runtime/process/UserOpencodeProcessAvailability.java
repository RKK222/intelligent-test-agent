package com.icbc.testagent.opencode.runtime.process;

/**
 * 当前用户 opencode 进程可用性，供前端决定是否允许发起对话。
 */
public enum UserOpencodeProcessAvailability {
    READY,
    NEEDS_INITIALIZATION,
    UNAVAILABLE
}
