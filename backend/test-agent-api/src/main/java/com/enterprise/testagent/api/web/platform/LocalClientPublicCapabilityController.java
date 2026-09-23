package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientPublicCapabilityCoordinator;
import java.util.Map;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** 当前用户确认指定实例、指定摘要的公共 Agent/Skill/Tool 更新。 */
@RestController
public class LocalClientPublicCapabilityController {

    private static final String PATH =
            "/api/internal/platform/local-opencode-client/instances/{clientInstanceId}/public-capabilities/updates";
    private final LocalClientPublicCapabilityCoordinator coordinator;

    public LocalClientPublicCapabilityController(LocalClientPublicCapabilityCoordinator coordinator) {
        this.coordinator = coordinator;
    }

    @PostMapping(PATH)
    public ApiResponse<LocalClientPublicCapabilityCoordinator.UpdateRequestResult> update(
            @PathVariable String clientInstanceId,
            @RequestBody UpdateRequest request,
            ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        String traceId = RuntimeApiSupport.traceId(exchange);
        try {
            return ApiResponse.ok(coordinator.requestUpdate(
                    userId,
                    new LocalClientInstanceId(clientInstanceId),
                    request.expectedBundleDigest(),
                    Boolean.TRUE.equals(request.confirmedDiscardPersonalChanges()),
                    traceId), traceId);
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR, "公共能力更新请求无效", Map.of(), exception);
        }
    }

    public record UpdateRequest(
            String expectedBundleDigest,
            Boolean confirmedDiscardPersonalChanges) {

        public UpdateRequest(String expectedBundleDigest) {
            this(expectedBundleDigest, Boolean.FALSE);
        }
    }
}
