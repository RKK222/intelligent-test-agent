package com.enterprise.testagent.opencode.runtime.localclient;

import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.domain.node.ExecutionNodeId;
import java.util.Optional;

/** 本地客户端稳定实例与关系库 execution node 锚点之间的唯一映射规则。 */
public final class LocalClientExecutionNodeIdentity {

    private static final String CLIENT_PREFIX = "lci_";
    private static final String NODE_PREFIX = "node_local_";

    private LocalClientExecutionNodeIdentity() {
    }

    /** 生成可供 Session binding 和 routing decision 外键引用的稳定节点 ID。 */
    public static ExecutionNodeId nodeId(LocalClientInstanceId clientInstanceId) {
        String value = clientInstanceId.value();
        return new ExecutionNodeId(NODE_PREFIX + value.substring(CLIENT_PREFIX.length()));
    }

    /** 仅解析本映射生成的本地节点；服务器节点和非法历史值返回空。 */
    public static Optional<LocalClientInstanceId> clientInstanceId(ExecutionNodeId executionNodeId) {
        String value = executionNodeId.value();
        if (!value.startsWith(NODE_PREFIX) || value.length() == NODE_PREFIX.length()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new LocalClientInstanceId(
                    CLIENT_PREFIX + value.substring(NODE_PREFIX.length())));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
