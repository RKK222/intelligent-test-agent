package com.enterprise.testagent.api.web.external;

import com.enterprise.testagent.api.web.common.ExternalApiWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.externalapi.ExternalApiPrincipal;
import com.enterprise.testagent.integration.externalapi.ExternalUserSshKeyApplicationService;
import java.util.Objects;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** 外部服务端调用入口；成功正文只包含 TAEK1 加密 envelope。 */
@RestController
@RequestMapping("/api/external/v1")
public class ExternalUserSshKeyController {

    private final ExternalUserSshKeyApplicationService service;

    public ExternalUserSshKeyController(ExternalUserSshKeyApplicationService service) {
        this.service = Objects.requireNonNull(service, "service must not be null");
    }

    @GetMapping("/users/{unifiedAuthId}/ssh-key")
    public ApiResponse<Object> get(
            @PathVariable("unifiedAuthId") String unifiedAuthId,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        ExternalApiPrincipal principal = ExternalApiWebSupport.getPrincipal(exchange);
        return ApiResponse.ok(service.get(unifiedAuthId, principal, traceId), traceId);
    }
}
