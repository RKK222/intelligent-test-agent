package com.enterprise.testagent.api.web.platform;

/** 引用资产库 HTTP 请求 DTO。 */
public final class ReferenceRepositoryDtos {

    private ReferenceRepositoryDtos() {
    }

    /** 首次初始化分支请求；分支语义与安全校验由业务服务统一处理。 */
    public record InitializeRequest(String branch) {
    }

    /** 受控切换目标分支请求。 */
    public record SwitchBranchRequest(String branch) {
    }
}
