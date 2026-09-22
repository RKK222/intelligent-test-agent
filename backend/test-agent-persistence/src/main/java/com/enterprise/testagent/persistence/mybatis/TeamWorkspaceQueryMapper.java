package com.enterprise.testagent.persistence.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 团队工作空间组合查询 mapper。 */
@Mapper
public interface TeamWorkspaceQueryMapper {

    List<TeamWorkspaceApplicationRow> findApplications(
            @Param("globalScope") boolean globalScope,
            @Param("ownerUserId") String ownerUserId,
            @Param("targetUserId") String targetUserId);

    List<TeamWorkspaceTemplateRow> findWorkspaceTemplates(
            @Param("globalScope") boolean globalScope,
            @Param("ownerUserId") String ownerUserId,
            @Param("appId") String appId,
            @Param("targetUserId") String targetUserId);

    List<TeamWorkspaceVersionRow> findWorkspaceVersions(
            @Param("globalScope") boolean globalScope,
            @Param("ownerUserId") String ownerUserId,
            @Param("applicationWorkspaceId") String applicationWorkspaceId,
            @Param("targetUserId") String targetUserId);

    List<TeamWorkspaceContributionRow> findContributions(
            @Param("globalScope") boolean globalScope,
            @Param("ownerUserId") String ownerUserId,
            @Param("versionId") String versionId);

    TeamWorkspaceContributionRow findPersonalWorkspace(
            @Param("globalScope") boolean globalScope,
            @Param("ownerUserId") String ownerUserId,
            @Param("personalWorkspaceId") String personalWorkspaceId);
}
