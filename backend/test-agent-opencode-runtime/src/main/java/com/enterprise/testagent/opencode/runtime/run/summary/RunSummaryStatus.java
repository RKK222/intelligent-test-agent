package com.enterprise.testagent.opencode.runtime.run.summary;

/**
 * 摘要生成状态：完整、因长度截断而部分可用，或清洗失败后的安全降级文本。
 */
public enum RunSummaryStatus {
    COMPLETE,
    PARTIAL,
    FALLBACK
}
