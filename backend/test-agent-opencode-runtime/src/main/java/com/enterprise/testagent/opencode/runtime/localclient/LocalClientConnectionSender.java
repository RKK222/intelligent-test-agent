package com.enterprise.testagent.opencode.runtime.localclient;

import com.enterprise.testagent.localclient.protocol.LocalClientFrame;

/** 当前 Java 进程内单条本地客户端 WebSocket 的发送与关闭能力。 */
public interface LocalClientConnectionSender {

    void send(LocalClientFrame frame);

    void close(String reason);
}
