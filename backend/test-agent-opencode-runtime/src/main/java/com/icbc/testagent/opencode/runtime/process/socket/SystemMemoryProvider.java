package com.icbc.testagent.opencode.runtime.process.socket;

/**
 * 系统内存信息获取策略接口。
 * 不同操作系统对"可用内存"的定义和获取方式不同，通过策略模式适配。
 */
interface SystemMemoryProvider {

    /**
     * 获取系统内存信息。
     *
     * @return 内存信息，获取失败返回 null
     */
    MemoryInfo getMemoryInfo();
}