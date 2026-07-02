package com.icbc.testagent.opencode.runtime.process;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessStartOperationRepository;
import com.icbc.testagent.domain.opencodeprocess.OpencodeProcessStartOperationStep;
import com.icbc.testagent.domain.user.UserId;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * opencode 用户进程启动的可选进度记录器，封装 operationId 存在时的持久化写入。
 */
final class OpencodeProcessStartProgress {

    private static final Supplier<Instant> SYSTEM_NOW = Instant::now;

    private final OpencodeProcessStartOperationRepository repository;
    private final String operationId;
    private final Supplier<Instant> nowSupplier;
    private OpencodeProcessStartOperationStep currentStep = OpencodeProcessStartOperationStep.VALIDATING_REQUEST;
    private boolean terminal;

    private OpencodeProcessStartProgress(
            OpencodeProcessStartOperationRepository repository,
            String operationId,
            Supplier<Instant> nowSupplier) {
        this.repository = repository;
        this.operationId = operationId;
        this.nowSupplier = nowSupplier == null ? SYSTEM_NOW : nowSupplier;
    }

    static OpencodeProcessStartProgress noop() {
        return new OpencodeProcessStartProgress(null, null, SYSTEM_NOW);
    }

    static OpencodeProcessStartProgress start(
            OpencodeProcessStartOperationRepository repository,
            String operationId,
            UserId userId,
            String agentId,
            String traceId,
            Supplier<Instant> nowSupplier) {
        if (repository == null || operationId == null || operationId.isBlank()) {
            return noop();
        }
        Supplier<Instant> resolvedNow = nowSupplier == null ? SYSTEM_NOW : nowSupplier;
        repository.start(operationId.trim(), userId, agentId, traceId, resolvedNow.get());
        return new OpencodeProcessStartProgress(repository, operationId.trim(), resolvedNow);
    }

    boolean enabled() {
        return repository != null && operationId != null && !operationId.isBlank();
    }

    OpencodeProcessStartOperationStep currentStep() {
        return currentStep;
    }

    void step(OpencodeProcessStartOperationStep step) {
        Objects.requireNonNull(step, "step must not be null");
        currentStep = step;
        if (enabled() && !terminal) {
            repository.markStep(operationId, step, nowSupplier.get());
        }
    }

    void succeeded(String processId, String serviceAddress) {
        if (!enabled() || terminal) {
            return;
        }
        terminal = true;
        currentStep = OpencodeProcessStartOperationStep.COMPLETED;
        repository.markSucceeded(operationId, processId, serviceAddress, nowSupplier.get());
    }

    void failed(PlatformException exception) {
        failed(exception.errorCode().name(), safeMessage(exception.getMessage()));
    }

    void failed(RuntimeException exception) {
        if (exception instanceof PlatformException platformException) {
            failed(platformException);
            return;
        }
        failed(ErrorCode.INTERNAL_ERROR.name(), "初始化 opencode 进程失败");
    }

    void failed(String errorCode, String errorMessage) {
        if (!enabled() || terminal) {
            return;
        }
        terminal = true;
        repository.markFailed(
                operationId,
                currentStep,
                errorCode == null || errorCode.isBlank() ? ErrorCode.INTERNAL_ERROR.name() : errorCode,
                safeMessage(errorMessage),
                nowSupplier.get());
    }

    private static String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "初始化 opencode 进程失败";
        }
        return message.trim();
    }
}
