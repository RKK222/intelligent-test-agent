package com.icbc.testagent.persistence.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 用户级会话运行态 MyBatis Mapper，SQL 统一维护在 XML 中。
 */
@Mapper
public interface SessionRuntimeStateMapper {

    List<SessionRuntimeStateRow> findUserRuntimeState(@Param("userId") String userId);
}
