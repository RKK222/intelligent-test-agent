package com.enterprise.testagent.opencode.runtime.process;

/**
 * 直接访问 opencode /api/info 的轻量结果。
 */
public record OpencodeWeakHealthHttpResult(boolean healthy, String message) {
}
