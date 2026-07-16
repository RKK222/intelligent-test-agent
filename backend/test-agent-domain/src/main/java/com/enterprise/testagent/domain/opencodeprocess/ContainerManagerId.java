package com.enterprise.testagent.domain.opencodeprocess;

import com.enterprise.testagent.domain.support.DomainValidation;

/**
 * 容器管理进程 ID，使用 mgr_ 前缀。
 */
public record ContainerManagerId(String value) {

    public ContainerManagerId {
        value = DomainValidation.requirePrefixedId(value, "mgr_", "containerManagerId");
    }

    @Override
    public String toString() {
        return value;
    }
}
