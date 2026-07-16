package com.enterprise.testagent.opencode.client;

import java.util.List;

/**
 * opencode projected messages facade 返回值，分页 cursor 保持不透明字符串。
 */
public record OpencodeSessionMessagesResult(
        List<OpencodeSessionMessage> messages,
        String previousCursor,
        String nextCursor) {

    /**
     * 固化消息列表，null 响应按空列表处理，cursor 保持远端不透明值。
     */
    public OpencodeSessionMessagesResult {
        messages = messages == null ? List.of() : List.copyOf(messages);
    }
}
