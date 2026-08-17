package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 用户 SCM Git 姓名 MyBatis mapper；SQL 统一维护在 XML。 */
@Mapper
public interface UserScmGitIdentityMapper {

    UserScmGitIdentityRow findByUserId(@Param("userId") String userId);

    List<UserScmGitIdentityCandidateRow> findSyncCandidatesAfter(
            @Param("afterUserId") String afterUserId,
            @Param("limit") int limit);

    int upsertAccepted(
            @Param("rows") List<UserScmGitIdentityRow> rows,
            @Param("now") Instant now);

    int upsertRemoteRejection(
            @Param("row") UserScmGitIdentityRow row,
            @Param("now") Instant now);
}
