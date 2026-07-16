package com.enterprise.testagent.domain.opencodeprocess;

import com.enterprise.testagent.domain.support.DomainValidation;

/**
 * opencode 容器 ID，保存 Docker 容器或管理进程注册时提供的稳定标识。
 */
public record OpencodeContainerId(String value) {

    public OpencodeContainerId {
        value = DomainValidation.requireText(value, "containerId");
    }

    @Override
    public String toString() {
        return value;
    }
}
