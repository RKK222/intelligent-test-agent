package com.enterprise.testagent.api.web.platform;

/** 应用自动化引用 HTTP 请求 DTO。 */
public final class AutomationReferenceRepositoryDtos {

    private AutomationReferenceRepositoryDtos() {
    }

    public record ConfigureRequest(
            String alias,
            String branch,
            String directoryPath,
            String description,
            boolean merge,
            long expectedGeneration,
            String operationId) {
    }

    public record SynchronizeRequest(long expectedGeneration, String operationId) {
    }

    public record GenerationRequest(long expectedGeneration) {
    }
}
