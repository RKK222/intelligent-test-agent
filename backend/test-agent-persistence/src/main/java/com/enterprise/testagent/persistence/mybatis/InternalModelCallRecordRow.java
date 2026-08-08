package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;

/** 内部模型代理调用明细行，与 domain 记录一一对应，供 MyBatis 构造函数 resultMap 映射。 */
public record InternalModelCallRecordRow(
        Long id,
        String providerId,
        String model,
        String endpoint,
        String source,
        String outcome,
        Integer httpStatus,
        String errorClass,
        Boolean streaming,
        Long durationMillis,
        Long firstByteMillis,
        Long firstTokenMillis,
        Long streamCompleteMillis,
        String traceId,
        String ucid,
        Instant startedAt) {
}
