package com.enterprise.testagent.persistence.mybatis;

import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 闲置 OpenCode 进程 MyBatis mapper；SQL 只维护在 XML 中。 */
@Mapper
public interface InactiveOpencodeProcessMapper {

    List<InactiveOpencodeProcessRow> findCandidates(
            @Param("linuxServerId") String linuxServerId,
            @Param("activityBefore") Instant activityBefore,
            @Param("limit") int limit);

    InactiveOpencodeProcessRow findCurrentCandidate(
            @Param("processId") String processId,
            @Param("activityBefore") Instant activityBefore);
}
