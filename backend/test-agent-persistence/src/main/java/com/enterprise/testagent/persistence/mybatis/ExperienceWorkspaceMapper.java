package com.enterprise.testagent.persistence.mybatis;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 体验工作区 MyBatis mapper；所有关系型 SQL 维护在 XML 中。 */
@Mapper
public interface ExperienceWorkspaceMapper {

    ExperienceWorkspaceBindingRow findCurrentByLinuxServerId(@Param("linuxServerId") String linuxServerId);

    int insertWorkspaceIfAbsent(ExperienceWorkspaceRow workspace);

    int insertCurrentBindingIfAbsent(ExperienceWorkspaceBindingRow binding);

    int updateCurrentBindingIfMatches(
            @Param("binding") ExperienceWorkspaceBindingRow binding,
            @Param("expected") ExperienceWorkspaceBindingRow expected);
}
