package com.icbc.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 配置管理 MyBatis mapper；SQL 必须维护在 XML 中，接口只声明入参与返回值。
 */
@Mapper
public interface ConfigurationManagementMapper {

    List<ApplicationDefinitionRow> findApplications(@Param("enabledOnly") Boolean enabledOnly);

    ApplicationDefinitionRow findApplication(@Param("appId") String appId);

    List<ApplicationDefinitionRow> findApplicationsByMember(@Param("userId") String userId);

    long countActiveMember(@Param("appId") String appId, @Param("userId") String userId);

    List<ApplicationMemberRow> findActiveMembers(@Param("appId") String appId);

    long countMember(@Param("appId") String appId, @Param("userId") String userId);

    int reactivateMember(
            @Param("appId") String appId,
            @Param("userId") String userId,
            @Param("updatedAt") Instant updatedAt);

    int insertMember(
            @Param("appId") String appId,
            @Param("userId") String userId,
            @Param("createdAt") Instant createdAt,
            @Param("updatedAt") Instant updatedAt,
            @Param("deletedAt") Instant deletedAt);

    int deleteMember(@Param("appId") String appId, @Param("userId") String userId);

    List<CodeRepositoryRow> findRepositories(@Param("limit") int limit, @Param("offset") long offset);

    long countRepositories();

    CodeRepositoryRow findRepository(@Param("repositoryId") String repositoryId);

    CodeRepositoryRow findRepositoryByGitUrl(@Param("gitUrl") String gitUrl);

    CodeRepositoryRow findRepositoryByEnglishName(@Param("englishName") String englishName);

    int insertRepository(CodeRepositoryRow repository);

    int updateRepositoryMetadata(CodeRepositoryRow repository);

    List<CodeRepositoryRow> findRepositoriesByApplication(@Param("appId") String appId);

    List<ApplicationDefinitionRow> findApplicationsByRepository(@Param("repositoryId") String repositoryId);

    long countRepositoryLink(@Param("appId") String appId, @Param("repositoryId") String repositoryId);

    int insertRepositoryLink(@Param("appId") String appId, @Param("repositoryId") String repositoryId);

    int deleteRepositoryLink(@Param("appId") String appId, @Param("repositoryId") String repositoryId);

    List<ApplicationWorkspaceRow> findWorkspaces(@Param("appId") String appId);

    ApplicationWorkspaceRow findWorkspace(@Param("workspaceId") String workspaceId);

    ApplicationWorkspaceRow findWorkspaceByLocation(
            @Param("appId") String appId,
            @Param("repositoryId") String repositoryId,
            @Param("branch") String branch,
            @Param("directoryPath") String directoryPath);

    int insertWorkspace(ApplicationWorkspaceRow workspace);

    int updateWorkspace(ApplicationWorkspaceRow workspace);

    int deleteWorkspace(@Param("workspaceId") String workspaceId);

    List<UserSshKeyRow> findSshKeys(@Param("userId") String userId);

    UserSshKeyRow findSshKey(@Param("userId") String userId, @Param("sshKeyId") String sshKeyId);

    int insertSshKey(UserSshKeyRow sshKey);

    int deleteSshKey(@Param("userId") String userId, @Param("sshKeyId") String sshKeyId);
}
