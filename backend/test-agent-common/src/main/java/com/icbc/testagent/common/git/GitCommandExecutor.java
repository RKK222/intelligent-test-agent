package com.icbc.testagent.common.git;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Git 命令执行器端口，统一封装命令、临时 SSH key 和超时边界。
 */
@FunctionalInterface
public interface GitCommandExecutor {

    ThreadLocal<List<String>> EXECUTED_COMMANDS = ThreadLocal.withInitial(ArrayList::new);
    ThreadLocal<Boolean> RECORDING = ThreadLocal.withInitial(() -> false);
    ThreadLocal<Consumer<String>> COMMAND_LISTENER = new ThreadLocal<>();

    static void startRecording() {
        startRecording(null);
    }

    static void startRecording(Consumer<String> commandListener) {
        RECORDING.set(true);
        EXECUTED_COMMANDS.get().clear();
        COMMAND_LISTENER.set(commandListener);
    }

    static List<String> stopRecording() {
        RECORDING.set(false);
        List<String> commands = new ArrayList<>(EXECUTED_COMMANDS.get());
        EXECUTED_COMMANDS.get().clear();
        COMMAND_LISTENER.remove();
        return commands;
    }

    static void record(List<String> command) {
        if (Boolean.TRUE.equals(RECORDING.get())) {
            String commandText = String.join(" ", command);
            Consumer<String> listener = COMMAND_LISTENER.get();
            if (listener != null) {
                listener.accept(commandText);
            }
            EXECUTED_COMMANDS.get().add(commandText);
        }
    }

    /**
     * 执行 Git 命令并返回 stdout；privateKey 为空时使用后端进程默认 Git/SSH 环境。
     */
    GitCommandResult execute(List<String> command, String privateKey, Duration timeout);
}
