package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.support.DomainValidation;

/**
 * 个人 SSH key 配置 ID，使用 ssh_ 前缀。
 */
public record SshKeyId(String value) {

    public SshKeyId {
        value = DomainValidation.requirePrefixedId(value, "ssh_", "sshKeyId");
    }

    @Override
    public String toString() {
        return value;
    }
}
