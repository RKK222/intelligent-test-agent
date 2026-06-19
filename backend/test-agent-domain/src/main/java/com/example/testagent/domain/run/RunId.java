package com.example.testagent.domain.run;

import com.example.testagent.domain.support.DomainValidation;

/**
 * Run 领域 ID，所有运行状态、事件和路由决策都围绕该 ID 串联。
 */
public record RunId(String value) {

    public RunId {
        value = DomainValidation.requirePrefixedId(value, "run_", "runId");
    }

    @Override
    public String toString() {
        return value;
    }
}
