package com.enterprise.testagent.persistence.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 自动化代码库当前版本 MyBatis mapper；所有关系型 SQL 维护在 XML 中。 */
@Mapper
public interface AutomationWorkspaceActiveVersionMapper {

    AutomationWorkspaceActiveVersionRow find(@Param("applicationWorkspaceId") String applicationWorkspaceId);

    List<AutomationWorkspaceActiveVersionRow> findByApplicationWorkspaceIds(
            @Param("applicationWorkspaceIds") List<String> applicationWorkspaceIds);

    int insertIfAbsent(@Param("row") AutomationWorkspaceActiveVersionRow row);

    int update(@Param("row") AutomationWorkspaceActiveVersionRow row);
}
