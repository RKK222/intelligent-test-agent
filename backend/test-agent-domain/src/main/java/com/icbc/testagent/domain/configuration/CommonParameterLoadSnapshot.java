package com.icbc.testagent.domain.configuration;

import java.time.Instant;
import java.util.List;

/**
 * 单个后端 Java 进程加载的通用参数快照；由每台实例把自己的内存缓存写入共享存储，供管理端聚合展示。
 *
 * @param backendProcessId  后端 Java 进程业务 ID
 * @param linuxServerId     所在 Linux 服务器 ID
 * @param listenUrl         后端监听地址
 * @param instanceId        发布实例 ID（广播身份）
 * @param loadedAt          本次加载时间
 * @param parameters        已加载的参数列表（含原始值与展开值）
 */
public record CommonParameterLoadSnapshot(
        String backendProcessId,
        String linuxServerId,
        String listenUrl,
        String instanceId,
        Instant loadedAt,
        List<LoadedParameter> parameters) {
}
