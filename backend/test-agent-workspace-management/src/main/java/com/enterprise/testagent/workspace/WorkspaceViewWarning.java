package com.enterprise.testagent.workspace;

/** 单个引用挂载失败的安全 warning；message 不包含物理路径。 */
public record WorkspaceViewWarning(String alias, String code, String message) {
}
