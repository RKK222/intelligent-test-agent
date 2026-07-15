package com.icbc.testagent.api.web.platform;

import com.icbc.testagent.api.web.common.AuthWebSupport;
import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.auth.AuthPrincipal;
import com.icbc.testagent.domain.dictionary.Dictionary;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Agent 配置进度 ticket 签发服务，在 HTTP 阶段绑定用户和 operationId。
 */
@Service
class AgentConfigOperationTicketService {

    private static final Pattern OPERATION_ID_PATTERN = Pattern.compile("^aco_[A-Za-z0-9_-]{8,128}$");
    private static final String WS_BASE = "/api/internal/platform/workspace-management/agent-config/operations/";

    private final AgentConfigOperationTicketStore ticketStore;
    private final CurrentBackendWebSocketUrlFactory webSocketUrlFactory;

    AgentConfigOperationTicketService(
            AgentConfigOperationTicketStore ticketStore,
            CurrentBackendWebSocketUrlFactory webSocketUrlFactory) {
        this.ticketStore = Objects.requireNonNull(ticketStore, "ticketStore must not be null");
        this.webSocketUrlFactory = Objects.requireNonNull(webSocketUrlFactory, "webSocketUrlFactory must not be null");
    }

    AgentConfigDtos.TicketResponse createTicket(AuthPrincipal principal, String operationId, String traceId) {
        String normalizedOperationId = normalizeOperationId(operationId);
        AgentConfigOperationTicket ticket = ticketStore.issue(
                normalizedOperationId,
                principal.userId().value(),
                AuthWebSupport.hasRole(principal, Dictionary.ROLE_SUPER_ADMIN),
                traceId);
        return new AgentConfigDtos.TicketResponse(
                ticket.ticket(),
                ticket.expiresAt(),
                webSocketUrlFactory.absoluteUrl(WS_BASE + normalizedOperationId + "/ws?ticket=" + ticket.ticket()));
    }

    AgentConfigOperationTicket consume(String ticket, String origin) {
        return ticketStore.consume(ticket, origin);
    }

    private String normalizeOperationId(String operationId) {
        if (operationId == null || operationId.isBlank() || !OPERATION_ID_PATTERN.matcher(operationId.trim()).matches()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "operationId 格式无效", Map.of("operationId", operationId));
        }
        return operationId.trim();
    }
}
