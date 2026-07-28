package com.enterprise.testagent.domain.appsource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import org.junit.jupiter.api.Test;

class AppSourceOperationIdTest {

    private static final String ECMA_SCRIPT_TRIM_CHARACTERS = new String(new char[]{
            0x0009, 0x000A, 0x000B, 0x000C, 0x000D,
            0x0020, 0x00A0, 0x1680, 0x2000, 0x2001, 0x2002, 0x2003, 0x2004,
            0x2005, 0x2006, 0x2007, 0x2008, 0x2009, 0x200A, 0x2028, 0x2029,
            0x202F, 0x205F, 0x3000, 0xFEFF
    });

    @Test
    void acceptsCompatibleIdsAtTheLengthBoundary() {
        assertThat(AppSourceOperationId.normalize(" job_123 ")).isEqualTo("job_123");
        assertThat(AppSourceOperationId.normalize(
                ECMA_SCRIPT_TRIM_CHARACTERS + "release..1" + ECMA_SCRIPT_TRIM_CHARACTERS))
                .isEqualTo("release..1");
        assertThat(AppSourceOperationId.normalize("x".repeat(AppSourceOperationId.MAX_LENGTH)))
                .hasSize(AppSourceOperationId.MAX_LENGTH);
    }

    @Test
    void rejectsDotSegmentsControlCharactersPathSeparatorsAndOverlongIds() {
        assertInvalid(".");
        assertInvalid("..");
        assertInvalid(" .. ");
        assertInvalid("\u00a0..\u00a0");
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
