package com.icbc.testagent.domain.configuration;

/**
 * 通用参数缓存已刷新事件，由 {@code CommonParameterCacheRefresher} 在内存缓存 reload 完成后发布。
 *
 * <p>供需要"缓存已刷新后联动"的模块监听（如向本实例 opencode manager 下发运行配置），
 * 替代直接监听 {@link CommonParameterUpdatedEvent}：远端实例收到广播刷新缓存后也发布本事件，
 * 使每台实例都能在缓存刷新后向自己持有的 manager 下发，无需跨模块调用。
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
