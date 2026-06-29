package com.icbc.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 通用参数 MyBatis mapper；SQL 必须维护在 XML 中，接口只声明入参与返回值。
 */
@Mapper
public interface CommonParameterMapper {

    CommonParameterRow findByEnglishNameAndPlatform(
            @Param("englishName") String englishName,
            @Param("platform") String platform);

    List<CommonParameterRow> findAll();

    CommonParameterRow findByParameterId(@Param("parameterId") String parameterId);

    int updateValue(
            @Param("parameterId") String parameterId,
            @Param("value") String value,
            @Param("updatedAt") Instant updatedAt);
}
