package com.enterprise.testagent.opencode.runtime.session;

/** 批量创建归因上下文；ID 由前端稳定生成，但最终仍由服务端校验。 */
public record BatchContext(String batchId, String itemRequestId) {

    private static final int MAX_ID_LENGTH = 128;

    public BatchContext {
        batchId = requiredId(batchId, "batchId");
        itemRequestId = requiredId(itemRequestId, "itemRequestId");
    }

    private static String requiredId(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        String normalized = value.trim();
        if (normalized.length() > MAX_ID_LENGTH) {
            throw new IllegalArgumentException(name + " must not exceed " + MAX_ID_LENGTH + " characters");
        }
        return normalized;
    }
}
