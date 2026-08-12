package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.system.management.localclient.LocalClientInstanceApplicationService;
import com.enterprise.testagent.system.management.localclient.LocalClientInstanceResponses;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** 当前用户全部本地客户端实例列表。 */
@RestController
public class LocalClientInstanceController {

    private static final String PATH =
            "/api/internal/platform/local-opencode-client/instances/me";
    private final LocalClientInstanceApplicationService service;

    public LocalClientInstanceController(LocalClientInstanceApplicationService service) {
        this.service = service;
    }

    @GetMapping(PATH)
    public ApiResponse<List<LocalClientInstanceResponses.InstanceView>> list(ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        return ApiResponse.ok(
                service.list(AuthWebSupport.getAuthPrincipal(exchange).userId()),
                traceId);
    }
}
