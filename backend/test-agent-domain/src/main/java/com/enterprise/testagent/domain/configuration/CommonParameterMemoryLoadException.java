package com.enterprise.testagent.domain.configuration;

import com.enterprise.testagent.domain.support.DomainValidation;

/** 内存参数读取或校验失败；message 必须是可安全展示且不含原始参数值的稳定原因。 */
public class CommonParameterMemoryLoadException extends RuntimeException {

    public CommonParameterMemoryLoadException(String safeMessage) {
        super(DomainValidation.requireText(safeMessage, "safeMessage").trim());
    }
}
