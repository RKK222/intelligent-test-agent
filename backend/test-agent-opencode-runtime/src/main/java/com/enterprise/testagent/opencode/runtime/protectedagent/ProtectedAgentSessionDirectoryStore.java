package com.enterprise.testagent.opencode.runtime.protectedagent;

import java.time.Duration;
import java.util.Optional;

/** 受保护远端 Session 到服务器隔离目录的短期映射端口。 */
public interface ProtectedAgentSessionDirectoryStore {

    void save(String remoteSessionId, String directory, Duration ttl);

    Optional<String> find(String remoteSessionId);
}
