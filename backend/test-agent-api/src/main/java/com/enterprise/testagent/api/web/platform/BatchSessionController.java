package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.api.web.common.RuntimeApiSupport;
import com.enterprise.testagent.common.api.ApiResponse;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.opencode.runtime.session.BatchContext;
import com.enterprise.testagent.opencode.runtime.session.BatchSessionApplicationService;
import jakarta.validation.Valid;
import java.util.Objects;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

/** 批量单项 Session HTTP 入口；当前用户身份只取认证上下文。 */
@RestController
public class BatchSessionController {

    private final BatchSessionApplicationService service;

    public BatchSessionController(BatchSessionApplicationService service) {
        this.service = Objects.requireNonNull(service);
    }

    /** 创建或幂等返回一个批量条目的独立 Session。 */
    @PostMapping("/api/internal/platform/opencode-runtime/sessions/batch-items")
    public ApiResponse<RuntimeDtos.SessionResponse> create(
            @Valid @RequestBody RuntimeDtos.CreateBatchItemSessionRequest request,
            ServerWebExchange exchange) {
        String traceId = RuntimeApiSupport.traceId(exchange);
        UserId userId = AuthWebSupport.getAuthPrincipal(exchange).userId();
        RuntimeDtos.BatchContextRequest batch = request.batchContext();
        var session = service.create(
                userId,
                new WorkspaceId(request.workspaceId()),
                request.title(),
                new BatchContext(batch.batchId(), batch.itemRequestId()),
                traceId);
        return ApiResponse.ok(RuntimeDtos.SessionResponse.from(session)
                .withRuntimeTarget(service.runtimeTarget(session.sessionId())), traceId);
    }
}
