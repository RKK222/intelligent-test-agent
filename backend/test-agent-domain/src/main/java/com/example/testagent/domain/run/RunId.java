package com.example.testagent.domain.run;

import com.example.testagent.domain.support.DomainValidation;

/**
 * Run 领域 ID，所有运行状态、事件和路由决策都围绕该 ID 串联。
 */
public record RunId(String value) {

    /**
     * 校验运行 ID 前缀。
     */
    public RunId {
        value = DomainValidation.requirePrefixedId(value, "run_", "runId");
    }

    /**
     * 返回原始 runId 字符串，便于日志、事件和错误详情使用。
     */
    @Override
    public String toString() {
        return value;
    }
}
