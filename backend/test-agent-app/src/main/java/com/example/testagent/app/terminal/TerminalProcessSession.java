package com.example.testagent.app.terminal;

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
    private final AtomicInteger seq = new AtomicInteger();

    TerminalProcessSession(Process process) {
        this.process = Objects.requireNonNull(process, "process must not be null");
        this.stdin = process.getOutputStream();
        this.output = Sinks.many().multicast().onBackpressureBuffer(1024, false);
        startOutputPump(process.getInputStream());
        startExitWatcher();
    }

    public Flux<TerminalServerMessage> output() {
        return output.asFlux();
    }

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

    public Mono<Void> resize(Integer cols, Integer rows) {
        return Mono.empty();
    }

    public Mono<Void> close() {
        return Mono.fromRunnable(() -> {
                    process.destroy();
                    output.tryEmitComplete();
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    private void startOutputPump(InputStream stream) {
        Mono.fromRunnable(() -> {
                    byte[] buffer = new byte[4096];
                    try {
                        int read;
                        while ((read = stream.read(buffer)) >= 0) {
                            if (read > 0) {
                                output.tryEmitNext(TerminalServerMessage.output(
                                        new String(buffer, 0, read, StandardCharsets.UTF_8),
                                        seq.incrementAndGet()));
                            }
                        }
                    } catch (Exception exception) {
                        output.tryEmitNext(TerminalServerMessage.error("PTY_OUTPUT_FAILED", "output failed"));
                    }
                })
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe();
    }

    private void startExitWatcher() {
        Mono.fromCallable(process::waitFor)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(code -> {
                    output.tryEmitNext(TerminalServerMessage.exit(code, seq.incrementAndGet()));
                    output.tryEmitComplete();
                }, error -> {
                    output.tryEmitNext(TerminalServerMessage.error("PTY_EXIT_FAILED", "exit watcher failed"));
                    output.tryEmitComplete();
                });
    }
}
