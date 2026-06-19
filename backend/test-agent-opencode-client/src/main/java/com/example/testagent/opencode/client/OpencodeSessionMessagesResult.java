package com.example.testagent.opencode.client;

import java.util.List;

/**
 * opencode projected messages facade 返回值，分页 cursor 保持不透明字符串。
 */
public record OpencodeSessionMessagesResult(
        List<OpencodeSessionMessage> messages,
        String previousCursor,
        String nextCursor) {

    public OpencodeSessionMessagesResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
