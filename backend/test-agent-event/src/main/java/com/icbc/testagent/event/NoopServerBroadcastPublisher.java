package com.icbc.testagent.event;

import com.icbc.testagent.domain.broadcast.ServerBroadcastEvent;
import com.icbc.testagent.domain.broadcast.ServerBroadcastPublisher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 服务器广播默认空实现；单机或未开启 Redis 时业务仍可记录本机副本。
 */
@Component
@ConditionalOnProperty(prefix = "test-agent.server-broadcast", name = "enabled", havingValue = "false", matchIfMissing = true)
public final class NoopServerBroadcastPublisher implements ServerBroadcastPublisher {

    public static final NoopServerBroadcastPublisher INSTANCE = new NoopServerBroadcastPublisher();

    public NoopServerBroadcastPublisher() {}

    @Override
    public void publish(ServerBroadcastEvent event) {
        // 广播是多服务器增强通道，未启用时不影响本机主流程。
    }
}
