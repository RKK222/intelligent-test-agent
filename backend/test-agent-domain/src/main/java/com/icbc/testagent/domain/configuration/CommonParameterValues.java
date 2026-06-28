package com.icbc.testagent.domain.configuration;

import java.util.List;
import java.util.Optional;

/**
 * 通用参数只读缓存视图；消费方通过该端口读取启动时加载到内存的通用参数，并获得变量引用展开后的值。
 *
 * <p>语义与原 {@link CommonParameterRepository#findByEnglishNameAndPlatform} 一致：
 * 先按指定平台读取，缺失回退 ALL；{@link #resolvedValue(String)} 默认按当前 JVM 平台上下文解析。
 * 读操作不查库，命中内存快照；变量引用 {@code ${englishName}} 由解析器在读取时展开。
 */
public interface CommonParameterValues {

    /**
     * 按当前 JVM 平台读取并展开参数值；缺失或空白返回 empty。
     */
    Optional<String> resolvedValue(String englishName);

    /**
     * 按指定平台上下文读取并展开参数值；先精确匹配该平台，缺失回退 ALL。
     */
    Optional<String> resolvedValue(String englishName, ParameterPlatform platform);

    /**
     * 按指定平台读取原始参数（值未展开）；先精确匹配该平台，缺失回退 ALL。
     */
    Optional<CommonParameter> raw(String englishName, ParameterPlatform platform);

    /**
     * 列出全部原始通用参数（值未展开），供管理端列表展示；结果按英文名、平台稳定排序。
     */
    List<CommonParameter> findAll();

    /**
     * 列出全部参数的解析视图（原始值 + 展开值 + 解析状态），供每进程加载快照写入。
     */
    List<ResolvedParameter> resolvedAll();
}
