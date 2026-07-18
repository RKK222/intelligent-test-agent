package com.enterprise.testagent.opencode.runtime.terminal;

/**
 * 服务器 root 终端 ticket 请求。确认文本必须由用户当次手工输入，不能由前端自动代填。
 */
public record ServerTerminalTicketRequest(
        String confirmationText,
        Integer cols,
        Integer rows) {
}
