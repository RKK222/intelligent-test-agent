package com.icbc.testagent.api.web.common;

import com.icbc.testagent.opencode.runtime.runtime.SideQuestionInput;
import com.icbc.testagent.opencode.runtime.runtime.SideQuestionResult;
import jakarta.validation.constraints.NotBlank;

/**
 * 宠物旁路问答的跨 Controller HTTP DTO，避免 agent/platform 两套路由复制契约。
 */
public final class SideQuestionDtos {

    private SideQuestionDtos() {
    }

    /**
     * 一次旁路问题；messageId 固定 fork 边界，model 仅在需要 compact 时必须可解析。
     */
    public record Request(
            @NotBlank String question,
            String messageId,
            String agent,
            String model) {

        public SideQuestionInput toInput() {
            return new SideQuestionInput(question, messageId, agent, model);
        }
    }

    /**
     * 旁路回答，不返回临时 fork 的远端会话标识。
     */
    public record Response(String answer, boolean compacted) {

        public static Response from(SideQuestionResult result) {
            return new Response(result.answer(), result.compacted());
        }
    }
}
