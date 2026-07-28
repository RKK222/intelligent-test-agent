package com.enterprise.testagent.domain.appsource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import org.junit.jupiter.api.Test;

class AppSourceOperationIdTest {

    @Test
    void acceptsCompatibleIdsAtTheLengthBoundary() {
        assertThat(AppSourceOperationId.normalize(" job_123 ")).isEqualTo("job_123");
        assertThat(AppSourceOperationId.normalize("x".repeat(AppSourceOperationId.MAX_LENGTH)))
                .hasSize(AppSourceOperationId.MAX_LENGTH);
    }

    @Test
    void rejectsControlCharactersPathSeparatorsAndOverlongIds() {
        assertInvalid("job_\n123");
        assertInvalid("job/123");
        assertInvalid("x".repeat(AppSourceOperationId.MAX_LENGTH + 1));
    }

    private void assertInvalid(String operationId) {
        assertThatThrownBy(() -> AppSourceOperationId.normalize(operationId))
                .isInstanceOfSatisfying(PlatformException.class,
                        exception -> assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
