package com.icbc.testagent.domain.node;

import com.icbc.testagent.domain.support.DomainValidation;

/**
 * 执行节点 ID，后续路由和健康检查围绕该值对象传递。
 */
public record ExecutionNodeId(String value) {

    /**
     * 校验执行节点 ID 前缀。
     */
    public ExecutionNodeId {
        value = DomainValidation.requirePrefixedId(value, "node_", "executionNodeId");
    }

    /**
     * 返回原始执行节点 ID 字符串，便于日志和持久化参数使用。
     */
    @Override
    public String toString() {
        return value;
    }
}
