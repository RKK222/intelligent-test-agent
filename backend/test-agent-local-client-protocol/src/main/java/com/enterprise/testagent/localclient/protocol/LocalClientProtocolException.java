package com.enterprise.testagent.localclient.protocol;

/** 线协议不兼容或帧字段非法时抛出的稳定异常。 */
public class LocalClientProtocolException extends RuntimeException {

    private final String code;

    public LocalClientProtocolException(String code, String message) {
        super(message);
        this.code = code;
    }

    public LocalClientProtocolException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
