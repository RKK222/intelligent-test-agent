package com.icbc.testagent.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class PageRequestTest {

    @Test
    void pageRequestUsesOneBasedPageAndComputesOffset() {
        PageRequest request = new PageRequest(2, 50);

        assertThat(request.page()).isEqualTo(2);
        assertThat(request.size()).isEqualTo(50);
        assertThat(request.offset()).isEqualTo(50);
    }

    @Test
    void pageRequestRejectsInvalidBounds() {
        assertThatThrownBy(() -> new PageRequest(0, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
        assertThatThrownBy(() -> new PageRequest(1, 201))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
    }

    @Test
    void pageResponseCopiesItemsAndComputesTotalPages() {
        PageResponse<String> response = new PageResponse<>(List.of("a", "b"), 2, 2, 5);

        assertThat(response.items()).containsExactly("a", "b");
        assertThat(response.totalPages()).isEqualTo(3);
    }
}
