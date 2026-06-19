package com.example.testagent.opencode.client;

/**
 * opencode 取消结果，只表达平台关心的取消是否被远端接受。
 */
public record OpencodeCancelResult(boolean cancelled) {
}
