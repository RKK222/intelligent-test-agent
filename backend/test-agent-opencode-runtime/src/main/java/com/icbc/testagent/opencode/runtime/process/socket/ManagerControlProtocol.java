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
    /** manager→后端请求当前公共参数运行配置。 */
    public static final String TYPE_CONFIG_REQUEST = "configRequest";
    /** 后端→manager 下发运行时配置，manager 热更新路径并按端口池 clamp 最大进程数。 */
    public static final String TYPE_CONFIG_UPDATE = "configUpdate";

    /**
     * 常量类不允许实例化。
     */
    private ManagerControlProtocol() {
    }
}
