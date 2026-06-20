package com.example.testagent.common.pagination;

/**
 * 统一分页请求，页码从 1 开始，并限制最大 page size 避免列表接口无界读取。
 */
public record PageRequest(int page, int size) {

    public static final int MAX_SIZE = 200;

    /**
     * 校验分页请求边界，防止列表接口出现 0 页、负页或过大 page size。
     */
    public PageRequest {
        if (page < 1) {
            throw new IllegalArgumentException("page must be greater than or equal to 1");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

    /**
     * 计算 SQL 分页 offset，使用 long 避免 page/size 乘法溢出。
     */
    public long offset() {
        return (long) (page - 1) * size;
    }
}
