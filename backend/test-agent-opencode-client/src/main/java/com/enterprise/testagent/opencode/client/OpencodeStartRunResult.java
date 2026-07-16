package com.enterprise.testagent.opencode.client;

/**
 * opencode 异步 prompt 接收结果，避免把 generated 204 响应细节暴露给应用层。
 */
public record OpencodeStartRunResult(boolean accepted) {
}
