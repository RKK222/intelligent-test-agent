package com.enterprise.testagent.opencode.runtime.night;

import com.enterprise.testagent.common.id.RuntimeIdGenerator;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.opencode.runtime.run.StartRunInput;
import java.util.List;

/** 提交时固化的普通 Run 输入；到期时只重新签发短期 contextToken。 */
public record NightExecutionRunInputSnapshot(
        String prompt,
        List<StartRunInput.PromptPart> parts,
        String messageId,
        String agent,
        String model,
        String variant,
        String mode,
        String command,
        String arguments,
        String clientRequestId) {

    public NightExecutionRunInputSnapshot {
        prompt = optional(prompt);
        parts = parts == null ? List.of() : List.copyOf(parts);
        messageId = optional(messageId);
        agent = optional(agent);
        model = optional(model);
        variant = optional(variant);
        mode = optional(mode);
        command = optional(command);
        arguments = optional(arguments);
        clientRequestId = optional(clientRequestId);
        if ((prompt == null || prompt.isBlank())
                && parts.stream().noneMatch(part -> "text".equals(part.type())
                        && part.text() != null && !part.text().isBlank())) {
            throw new IllegalArgumentException("夜间任务消息不能为空");
        }
    }

    /** 补齐服务端稳定 ID，避免调度重试制造重复远端消息或 Run。 */
    public NightExecutionRunInputSnapshot withStableIds() {
        return new NightExecutionRunInputSnapshot(
                prompt, parts,
                messageId == null ? RuntimeIdGenerator.messageId() : messageId,
                agent, model, variant, mode, command, arguments,
                clientRequestId == null ? "night-run-" + RuntimeIdGenerator.runId() : clientRequestId);
    }

    public StartRunInput toStartRunInput(SessionId sessionId, String contextToken) {
        return new StartRunInput(
                sessionId, prompt, parts, messageId, agent, model, variant, mode,
                command, arguments, contextToken, clientRequestId);
    }

    public String effectivePrompt() {
        return toStartRunInput(new SessionId("ses_night_preview"), null).effectivePrompt();
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
