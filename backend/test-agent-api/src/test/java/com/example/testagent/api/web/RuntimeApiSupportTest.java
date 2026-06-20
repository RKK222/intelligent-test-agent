package com.example.testagent.api.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.testagent.common.error.ErrorCode;
import com.example.testagent.common.error.PlatformException;
import com.example.testagent.common.pagination.PageRequest;
import org.junit.jupiter.api.Test;

class RuntimeApiSupportTest {

    @Test
    void pageRequestAppliesDefaultPagination() {
        PageRequest request = RuntimeApiSupport.pageRequest(null, null);

        assertThat(request.page()).isEqualTo(1);
        assertThat(request.size()).isEqualTo(50);
    }

    @Test
    void pageRequestConvertsInvalidValuesToPlatformException() {
        assertThatThrownBy(() -> RuntimeApiSupport.pageRequest(0, 20))
                .isInstanceOf(PlatformException.class)
                .satisfies(exception -> assertThat(((PlatformException) exception).errorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));
    }
}
