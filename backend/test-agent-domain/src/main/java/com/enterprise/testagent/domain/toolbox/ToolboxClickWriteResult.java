package com.enterprise.testagent.domain.toolbox;

/** 一次点击事务的持久化结果，区分事件幂等和计数窗口竞争结果。 */
public record ToolboxClickWriteResult(long clickCount, boolean recorded, boolean incremented) {

    /** 累计点击量不得为负数。 */
    public ToolboxClickWriteResult {
        if (clickCount < 0) {
            throw new IllegalArgumentException("clickCount must not be negative");
        }
    }
}
