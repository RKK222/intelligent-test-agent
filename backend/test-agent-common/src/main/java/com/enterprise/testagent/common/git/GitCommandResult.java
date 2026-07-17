package com.enterprise.testagent.common.git;

/**
 * Git 命令执行结果，只暴露安全摘要，不携带完整 stderr。
 */
public record GitCommandResult(int exitCode, String stdoutText, byte[] stdoutBytes) {
}
