package com.enterprise.testagent.api.web.common;

/** 请求 DTO 可实现该接口，以低敏摘要替代完整正文进入 API 访问日志。 */
public interface ApiRequestLogSummary {

    /** 返回只包含低敏、定长或有界字段的日志摘要。 */
    Object apiRequestLogSummary();
}
