package com.enterprise.testagent.domain.localclient;

import com.enterprise.testagent.domain.support.DomainValidation;

/** 本地客户端稳定实例 ID，使用 lci_ 前缀。 */
public record LocalClientInstanceId(String value) {

    public LocalClientInstanceId {
        value = DomainValidation.requirePrefixedId(value, "lci_", "clientInstanceId");
    }

    @Override
    public String toString() {
        return value;
    }
}
