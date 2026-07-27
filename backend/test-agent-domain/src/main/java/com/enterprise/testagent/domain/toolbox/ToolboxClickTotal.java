package com.enterprise.testagent.domain.toolbox;

import java.time.Instant;

/** 工具累计点击投影；零点击工具不会强制产生数据库行。 */
public record ToolboxClickTotal(String toolId, long clickCount, Instant lastCountedAt) {

    /** 校验累计数和工具身份。 */
    public ToolboxClickTotal {
        if (toolId == null || toolId.isBlank()) {
            throw new IllegalArgumentException("toolId must not be blank");
        }
        if (clickCount < 0) {
            throw new IllegalArgumentException("clickCount must not be negative");
        }
    }
}
