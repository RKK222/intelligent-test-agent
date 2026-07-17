package com.enterprise.testagent.opencode.client;

import com.enterprise.testagent.domain.node.ExecutionNode;
import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.List;
import java.util.Objects;

/**
 * 通过 opencode 原生 session command 启动技能任务的稳定命令模型。
 */
public record OpencodeStartCommand(
        ExecutionNode node,
        String opencodeSessionId,
        String directory,
        String workspace,
        String command,
        String arguments,
        List<OpencodePromptPart> parts,
        String messageId,
        String agent,
        String modelProviderId,
        String modelId,
        String variant,
        String traceId) {

    /**
     * 规范化命令参数；command 必填，arguments 和运行态选择字段可空。
     */
    public OpencodeStartCommand {
        Objects.requireNonNull(node, "node must not be null");
        opencodeSessionId = DomainValidation.requireText(opencodeSessionId, "opencodeSessionId");
        directory = DomainValidation.requireText(directory, "directory");
        workspace = optionalText(workspace);
        command = DomainValidation.requireText(command, "command");
        arguments = arguments == null ? "" : arguments;
        parts = parts == null ? List.of() : List.copyOf(parts);
        messageId = optionalText(messageId);
        agent = optionalText(agent);
        modelProviderId = optionalText(modelProviderId);
        modelId = optionalText(modelId);
        if ((modelProviderId == null) != (modelId == null)) {
            throw new IllegalArgumentException("modelProviderId and modelId must be provided together");
        }
        variant = optionalText(variant);
        traceId = DomainValidation.requireText(traceId, "traceId");
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
