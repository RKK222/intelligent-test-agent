package com.icbc.testagent.persistence.mybatis;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** Run 级反馈 MyBatis mapper；SQL 统一维护在 XML。 */
@Mapper
public interface AiRunFeedbackMapper {

    AiMessageFeedbackRow findByUserIdAndRunId(
            @Param("userId") String userId,
            @Param("runId") String runId);

    List<AiMessageFeedbackRow> findByUserIdAndRunIds(
            @Param("userId") String userId,
            @Param("runIds") List<String> runIds);

    AiMessageFeedbackRow findByFeedbackId(@Param("feedbackId") String feedbackId);

    int insert(AiMessageFeedbackRow row);

    int update(AiMessageFeedbackRow row);
}
