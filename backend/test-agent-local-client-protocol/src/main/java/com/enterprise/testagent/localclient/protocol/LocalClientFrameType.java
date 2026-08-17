package com.enterprise.testagent.localclient.protocol;

/** v1 反向隧道支持的帧类型。 */
public enum LocalClientFrameType {
    REGISTER,
    REGISTERED,
    HEARTBEAT,
    HEARTBEAT_ACK,
    LIFECYCLE_COMMAND,
    LIFECYCLE_RESULT,
    HTTP_REQUEST,
    HTTP_RESPONSE,
    STREAM_OPEN,
    STREAM_CHUNK,
    STREAM_END,
    FILE_REQUEST,
    FILE_RESPONSE,
    BINARY_CHUNK,
    MODEL_GRANT,
    CANCEL,
    ERROR
}
