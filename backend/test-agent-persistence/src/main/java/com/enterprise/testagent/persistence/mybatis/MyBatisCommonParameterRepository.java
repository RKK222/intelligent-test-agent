package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.configuration.CommonParameter;
import com.enterprise.testagent.domain.configuration.CommonParameterRepository;
import com.enterprise.testagent.domain.configuration.ParameterPlatform;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * CommonParameter 的 MyBatis Repository 实现，负责领域端口和 XML mapper 之间的转换。
 */
@Repository
public class MyBatisCommonParameterRepository implements CommonParameterRepository {

    private final CommonParameterMapper mapper;

    /**
     * 注入 MyBatis mapper；连接、事务和 SQL 执行由 MyBatis-Spring 管理。
     */
    public MyBatisCommonParameterRepository(CommonParameterMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public Optional<CommonParameter> findByEnglishNameAndPlatform(String englishName, ParameterPlatform platform) {
        return Optional.ofNullable(mapper.findByEnglishNameAndPlatform(englishName, platform.value()))
                .map(this::toDomain);
    }

    @Override
    public List<CommonParameter> findAll() {
        return mapper.findAll().stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public Optional<CommonParameter> findByParameterId(String parameterId) {
        return Optional.ofNullable(mapper.findByParameterId(parameterId))
                .map(this::toDomain);
    }

    @Override
    public int updateValue(String parameterId, String newValue, Instant updatedAt) {
        return mapper.updateValue(parameterId, newValue, updatedAt);
    }

    /**
     * 将表行模型转换为领域对象，平台枚举和值对象校验继续复用领域构造器。
     */
    private CommonParameter toDomain(CommonParameterRow row) {
        return new CommonParameter(
                row.parameterId(),
                row.parameterEnglish(),
                row.parameterChinese(),
                row.parameterValue(),
                ParameterPlatform.fromValue(row.platform()),
                row.editable(),
                row.createdAt(),
                row.updatedAt());
    }
}
