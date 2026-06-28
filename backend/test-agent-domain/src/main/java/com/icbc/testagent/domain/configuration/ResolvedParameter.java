package com.icbc.testagent.domain.configuration;

/**
 * 通用参数解析后的视图；同时携带原始参数、展开值与解析状态，供管理端展示与每进程加载快照使用。
 *
 * @param parameter        原始通用参数（值未展开）
 * @param resolvedValue    展开后的值；解析失败时可能仍残留 {@code ${...}} 字面占位符
 * @param hasReference     原始值是否包含变量引用
 * @param resolutionError  解析错误描述；为 null 表示无错误
 */
public record ResolvedParameter(
        CommonParameter parameter,
        String resolvedValue,
        boolean hasReference,
        String resolutionError) {
}
