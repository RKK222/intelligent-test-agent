package com.example.testagent.domain.node;

import com.example.testagent.domain.support.DomainValidation;

/**
 * 执行节点 ID，后续路由和健康检查围绕该值对象传递。
 */
public record ExecutionNodeId(String value) {

    public ExecutionNodeId {
        value = DomainValidation.requirePrefixedId(value, "node_", "executionNodeId");
    }

    @Override
    public String toString() {
        return value;
    }
}
