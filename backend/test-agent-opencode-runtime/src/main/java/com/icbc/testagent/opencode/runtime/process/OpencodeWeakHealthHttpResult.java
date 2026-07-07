package com.icbc.testagent.opencode.runtime.process;

/**
 * 直接访问 opencode /global/health 的轻量结果。
 */
public record OpencodeWeakHealthHttpResult(boolean healthy, String message) {
}
