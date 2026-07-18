package com.enterprise.testagent.opencode.runtime.terminal;

/**
 * 服务器终端 ticket 请求。确认值由连接确认框绑定到用户当次选择的目标服务器。
 */
public record ServerTerminalTicketRequest(
        String confirmationText,
        Integer cols,
        Integer rows) {
}
