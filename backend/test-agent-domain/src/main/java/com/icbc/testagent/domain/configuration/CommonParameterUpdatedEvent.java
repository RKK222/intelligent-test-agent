package com.icbc.testagent.domain.configuration;

/**
 * 通用参数 value 更新事件，由通用参数管理服务在更新事务提交后发布，
 * 供需要据此联动（如向 opencode manager 下发运行时配置）的模块监听，解耦模块依赖。
 *
 * @param englishName  参数英文名，消费方按需过滤
 * @param platform     参数平台
 * @param newValue     新值
 * @param parameterId  参数业务 ID
 * @param traceId      链路 traceId
 */
public record CommonParameterUpdatedEvent(
        String englishName,
        ParameterPlatform platform,
        String newValue,
        String parameterId,
        String traceId) {
}
