package com.example.testagent.opencode.client;

import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.support.DomainValidation;
import java.util.Objects;

/**
 * opencode 健康检查命令，调用方必须传入路由选中的执行节点和 traceId。
 */
public record OpencodeHealthCommand(ExecutionNode node, String traceId) {

    public OpencodeHealthCommand {
        Objects.requireNonNull(node, "node must not be null");
        traceId = DomainValidation.requireText(traceId, "traceId");
    }
}
