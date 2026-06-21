package com.icbc.testagent.opencode.client;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * opencode 健康检查命令，调用方必须传入路由选中的执行节点和 traceId。
 */
public record OpencodeHealthCommand(ExecutionNode node, String traceId) {

    /**
     * 校验健康检查必须携带执行节点和 traceId。
     */
    public OpencodeHealthCommand {
        Objects.requireNonNull(node, "node must not be null");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
