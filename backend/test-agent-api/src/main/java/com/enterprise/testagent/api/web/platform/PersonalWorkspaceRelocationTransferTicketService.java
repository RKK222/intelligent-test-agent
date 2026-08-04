package com.enterprise.testagent.api.web.platform;

import com.enterprise.testagent.workspace.PersonalWorkspaceRelocationReceiveService;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 在 HTTP 阶段校验权威搬迁状态并签发目标 Java 本机的一次性 WebSocket ticket。 */
@Service
class PersonalWorkspaceRelocationTransferTicketService {

    private final PersonalWorkspaceRelocationReceiveService receiveService;
    private final PersonalWorkspaceRelocationTransferTicketStore store;

    PersonalWorkspaceRelocationTransferTicketService(
            PersonalWorkspaceRelocationReceiveService receiveService,
            PersonalWorkspaceRelocationTransferTicketStore store) {
        this.receiveService = Objects.requireNonNull(receiveService);
        this.store = Objects.requireNonNull(store);
    }

    PersonalWorkspaceRelocationTransferDtos.TicketResponse issue(
            PersonalWorkspaceRelocationTransferDtos.TicketRequest request, String traceId) {
        receiveService.authorize(
                request.relocationId(),
                request.sourceLinuxServerId(),
                request.targetLinuxServerId(),
                request.snapshotSha256(),
                request.archiveSizeBytes());
        PersonalWorkspaceRelocationTransferTicket ticket = store.issue(request, traceId);
        return new PersonalWorkspaceRelocationTransferDtos.TicketResponse(
                ticket.ticket(),
                ticket.expiresAt(),
                PersonalWorkspaceRelocationTransferController.WEB_SOCKET_PATH);
    }

    PersonalWorkspaceRelocationTransferTicket consume(
            String ticket, String origin, String sourceLinuxServerId) {
        return store.consume(ticket, origin, sourceLinuxServerId);
    }
}
