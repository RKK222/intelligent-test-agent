package com.enterprise.testagent.integration.workflow;

/** 模型网关验证workflow短期委托后可使用的最小身份。 */
public record WorkflowModelIdentity(String userId, String unifiedAuthId, String scope) {
}
