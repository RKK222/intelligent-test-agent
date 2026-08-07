package com.enterprise.testagent.persistence.mybatis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.user.UserId;
import org.junit.jupiter.api.Test;

/** 批量会话归因适配器只负责 ID 转换，所有关系型 SQL 留在 XML。 */
class MyBatisBatchSessionAttributionRepositoryTest {

    @Test
    void delegatesLookupLockAndMarkToDedicatedMapper() {
        BatchSessionAttributionMapper mapper = mock(BatchSessionAttributionMapper.class);
        MyBatisBatchSessionAttributionRepository repository =
                new MyBatisBatchSessionAttributionRepository(mapper);
        UserId userId = new UserId("usr_batch_mapper");
        SessionId sessionId = new SessionId("ses_batch_mapper");
        when(mapper.findSessionId("usr_batch_mapper", "batch_item_mapper"))
                .thenReturn("ses_batch_mapper");
        when(mapper.markBatch(
                "ses_batch_mapper", "usr_batch_mapper", "batch_mapper", "batch_item_mapper"))
                .thenReturn(1);

        assertThat(repository.findSessionId(userId, "batch_item_mapper"))
                .contains(sessionId);
        repository.lockCreateRequest(userId, "batch_item_mapper");
        assertThat(repository.markBatch(
                sessionId, userId, "batch_mapper", "batch_item_mapper")).isTrue();

        verify(mapper).lockCreateRequest("usr_batch_mapper:batch_item_mapper");
        verify(mapper).markBatch(
                "ses_batch_mapper", "usr_batch_mapper", "batch_mapper", "batch_item_mapper");
    }
}
