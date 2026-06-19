package com.example.testagent.common.pagination;

import java.util.List;
import java.util.Objects;

/**
 * 统一分页响应，复制列表内容以避免调用方在响应构造后继续修改集合。
 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    public PageResponse {
        items = List.copyOf(Objects.requireNonNull(items, "items must not be null"));
        if (page < 1) {
            throw new IllegalArgumentException("page must be greater than or equal to 1");
        }
        if (size < 1 || size > PageRequest.MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + PageRequest.MAX_SIZE);
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must be greater than or equal to 0");
        }
    }

    public long totalPages() {
        if (total == 0) {
            return 0;
        }
        return (total + size - 1) / size;
    }
}
