package com.enterprise.testagent.domain.run;

import com.enterprise.testagent.domain.support.DomainValidation;

/** 一次原生撤销重发操作的持久标识。 */
public record RunResendId(String value) {

    public RunResendId {
        value = DomainValidation.requirePrefixedId(value, "rsd_", "runResendId");
    }

    @Override
    public String toString() {
        return value;
    }
}
