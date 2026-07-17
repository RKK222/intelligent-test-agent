package com.enterprise.testagent.agent.runtime;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.util.Map;

/**
 * 平台内部通用 prompt part，具体 agent 适配器负责转成自身协议。
 */
public record AgentPromptPart(
        String type,
        String text,
        String url,
        String mime,
        String filename,
        String agentName,
        Map<String, Object> source) {

    /**
     * 固化 part 类型和扩展元数据。
     */
    public AgentPromptPart {
        type = DomainValidation.requireText(type, "type");
        source = source == null ? Map.of() : Map.copyOf(source);
    }

    /**
     * 构造文本 part。
     */
    public static AgentPromptPart text(String text) {
        return new AgentPromptPart("text", DomainValidation.requireText(text, "text"), null, null, null, null, Map.of());
    }

    /**
     * 构造文件 part。
     */
    public static AgentPromptPart file(String url, String mime, String filename, Map<String, Object> source) {
        return new AgentPromptPart(
                "file",
                null,
                DomainValidation.requireText(url, "url"),
                DomainValidation.requireText(mime, "mime"),
                DomainValidation.requireText(filename, "filename"),
                null,
                source);
    }

    /**
     * 构造 agent part。
     */
    public static AgentPromptPart agent(String agentName, Map<String, Object> source) {
        return new AgentPromptPart(
                "agent",
                null,
                null,
                null,
                null,
                DomainValidation.requireText(agentName, "agentName"),
                source);
    }
}
