package com.example.testagent.common.pagination;

import java.util.List;
import java.util.Objects;

/**
 * 统一分页响应，复制列表内容以避免调用方在响应构造后继续修改集合。
 */
public record PageResponse<T>(List<T> items, int page, int size, long total) {

    /**
     * 校验分页响应边界并复制结果列表；page 从 1 开始，size 与请求上限保持一致，total 不允许为负数。
     */
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

    /**
     * 计算总页数；空结果返回 0，非空结果按 size 向上取整。
     */
    public long totalPages() {
        if (total == 0) {
            return 0;
        }
        return (total + size - 1) / size;
    }
}
