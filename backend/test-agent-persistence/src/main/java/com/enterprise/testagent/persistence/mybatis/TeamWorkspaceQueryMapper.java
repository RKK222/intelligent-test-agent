package com.enterprise.testagent.persistence.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 团队工作空间组合查询 mapper。 */
@Mapper
public interface TeamWorkspaceQueryMapper {

    List<TeamWorkspaceApplicationRow> findApplications(
            @Param("globalScope") boolean globalScope,
            @Param("ownerUserId") String ownerUserId);

    List<TeamWorkspaceTemplateRow> findWorkspaceTemplates(
            @Param("globalScope") boolean globalScope,
            @Param("ownerUserId") String ownerUserId,
            @Param("appId") String appId);

    List<TeamWorkspaceVersionRow> findWorkspaceVersions(
            @Param("globalScope") boolean globalScope,
            @Param("ownerUserId") String ownerUserId,
            @Param("applicationWorkspaceId") String applicationWorkspaceId);

    List<TeamWorkspaceContributionRow> findContributions(
            @Param("globalScope") boolean globalScope,
            @Param("ownerUserId") String ownerUserId,
            @Param("versionId") String versionId);

    TeamWorkspaceContributionRow findPersonalWorkspace(
            @Param("globalScope") boolean globalScope,
            @Param("ownerUserId") String ownerUserId,
            @Param("personalWorkspaceId") String personalWorkspaceId);
}
