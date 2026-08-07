package com.enterprise.testagent.opencode.runtime.internalmodel.observability;

import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecord;
import com.enterprise.testagent.domain.internalmodelobservability.InternalModelCallRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 内部模型代理调用观测记录器。api 层只调用此服务，落库逻辑全在 opencode-runtime。
 *
 * <p>观测是尽力而为：任何落库异常都吞掉并记 warn，绝不影响转发主链路。
 * 内部代理低 QPS，采用 boundedElastic 直插单条 insert+upsert，不做内存队列批量。</p>
 */
@Service
public class InternalModelCallRecorder {

    private static final Logger log = LoggerFactory.getLogger(InternalModelCallRecorder.class);

    private final InternalModelCallRecordRepository repository;

    public InternalModelCallRecorder(InternalModelCallRecordRepository repository) {
        this.repository = repository;
    }

    /**
     * 异步落库，返回的 Mono 已完成；订阅方无需等待。失败记录 warn 后静默。
     */
    public Mono<Void> record(InternalModelCallRecord record) {
        return Mono.fromRunnable(() -> repository.record(record))
                .subscribeOn(Schedulers.boundedElastic())
                .then()
                .onErrorResume(error -> {
                    log.warn(
                            "internal-model observability record failed, providerId={} traceId={}",
                            record.providerId(), record.traceId(), error);
                    return Mono.empty();
                });
    }

    /**
     * 同步入口的 fire-and-forget 兜底：在请求前同步阶段（prepareRequest 异常）调用，
     * 不阻塞当前线程、不影响后续 rethrow。
     */
    public void recordAsyncFireAndForget(InternalModelCallRecord record) {
        record(record).subscribe();
    }
}
