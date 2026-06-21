package com.icbc.testagent.opencode.client;

/**
 * opencode 健康检查结果，避免向业务层暴露 generated SDK 的 health DTO。
 */
public record OpencodeHealthResult(boolean available, String baseUrl) {
}
