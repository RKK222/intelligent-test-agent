package com.enterprise.testagent.opencode.runtime.localclient;

import com.enterprise.testagent.domain.localclient.LocalClientInstanceId;
import com.enterprise.testagent.localclient.protocol.LocalClientFrame;
import com.enterprise.testagent.localclient.protocol.LocalClientFrameType;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

/** 反向隧道请求等待表；每个请求使用有界 64 帧队列，溢出时失败关闭而不是无界堆积。 */
@Component
public class LocalClientPendingRequestRegistry {

    private static final int MAX_PENDING_REQUESTS = 1024;

    private final ConcurrentMap<String, Pending> requests = new ConcurrentHashMap<>();
    private final Semaphore requestSlots = new Semaphore(MAX_PENDING_REQUESTS);

    public PendingExchange open(String requestId, LocalClientInstanceId clientInstanceId, long generation) {
        if (!requestSlots.tryAcquire()) {
            throw new IllegalStateException("too many pending local client requests");
        }
        Sinks.Many<LocalClientFrame> sink = Sinks.many().unicast()
                .onBackpressureBuffer(new ArrayBlockingQueue<>(64));
        Pending pending = new Pending(clientInstanceId, generation, sink);
        if (requests.putIfAbsent(requestId, pending) != null) {
            requestSlots.release();
            throw new IllegalStateException("duplicate local client requestId");
        }
        return new PendingExchange(requestId, sink.asFlux());
    }

    public boolean accept(LocalClientInstanceId clientInstanceId, LocalClientFrame frame) {
        Pending pending = requests.get(frame.requestId());
        if (pending == null
                || !pending.clientInstanceId().equals(clientInstanceId)
                || frame.connectionGeneration() == null
                || frame.connectionGeneration() != pending.generation()) {
            return false;
        }
        Sinks.EmitResult result;
        synchronized (pending.sink()) {
            result = pending.sink().tryEmitNext(frame);
            if (result == Sinks.EmitResult.FAIL_OVERFLOW) {
                pending.sink().tryEmitError(new IllegalStateException("local client response backpressure overflow"));
            } else if (terminal(frame.type())) {
                pending.sink().tryEmitComplete();
            }
        }
        if ((result != Sinks.EmitResult.OK || terminal(frame.type()))
                && requests.remove(frame.requestId(), pending)) {
            requestSlots.release();
        }
        return result == Sinks.EmitResult.OK;
    }

    public void cancel(String requestId) {
        Pending pending = requests.remove(requestId);
        if (pending == null) {
            return;
        }
        requestSlots.release();
        synchronized (pending.sink()) {
            pending.sink().tryEmitComplete();
        }
    }

    public void failConnection(LocalClientInstanceId clientInstanceId, long generation, Throwable error) {
        requests.forEach((requestId, pending) -> {
            if (!pending.clientInstanceId().equals(clientInstanceId) || pending.generation() != generation) {
                return;
            }
            if (requests.remove(requestId, pending)) {
                requestSlots.release();
                synchronized (pending.sink()) {
                    pending.sink().tryEmitError(error);
                }
            }
        });
    }

    private static boolean terminal(LocalClientFrameType type) {
        return type == LocalClientFrameType.LIFECYCLE_RESULT
                || type == LocalClientFrameType.HTTP_RESPONSE
                || type == LocalClientFrameType.FILE_RESPONSE
                || type == LocalClientFrameType.STREAM_END
                || type == LocalClientFrameType.ERROR;
    }

    public record PendingExchange(String requestId, Flux<LocalClientFrame> frames) {
        public PendingExchange {
            Objects.requireNonNull(requestId, "requestId must not be null");
            Objects.requireNonNull(frames, "frames must not be null");
        }
    }

    private record Pending(
            LocalClientInstanceId clientInstanceId,
            long generation,
            Sinks.Many<LocalClientFrame> sink) {
    }
}
