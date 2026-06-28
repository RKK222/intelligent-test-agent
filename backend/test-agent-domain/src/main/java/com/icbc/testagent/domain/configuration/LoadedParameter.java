package com.icbc.testagent.domain.configuration;

/**
 * 通用参数加载快照中的单个参数项；同时记录原始值与展开值，便于管理端核对各进程实际加载结果。
 *
 * @param englishName       参数英文名
 * @param platform          参数平台（小写稳定值）
 * @param rawValue          原始值（未展开）
 * @param resolvedValue     展开后的值；解析失败时可能仍残留 {@code ${...}} 字面占位符
 * @param hasReference      原始值是否包含变量引用
 * @param resolutionError   解析错误描述；为 null 表示无错误
 */
public record LoadedParameter(
        String englishName,
        String platform,
        String rawValue,
        String resolvedValue,
        boolean hasReference,
        String resolutionError) {
}
