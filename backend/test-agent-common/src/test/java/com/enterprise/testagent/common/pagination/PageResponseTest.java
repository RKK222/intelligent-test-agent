package com.enterprise.testagent.common.pagination;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PageResponseTest {

    @Test
    void pageResponseDefensivelyCopiesItems() {
        List<String> items = new ArrayList<>();
        items.add("a");

        PageResponse<String> response = new PageResponse<>(items, 1, 20, 1);
        items.add("b");

        assertThat(response.items()).containsExactly("a");
        assertThatThrownBy(() -> response.items().add("c")).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void totalPagesReturnsZeroWhenTotalIsZero() {
        PageResponse<String> response = new PageResponse<>(List.of(), 1, 20, 0);

        assertThat(response.totalPages()).isZero();
    }

    @Test
    void pageResponseRejectsInvalidPageSizeAndTotal() {
        assertThatThrownBy(() -> new PageResponse<>(List.of(), 0, 20, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("page");
        assertThatThrownBy(() -> new PageResponse<>(List.of(), 1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("size");
        assertThatThrownBy(() -> new PageResponse<>(List.of(), 1, 20, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("total");
    }
}
