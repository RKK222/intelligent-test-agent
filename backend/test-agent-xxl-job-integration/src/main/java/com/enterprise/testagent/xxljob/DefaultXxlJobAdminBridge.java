package com.enterprise.testagent.xxljob;

import com.enterprise.testagent.domain.auth.TokenSessionMarkerStore;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** 主上下文桥接实现，只暴露票据消费与摘要 marker 校验。 */
@Component
public class DefaultXxlJobAdminBridge implements XxlJobAdminBridge {

    private final XxlJobSsoTicketService ticketService;
    private final TokenSessionMarkerStore markerStore;

    public DefaultXxlJobAdminBridge(
            XxlJobSsoTicketService ticketService,
            TokenSessionMarkerStore markerStore) {
        this.ticketService = Objects.requireNonNull(ticketService, "ticketService must not be null");
        this.markerStore = Objects.requireNonNull(markerStore, "markerStore must not be null");
    }

    @Override
    public Optional<XxlJobSsoIdentity> consumeTicket(String ticket) {
        return ticketService.consume(ticket);
    }

    @Override
    public boolean isPlatformSessionActive(String sessionDigest) {
        return markerStore.isActive(sessionDigest);
    }
}
