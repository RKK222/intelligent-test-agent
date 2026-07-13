package com.icbc.testagent.agent.runtime;

import com.icbc.testagent.domain.node.ExecutionNode;
import com.icbc.testagent.domain.support.DomainValidation;
import java.util.List;
import java.util.Map;
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
        Map<String, Boolean> tools,
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
        tools = tools == null ? Map.of() : Map.copyOf(tools);
        command = optionalText(command);
        arguments = optionalText(arguments);
        traceId = DomainValidation.requireText(traceId, "traceId");
    }

    /**
     * 兼容尚未声明工具开关的运行入口；空映射表示沿用远端 agent 默认工具配置。
     */
    public AgentStartRunCommand(
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
        this(
                node,
                remoteSessionId,
                directory,
                workspace,
                prompt,
                parts,
                messageId,
                agent,
                system,
                modelProviderId,
                modelId,
                variant,
                Map.of(),
                command,
                arguments,
                traceId);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
