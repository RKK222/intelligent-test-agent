package com.enterprise.testagent.integration.toolbox;

/** 点击上报业务结果。 */
public record ToolboxClickResult(String toolId, long clickCount, boolean recorded, boolean incremented) {
}
