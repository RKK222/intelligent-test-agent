package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.api.web.common.AuthWebSupport;
import com.enterprise.testagent.domain.appsource.AppSourceOperationId;
import com.enterprise.testagent.domain.auth.AuthPrincipal;
import com.enterprise.testagent.domain.dictionary.Dictionary;
import com.enterprise.testagent.domain.opencodeprocess.BackendInstanceIdentity;
import com.enterprise.testagent.workspace.AppSourceApplicationService;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

/**
 * 应用源码进度 ticket 签发服务。
 *
 * <p>签发前通过业务服务执行一次实时授权；WebSocket 建连后仍会再次读取操作以防成员关系变化。
 */
@Service
class AppSourceOperationTicketService {

    private static final String WS_BASE =
            "/api/internal/platform/workspace-management/app-source-operations/";

    private final AppSourceOperationTicketStore ticketStore;
    private final AppSourceApplicationService appSources;
    private final CurrentBackendWebSocketUrlFactory webSocketUrls;
    private final BackendInstanceIdentity backendIdentity;

    AppSourceOperationTicketService(
            AppSourceOperationTicketStore ticketStore,
            AppSourceApplicationService appSources,
            CurrentBackendWebSocketUrlFactory webSocketUrls,
            BackendInstanceIdentity backendIdentity) {
        this.ticketStore = Objects.requireNonNull(ticketStore, "ticketStore must not be null");
        this.appSources = Objects.requireNonNull(appSources, "appSources must not be null");
        this.webSocketUrls = Objects.requireNonNull(webSocketUrls, "webSocketUrls must not be null");
        this.backendIdentity = Objects.requireNonNull(backendIdentity, "backendIdentity must not be null");
    }

    AppSourceDtos.TicketResponse createTicket(
            AuthPrincipal principal,
            String operationId,
            String origin,
            String traceId) {
        String normalizedOperationId = AppSourceOperationId.normalize(operationId);
        String canonicalOrigin = AppSourceWebSocketOrigin.canonicalize(origin);
        boolean appAdmin = AuthWebSupport.hasRole(principal, Dictionary.ROLE_APP_ADMIN);
        appSources.getOperation(normalizedOperationId, principal.userId(), appAdmin);
        AppSourceOperationTicket ticket = ticketStore.issue(
                normalizedOperationId,
                principal.userId().value(),
                appAdmin,
                backendIdentity.backendProcessId(),
                canonicalOrigin,
                traceId);
        String path = WS_BASE
                + UriUtils.encodePathSegment(normalizedOperationId, java.nio.charset.StandardCharsets.UTF_8)
                + "/ws?ticket=" + ticket.ticket();
        return new AppSourceDtos.TicketResponse(
                ticket.ticket(),
                ticket.expiresAt(),
                webSocketUrls.absoluteUrl(path));
    }

    AppSourceOperationTicket consume(
            String ticket,
            String expectedOperationId,
            String origin) {
        return ticketStore.consume(
                ticket,
                AppSourceOperationId.normalize(expectedOperationId),
                backendIdentity.backendProcessId(),
                AppSourceWebSocketOrigin.canonicalize(origin));
    }

}
