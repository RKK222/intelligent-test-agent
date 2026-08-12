package com.enterprise.testagent.common.git;
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
    ThreadLocal<List<String>> SENSITIVE_LOG_ARGUMENTS = ThreadLocal.withInitial(ArrayList::new);

    /** 可嵌套的 Git 日志脱敏作用域，仅影响当前调用线程，不改变实际执行参数。 */
    @FunctionalInterface
    interface LogRedaction extends AutoCloseable {
        @Override
        void close();
    }

    /**
     * 将指定本地参数在当前线程的 Git 命令、错误详情和日志中替换为固定占位符。
     * 体验工作区用它隐藏管理员配置的物理目录，关闭作用域后恢复外层配置。
     */
    static LogRedaction redactSensitiveArguments(List<String> arguments) {
        List<String> previous = List.copyOf(SENSITIVE_LOG_ARGUMENTS.get());
        List<String> merged = new ArrayList<>(previous);
        if (arguments != null) {
            arguments.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .filter(value -> !merged.contains(value))
                    .forEach(merged::add);
        }
        // 长路径优先，避免嵌套目录先被短前缀替换后残留敏感后缀。
        merged.sort((left, right) -> Integer.compare(right.length(), left.length()));
        SENSITIVE_LOG_ARGUMENTS.set(merged);
        return () -> {
            if (previous.isEmpty()) {
                SENSITIVE_LOG_ARGUMENTS.remove();
            } else {
                SENSITIVE_LOG_ARGUMENTS.set(new ArrayList<>(previous));
            }
        };
    }

    /** 对日志文本应用当前线程的精确参数替换；原始命令仍按未替换参数执行。 */
    static String redactSensitiveText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String redacted = text;
        for (String sensitive : SENSITIVE_LOG_ARGUMENTS.get()) {
            redacted = redacted.replace(sensitive, "<redacted-local-path>");
        }
        return redacted;
    }

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
            // 测试监听器与正式日志遵循同一脱敏范围，避免记录模式旁路暴露本地目录或密钥参数。
            String commandText = redactSensitiveText(String.join(" ", command));
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
