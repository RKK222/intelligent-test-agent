package com.enterprise.testagent.opencode.runtime.runtime;

/**
 * 旁路问答结果；compacted 表示临时 fork 是否先做过上下文压缩。
 */
public record SideQuestionResult(String answer, boolean compacted) {
}
