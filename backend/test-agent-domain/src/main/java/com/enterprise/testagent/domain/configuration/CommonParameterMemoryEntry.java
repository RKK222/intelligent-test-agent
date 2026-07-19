package com.enterprise.testagent.domain.configuration;

/**
 * 显式 JVM 内存通用参数 SPI。
 *
 * <p>实现必须先完整读取并校验数据库值，再原子替换自己的业务快照；失败时抛出
 * {@link CommonParameterMemoryLoadException} 并保持上一份业务值不变。未实现本接口的通用参数继续直读数据库。
 */
public interface CommonParameterMemoryEntry {

    /** 返回唯一的参数英文名与平台键。 */
    CommonParameterMemoryKey key();

    /** 从数据库读取、校验并原子更新业务内存，成功后返回源值和实际生效值。 */
    CommonParameterMemoryLoadedValue reloadFromDatabase();
}
