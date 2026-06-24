package com.icbc.testagent.opencode.runtime.process;

/**
 * 超级管理员运行管理页概览计数，所有字段都是当前查询快照中的只读统计。
 */
public record RuntimeManagementSummary(
        int linuxServers,
        int readyLinuxServers,
        int backendProcesses,
        int readyBackendProcesses,
        int containers,
        int readyContainers,
        int managers,
        int connectedManagers,
        int managerBackendConnections,
        long opencodeProcesses,
        long runningOpencodeProcesses,
        long userBindings) {
}
