package com.icbc.testagent.agent.runtime;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.support.DomainValidation;
import java.util.List;
import java.util.Objects;

/**
 * 启动远端 agent 运行的通用命令。
 */
public record AgentStartRunCommand(
        ExecutionNode node,
        String remoteSessionId,
        String directory,
        String workspace,
        String prompt,
        List<AgentPromptPart> parts,
        String messageId,
        String agent,
        String system,
        String modelProviderId,
        String modelId,
        String variant,
        String command,
        String arguments,
        String traceId) {

    /**
     * 校验远端会话、prompt 和 traceId，并固化 part 列表。
     */
    public AgentStartRunCommand {
        Objects.requireNonNull(node, "node must not be null");
        remoteSessionId = DomainValidation.requireText(remoteSessionId, "remoteSessionId");
        directory = directory == null ? null : DomainValidation.requireText(directory, "directory");
        workspace = workspace == null || workspace.isBlank() ? null : workspace.trim();
        prompt = DomainValidation.requireText(prompt, "prompt");
        parts = parts == null ? List.of() : List.copyOf(parts);
        messageId = optionalText(messageId);
        agent = optionalText(agent);
        system = optionalText(system);
        modelProviderId = optionalText(modelProviderId);
        modelId = optionalText(modelId);
        variant = optionalText(variant);
        command = optionalText(command);
        arguments = optionalText(arguments);
        traceId = DomainValidation.requireText(traceId, "traceId");
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
