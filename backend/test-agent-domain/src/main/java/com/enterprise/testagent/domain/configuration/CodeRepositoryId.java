package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.support.DomainValidation;

/**
 * 代码库配置 ID，使用 repo_ 前缀。
 */
public record CodeRepositoryId(String value) {

    public CodeRepositoryId {
        value = DomainValidation.requirePrefixedId(value, "repo_", "repositoryId");
    }

    @Override
    public String toString() {
        return value;
    }
}
