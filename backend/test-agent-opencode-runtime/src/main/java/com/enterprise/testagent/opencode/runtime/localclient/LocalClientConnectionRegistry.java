package com.enterprise.testagent.opencode.runtime.localclient;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

/** 当前后台 Java 持有的客户端连接表；同实例只保留 generation 最大的连接。 */
@Component
public class LocalClientConnectionRegistry {

    private final ConcurrentMap<LocalClientInstanceId, Connection> connections = new ConcurrentHashMap<>();

    public void register(
            LocalClientInstanceId clientInstanceId,
            UserId userId,
            long generation,
            String modelGrantFingerprint,
            LocalClientConnectionSender sender) {
        Connection incoming = new Connection(
                clientInstanceId, userId, generation, modelGrantFingerprint, sender);
        connections.compute(clientInstanceId, (ignored, current) -> {
            if (current != null && current.generation() >= generation) {
                sender.close("STALE_CONNECTION_GENERATION");
                return current;
            }
            if (current != null) {
                current.sender().close("CONNECTION_SUPERSEDED");
            }
            return incoming;
        });
    }

    public void send(LocalClientInstanceId clientInstanceId, long generation, LocalClientFrame frame) {
        Connection connection = require(clientInstanceId, generation);
        connection.sender().send(frame);
    }

    public boolean close(LocalClientInstanceId clientInstanceId, long generation, String reason) {
        Connection current = connections.get(clientInstanceId);
        if (current == null || current.generation() != generation) {
            return false;
        }
        if (!connections.remove(clientInstanceId, current)) {
            return false;
        }
        current.sender().close(reason);
        return true;
    }

    public boolean disconnect(LocalClientInstanceId clientInstanceId, long generation) {
        Connection current = connections.get(clientInstanceId);
        return current != null
                && current.generation() == generation
                && connections.remove(clientInstanceId, current);
    }

    public Optional<ConnectionSnapshot> find(LocalClientInstanceId clientInstanceId) {
        Connection connection = connections.get(clientInstanceId);
        return connection == null ? Optional.empty() : Optional.of(new ConnectionSnapshot(
                connection.clientInstanceId(),
                connection.userId(),
                connection.generation(),
                connection.modelGrantFingerprint()));
    }

    private Connection require(LocalClientInstanceId clientInstanceId, long generation) {
        Connection connection = connections.get(clientInstanceId);
        if (connection == null || connection.generation() != generation) {
            throw new PlatformException(
                    ErrorCode.LOCAL_CLIENT_DISCONNECTED,
                    "本地 OpenCode 客户端连接已离线或换代");
        }
        return connection;
    }

    public record ConnectionSnapshot(
            LocalClientInstanceId clientInstanceId,
            UserId userId,
            long generation,
            String modelGrantFingerprint) {
    }

    private record Connection(
            LocalClientInstanceId clientInstanceId,
            UserId userId,
            long generation,
            String modelGrantFingerprint,
            LocalClientConnectionSender sender) {

        private Connection {
            Objects.requireNonNull(clientInstanceId, "clientInstanceId must not be null");
            Objects.requireNonNull(userId, "userId must not be null");
            Objects.requireNonNull(modelGrantFingerprint, "modelGrantFingerprint must not be null");
            Objects.requireNonNull(sender, "sender must not be null");
        }
    }
}
