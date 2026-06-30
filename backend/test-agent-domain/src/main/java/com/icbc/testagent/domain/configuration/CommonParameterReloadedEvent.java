package com.icbc.testagent.domain.configuration;

/**
 * 通用参数已更新事件，由 {@code CommonParameterUpdateBroadcaster} 在本地更新或远端广播后发布。
 *
 * <p>供需要跨实例联动的模块监听（如向本实例 opencode manager 下发运行配置）。监听方收到事件后
 * 通过 {@link CommonParameterValues} 从数据库读取最新值，不依赖 JVM 或 Redis 参数缓存。
 *
 * @param englishName       触发刷新的参数英文名；远端批量刷新时可能为 null
 * @param platform          触发刷新的参数平台
 * @param parameterId       触发刷新的参数业务 ID
 * @param traceId           链路 traceId
 * @param originInstanceId  发布实例 ID，便于监听方按需识别来源
 */
public record CommonParameterReloadedEvent(
        String englishName,
        ParameterPlatform platform,
        String parameterId,
        String traceId,
        String originInstanceId) {
}
