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

    /** 终止请求必须携带页面实际观察到的 generation，防止误伤随后发起的新操作。 */
    public record TerminateRequest(long expectedGeneration) {
    }
}
