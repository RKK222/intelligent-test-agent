package com.enterprise.testagent.domain.memory;

import com.enterprise.testagent.domain.support.DomainValidation;
import java.time.Instant;
import java.util.Objects;

/** Application 团队记忆的审核事实。 */
public record MemoryReview(
        String reviewId,
        MemoryId memoryId,
        String applicationId,
        String submittedByUserId,
        String reviewedByUserId,
        String decision,
        String comment,
        Instant submittedAt,
        Instant reviewedAt) {

    public MemoryReview {
        reviewId = DomainValidation.requireText(reviewId, "reviewId");
        Objects.requireNonNull(memoryId, "memoryId must not be null");
        applicationId = DomainValidation.requireText(applicationId, "applicationId");
        submittedByUserId = DomainValidation.requireText(submittedByUserId, "submittedByUserId");
        decision = DomainValidation.requireText(decision, "decision");
        reviewedByUserId = optional(reviewedByUserId);
        comment = optional(comment);
        Objects.requireNonNull(submittedAt, "submittedAt must not be null");
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
