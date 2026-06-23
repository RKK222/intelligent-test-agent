package com.icbc.testagent.common.git;

import java.time.Duration;
import java.util.List;

/**
 * Git 命令执行器端口，统一封装命令、临时 SSH key 和超时边界。
 */
@FunctionalInterface
public interface GitCommandExecutor {

    /**
     * 执行 Git 命令并返回 stdout；privateKey 为空时使用后端进程默认 Git/SSH 环境。
     */
    GitCommandResult execute(List<String> command, String privateKey, Duration timeout);
}
