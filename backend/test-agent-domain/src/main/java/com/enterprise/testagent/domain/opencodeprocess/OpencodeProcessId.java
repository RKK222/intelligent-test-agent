package com.enterprise.testagent.domain.opencodeprocess;

import com.enterprise.testagent.domain.support.DomainValidation;

/**
 * opencode server 进程 ID，使用 ocp_ 前缀。
 */
public record OpencodeProcessId(String value) {

    public OpencodeProcessId {
        value = DomainValidation.requirePrefixedId(value, "ocp_", "opencodeProcessId");
    }

    @Override
    public String toString() {
        return value;
    }
}
