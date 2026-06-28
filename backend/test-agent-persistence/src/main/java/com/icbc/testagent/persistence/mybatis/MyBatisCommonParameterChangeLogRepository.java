package com.icbc.testagent.persistence.mybatis;

import com.icbc.testagent.domain.configuration.CommonParameterChangeLog;
import com.icbc.testagent.domain.configuration.CommonParameterChangeLogRepository;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * CommonParameterChangeLog 的 MyBatis Repository 实现，负责领域端口和 XML mapper 之间的转换。
 */
@Repository
public class MyBatisCommonParameterChangeLogRepository implements CommonParameterChangeLogRepository {

    private final CommonParameterChangeLogMapper mapper;

    /**
     * 注入 MyBatis mapper；连接、事务和 SQL 执行由 MyBatis-Spring 管理。
     */
    public MyBatisCommonParameterChangeLogRepository(CommonParameterChangeLogMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void save(CommonParameterChangeLog log) {
        mapper.insert(toRow(log));
    }

    @Override
    public List<CommonParameterChangeLog> findByParameterId(String parameterId, int limit) {
        return mapper.findByParameterId(parameterId, limit).stream()
                .map(this::toDomain)
                .toList();
    }

    /**
     * 将领域对象转换为表行模型。
     */
    private CommonParameterChangeLogRow toRow(CommonParameterChangeLog log) {
        return new CommonParameterChangeLogRow(
                log.logId(),
                log.parameterId(),
                log.oldValue(),
                log.newValue(),
                log.changedByUserId(),
                log.changedByUsername(),
                log.traceId(),
                log.createdAt());
    }

    /**
     * 将表行模型转换为领域对象。
     */
    private CommonParameterChangeLog toDomain(CommonParameterChangeLogRow row) {
        return new CommonParameterChangeLog(
                row.logId(),
                row.parameterId(),
                row.oldValue(),
                row.newValue(),
                row.changedByUserId(),
                row.changedByUsername(),
                row.traceId(),
                row.createdAt());
    }
}
