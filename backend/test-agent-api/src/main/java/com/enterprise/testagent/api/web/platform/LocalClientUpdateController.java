package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.notification.UserNotificationId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.opencode.runtime.localclient.LocalClientUpdateCoordinator;
import java.util.Map;
import java.util.Objects;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** 当前用户从站内信触发单个自有实例静默切换。 */
@RestController
public class LocalClientUpdateController {

    private static final String PATH =
            "/api/internal/platform/local-opencode-client/instances/{clientInstanceId}/updates";
    private final LocalClientUpdateCoordinator updates;

    public LocalClientUpdateController(LocalClientUpdateCoordinator updates) {
        this.updates = Objects.requireNonNull(updates);
    }

    @PostMapping(PATH)
    public ApiResponse<LocalClientVersionManagementDtos.RolloutView> update(
            @PathVariable String clientInstanceId,
            @RequestBody LocalClientVersionManagementDtos.UserUpdateRequest request,
            ServerWebExchange exchange) {
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        String traceId = RuntimeApiSupport.traceId(exchange);
        try {
            LocalClientUpdateCoordinator.RolloutResult result = updates.createUserRequestedUpdate(
                    userId,
                    new LocalClientInstanceId(clientInstanceId),
                    new UserNotificationId(request.notificationId()),
                    required(request.expectedTargetVersion(), "expectedTargetVersion"),
                    traceId);
            return ApiResponse.ok(
                    LocalClientVersionManagementDtos.rollout(result.rollout(), result.attemptCount()), traceId);
        } catch (IllegalArgumentException exception) {
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR, "本地客户端更新请求无效", Map.of(), exception);
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.trim();
    }
}
