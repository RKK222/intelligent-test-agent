package com.enterprise.testagent.domain.opencodeprocess;

import com.enterprise.testagent.domain.support.DomainValidation;

/**
 * 后端 Java 进程 ID，使用 bjp_ 前缀。
 */
public record BackendProcessId(String value) {

    public BackendProcessId {
        value = DomainValidation.requirePrefixedId(value, "bjp_", "backendProcessId");
    }

    @Override
    public String toString() {
        return value;
    }
}
