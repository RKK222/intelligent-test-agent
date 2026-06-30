package com.icbc.testagent.domain.opencodeprocess;

/**
 * 当前后端 Java 实例身份端口；供需要实例身份（广播来源等）的模块依赖，不直接耦合运行时实现。
 *
 * <p>由 {@code test-agent-opencode-runtime} 提供实现，复用 manager 控制面设置与后端进程生命周期服务。
 */
public interface BackendInstanceIdentity {

    /** 当前实例的广播身份 ID，等价 {@code ServerBroadcastPublisher.instanceId()}。 */
    String instanceId();

    /** 当前实例所在 Linux 服务器 ID。 */
    String linuxServerId();

    /** 当前后端 Java 进程业务 ID（形如 {@code bjp_xxx}）。 */
    String backendProcessId();

    /** 当前后端监听地址。 */
    String listenUrl();
}
