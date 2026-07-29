package com.enterprise.testagent.integration.lobehub;

/** 生成 256-bit 不可预测的票据或模型委托。 */
public interface LobehubTokenFactory {

    /** 返回不含填充字符的 Base64URL token。 */
    String newToken();
}
