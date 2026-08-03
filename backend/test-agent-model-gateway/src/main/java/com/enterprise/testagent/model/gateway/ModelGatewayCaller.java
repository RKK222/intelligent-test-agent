package com.enterprise.testagent.model.gateway;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;

/** 模型委托完成鉴权后形成的可信调用身份，浏览器请求头不得直接构造该对象。 */
public record ModelGatewayCaller(
        String userId,
        String unifiedAuthId,
        String sourceClient) {

    public ModelGatewayCaller {
        userId = requireText(userId, "模型调用用户不能为空");
        unifiedAuthId = requireText(unifiedAuthId, "模型调用统一认证标识不能为空");
        sourceClient = requireText(sourceClient, "模型调用来源不能为空");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new PlatformException(ErrorCode.UNAUTHENTICATED, message);
        }
        return value.trim();
    }
}
