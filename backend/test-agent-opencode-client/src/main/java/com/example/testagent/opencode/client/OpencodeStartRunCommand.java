package com.example.testagent.opencode.client;

import com.example.testagent.domain.node.ExecutionNode;
import com.example.testagent.domain.support.DomainValidation;
import java.util.List;
import java.util.Objects;

/**
 * opencode 异步启动 Run 命令，使用远端 opencode session id，generated request 只在 gateway 内部构造。
 */
public record OpencodeStartRunCommand(
        ExecutionNode node,
        String opencodeSessionId,
        String directory,
        String workspace,
        String prompt,
        List<OpencodePromptPart> parts,
        String messageId,
        String agent,
        String modelProviderId,
        String modelId,
        String variant,
        String traceId) {

    public OpencodeStartRunCommand {
        Objects.requireNonNull(node, "node must not be null");
        opencodeSessionId = DomainValidation.requireText(opencodeSessionId, "opencodeSessionId");
        directory = DomainValidation.requireText(directory, "directory");
        workspace = optionalText(workspace);
        prompt = DomainValidation.requireText(prompt, "prompt");
        parts = normalizeParts(parts, prompt);
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

    public OpencodeStartRunCommand(
            ExecutionNode node,
            String opencodeSessionId,
            String directory,
            String workspace,
            String prompt,
            String traceId) {
        this(node, opencodeSessionId, directory, workspace, prompt, List.of(OpencodePromptPart.text(prompt)),
                null, null, null, null, null, traceId);
    }

    private static List<OpencodePromptPart> normalizeParts(List<OpencodePromptPart> parts, String prompt) {
        if (parts == null || parts.isEmpty()) {
            return List.of(OpencodePromptPart.text(prompt));
        }
        return List.copyOf(parts);
    }

    private static String optionalText(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
