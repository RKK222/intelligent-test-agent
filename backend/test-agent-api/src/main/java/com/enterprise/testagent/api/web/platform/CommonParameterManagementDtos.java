package com.enterprise.testagent.api.web.platform;

/**
 * 通用参数管理 HTTP 请求 DTO；响应直接复用业务模块的
 * {@code CommonParameterManagementResponses.CommonParameterResponse}，
 * 该响应已剥离数据库代理主键，可作为平台 DTO 暴露。
 */
final class CommonParameterManagementDtos {

    private CommonParameterManagementDtos() {
    }

    /**
     * 仅更新 value 的请求体；value 为空将由业务层校验为 VALIDATION_ERROR。
     */
    record UpdateValueRequest(String value) {
    }
}
