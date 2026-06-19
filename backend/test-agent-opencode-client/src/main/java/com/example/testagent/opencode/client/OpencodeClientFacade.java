package com.example.testagent.opencode.client;

import com.example.testagent.domain.event.RunEventDraft;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 业务侧访问 opencode server 的唯一门面，不暴露 generated SDK API 或 DTO。
 */
public interface OpencodeClientFacade {

    Mono<OpencodeHealthResult> health(OpencodeHealthCommand command);

    Mono<OpencodeCreateSessionResult> createSession(OpencodeCreateSessionCommand command);

    Mono<OpencodeCancelResult> cancelSession(OpencodeCancelCommand command);

    Mono<OpencodeStartRunResult> startRun(OpencodeStartRunCommand command);

    Flux<RunEventDraft> streamRunEvents(OpencodeStreamEventsCommand command);

    Mono<OpencodeDiffResult> getDiff(OpencodeDiffCommand command);

    Mono<OpencodeRejectDiffResult> rejectDiff(OpencodeRejectDiffCommand command);
}
