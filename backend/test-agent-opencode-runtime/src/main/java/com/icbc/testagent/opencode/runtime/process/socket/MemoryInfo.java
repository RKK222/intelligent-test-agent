package com.icbc.testagent.opencode.runtime.process.socket;

/**
 * 系统内存信息数据结构。
 *
 * @param totalBytes     总物理内存字节数
 * @param availableBytes 可用内存字节数（包括空闲和可回收内存）
 */
record MemoryInfo(Long totalBytes, Long availableBytes) {
}