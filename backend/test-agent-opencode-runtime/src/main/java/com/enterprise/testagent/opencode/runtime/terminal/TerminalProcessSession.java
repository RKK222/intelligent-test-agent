package com.enterprise.testagent.opencode.runtime.terminal;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * 本地 shell 进程适配器。当前实现不暴露裸 Process，只通过受控 input/output envelope 交互。
 */
public class TerminalProcessSession {

    private final Process process;
    private final OutputStream stdin;
    private final Sinks.Many<TerminalServerMessage> output;
    private final TerminalOutputLimiter outputLimiter;
    private final AtomicInteger seq = new AtomicInteger();

    /**
     * 使用默认输出预算包装本地进程，供测试和生产快捷构造。
     */
    TerminalProcessSession(Process process) {
        this(process, new TerminalOutputLimiter(16 * 1024, 1024 * 1024));
    }

    /**
     * 包装本地进程并启动输出泵和退出监听器。
     */
    TerminalProcessSession(Process process, TerminalOutputLimiter outputLimiter) {
        this.process = Objects.requireNonNull(process, "process must not be null");
        this.outputLimiter = Objects.requireNonNull(outputLimiter, "outputLimiter must not be null");
        this.stdin = process.getOutputStream();
        this.output = Sinks.many().replay().limit(1024);
        // exit envelope 不能抢在 stdout 泵完成前结束 sink；快速退出的命令仍必须把末尾输出交给客户端。
        startExitWatcher(startOutputPump(process.getInputStream()));
    }

    /**
     * 返回服务端输出流，包含 output/exit/error/warning envelope。
     */
    public Flux<TerminalServerMessage> output() {
        return output.asFlux();
    }

    /**
     * 向本地进程 stdin 写入输入帧，空输入忽略，过大输入转为错误 envelope。
     */
    public Mono<Void> input(String data) {
        if (data == null || data.isEmpty()) {
            return Mono.empty();
        }
        if (data.length() > 16 * 1024) {
            output.tryEmitNext(TerminalServerMessage.error("PTY_INPUT_TOO_LARGE", "input too large"));
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> {
                    try {
                        stdin.write(data.getBytes(StandardCharsets.UTF_8));
                        stdin.flush();
                    } catch (Exception exception) {
                        output.tryEmitNext(TerminalServerMessage.error("PTY_INPUT_FAILED", "input failed"));
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /**
     * 调整 PTY 尺寸；当前本地进程适配器暂不支持真实 resize，保留幂等空操作。
     */
    public Mono<Void> resize(Integer cols, Integer rows) {
        return Mono.empty();
    }

    /**
     * 关闭本地进程并完成输出流。
     */
    public Mono<Void> close() {
        return Mono.fromRunnable(() -> {
                    process.destroy();
                    output.tryEmitComplete();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /**
     * 从进程 stdout/stderr 读取数据，按输出预算转换为服务端 envelope。
     */
    private Mono<Void> startOutputPump(InputStream stream) {
        return Mono.fromRunnable(() -> {
                    byte[] buffer = new byte[4096];
                    try {
                        int read;
                        while ((read = stream.read(buffer)) >= 0) {
                            if (read > 0) {
                                int nextSeq = seq.incrementAndGet();
                                outputLimiter.output(new String(buffer, 0, read, StandardCharsets.UTF_8), nextSeq)
                                        .forEach(output::tryEmitNext);
                            }
                        }
                    } catch (Exception exception) {
                        output.tryEmitNext(TerminalServerMessage.error("PTY_OUTPUT_FAILED", "output failed"));
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /**
     * 监听进程退出并发送 exit envelope，监听失败时发送稳定错误 envelope。
     */
    private void startExitWatcher(Mono<Void> outputPump) {
        outputPump.then(Mono.fromCallable(process::waitFor)
                .subscribeOn(Schedulers.boundedElastic())
        ).subscribe(code -> {
                    output.tryEmitNext(TerminalServerMessage.exit(code, seq.incrementAndGet()));
                    output.tryEmitComplete();
                }, error -> {
                    output.tryEmitNext(TerminalServerMessage.error("PTY_EXIT_FAILED", "exit watcher failed"));
                    output.tryEmitComplete();
                });
    }
}
