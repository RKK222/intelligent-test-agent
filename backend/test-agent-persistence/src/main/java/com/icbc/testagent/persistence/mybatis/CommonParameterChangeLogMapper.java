package com.icbc.testagent.persistence.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 通用参数修改日志 MyBatis mapper；SQL 必须维护在 XML 中，接口只声明入参与返回值。
 */
@Mapper
public interface CommonParameterChangeLogMapper {

    void insert(CommonParameterChangeLogRow row);

    List<CommonParameterChangeLogRow> findByParameterId(
            @Param("parameterId") String parameterId,
            @Param("limit") int limit);
}
