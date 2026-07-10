package com.icbc.testagent.domain.run;

/** 终态对话摘要的生成质量，供历史接口向前端解释内容是否完整。 */
public enum RunSummaryStatus {
    /** 摘要已完整生成且未发生截断。 */
    COMPLETE,
    /** 摘要经过确定性截断，只保留允许持久化的前缀。 */
    PARTIAL,
    /** 正常摘要生成失败，使用不含原文的安全兜底内容。 */
    FALLBACK
}
