package com.enterprise.testagent.api.web.platform;

import java.util.Set;

/** API Key 管理 HTTP 请求模型；toolCode 仅在创建时出现，避免修改稳定身份。 */
final class ExternalApiCredentialDtos {

    private ExternalApiCredentialDtos() {
    }

    record CreateRequest(String toolCode, String toolName, Set<String> scopes, Boolean enabled) {
    }

    record UpdateRequest(String toolName, Set<String> scopes, Boolean enabled) {
    }
}
