package com.enterprise.testagent.opencode.runtime.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.enterprise.testagent.domain.session.BatchSessionAttributionRepository;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 验证批量单项 Session 的用户级幂等与归因写入。 */
class BatchSessionApplicationServiceTest {

    private static final UserId USER = new UserId("usr_batch_session");
    private static final WorkspaceId WORKSPACE = new WorkspaceId("wrk_batch_session");
    private static final SessionId SESSION_ID = new SessionId("ses_batch_session");
    private static final BatchContext CONTEXT = new BatchContext("batch_20260807", "batch_item_01");

    private SessionApplicationService sessionService;
    private BatchSessionAttributionRepository attributionRepository;
    private BatchSessionApplicationService service;

    @BeforeEach
    void setUp() {
        sessionService = mock(SessionApplicationService.class);
        attributionRepository = mock(BatchSessionAttributionRepository.class);
        service = new BatchSessionApplicationService(sessionService, attributionRepository);
    }

    @Test
    void createsManualSessionAndMarksBatchAttributionAfterLockingRequest() {
        Session created = session();
        when(attributionRepository.findSessionId(USER, CONTEXT.itemRequestId()))
                .thenReturn(Optional.empty());
        when(sessionService.createSession(USER, WORKSPACE, "需求一 登录 测试案例", "trace_batch"))
                .thenReturn(created);
        when(attributionRepository.markBatch(
                SESSION_ID, USER, CONTEXT.batchId(), CONTEXT.itemRequestId()))
                .thenReturn(true);

        Session result = service.create(
                USER, WORKSPACE, "需求一 登录 测试案例", CONTEXT, "trace_batch");

        assertThat(result).isSameAs(created);
        assertThat(result.sourceType()).isEqualTo(ConversationSourceType.MANUAL);
        verify(attributionRepository).lockCreateRequest(USER, CONTEXT.itemRequestId());
        verify(attributionRepository).markBatch(
                SESSION_ID, USER, CONTEXT.batchId(), CONTEXT.itemRequestId());
    }

    @Test
    void returnsExistingSessionForSameUserAndItemRequest() {
        Session existing = session();
        when(attributionRepository.findSessionId(USER, CONTEXT.itemRequestId()))
                .thenReturn(Optional.of(SESSION_ID));
        when(sessionService.getSession(USER, SESSION_ID, true)).thenReturn(existing);

        Session result = service.create(
                USER, WORKSPACE, "会被忽略的标题", CONTEXT, "trace_batch_retry");

        assertThat(result).isSameAs(existing);
        verify(attributionRepository, never()).lockCreateRequest(USER, CONTEXT.itemRequestId());
        verify(sessionService, never()).createSession(USER, WORKSPACE, "会被忽略的标题", "trace_batch_retry");
        verify(attributionRepository, never()).markBatch(
                SESSION_ID, USER, CONTEXT.batchId(), CONTEXT.itemRequestId());
    }

    private static Session session() {
        Instant now = Instant.parse("2026-08-07T12:00:00Z");
        return new Session(SESSION_ID, WORKSPACE, "需求一 登录 测试案例", SessionStatus.ACTIVE,
                now, now, "trace_batch").withSource(ConversationSourceType.MANUAL, null, USER);
    }
}
