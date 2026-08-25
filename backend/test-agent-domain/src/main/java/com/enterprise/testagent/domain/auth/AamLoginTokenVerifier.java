package com.enterprise.testagent.domain.auth;

/**
 * AAM 登录凭据验真端口。领域层只关心凭据是否有效，不感知外部响应结构和地址。
 */
public interface AamLoginTokenVerifier {

    /**
     * 验证 AAM 回调携带的用户号与 Token；失败时抛出稳定平台异常。
     */
    void verify(String userId, String token);
}
