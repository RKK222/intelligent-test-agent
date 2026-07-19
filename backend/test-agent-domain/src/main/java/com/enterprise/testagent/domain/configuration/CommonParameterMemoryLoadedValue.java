package com.enterprise.testagent.domain.configuration;

import java.util.Objects;

/**
 * 一次成功加载得到的源值与内存生效值。
 *
 * @param sourceValue 从数据库读取并完成引用展开后的值
 * @param memoryValue 业务组件校验、规范化后实际保存在 JVM 中的值
 */
public record CommonParameterMemoryLoadedValue(String sourceValue, String memoryValue) {

    public CommonParameterMemoryLoadedValue {
        sourceValue = Objects.requireNonNull(sourceValue, "sourceValue must not be null");
        memoryValue = Objects.requireNonNull(memoryValue, "memoryValue must not be null");
    }
}
