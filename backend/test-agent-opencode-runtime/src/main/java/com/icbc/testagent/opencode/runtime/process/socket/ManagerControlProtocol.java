package com.icbc.testagent.opencode.runtime.process.socket;

/**
 * opencode-manager 与后端 Java 实例之间的控制面协议常量。
 */
public final class ManagerControlProtocol {

    public static final String VERSION = "opencode-manager.v1";
    public static final String TYPE_REGISTER = "register";
    public static final String TYPE_REGISTERED = "registered";
    public static final String TYPE_HEARTBEAT = "heartbeat";
    public static final String TYPE_MANAGER_HEARTBEAT = "managerHeartbeat";
    public static final String TYPE_BACKEND_LIST_REQUEST = "backendListRequest";
    public static final String TYPE_BACKEND_LIST_RESPONSE = "backendListResponse";
    public static final String TYPE_COMMAND = "command";
    public static final String TYPE_COMMAND_RESULT = "commandResult";
    public static final String TYPE_ERROR = "error";
    /** 后端→manager 下发运行时配置（当前为最大进程数），manager 热更新并按端口池 clamp。 */
    public static final String TYPE_CONFIG_UPDATE = "configUpdate";

    /**
     * 常量类不允许实例化。
     */
    private ManagerControlProtocol() {
    }
}
