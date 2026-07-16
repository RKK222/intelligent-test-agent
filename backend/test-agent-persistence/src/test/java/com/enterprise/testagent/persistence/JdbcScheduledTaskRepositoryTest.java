package com.enterprise.testagent.persistence;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskKey;
import com.enterprise.testagent.domain.scheduler.ScheduledTaskRun;
import java.sql.Types;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;

class JdbcScheduledTaskRepositoryTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void findActiveRunByTaskKeyBindsNullExclusionAsVarcharForPostgreSql() {
        JdbcClient jdbcClient = mock(JdbcClient.class);
        JdbcClient.StatementSpec statement = mock(JdbcClient.StatementSpec.class);
        JdbcClient.MappedQuerySpec<ScheduledTaskRun> query = mock(JdbcClient.MappedQuerySpec.class);
        when(jdbcClient.sql(anyString())).thenReturn(statement);
        when(statement.param(anyString(), any())).thenReturn(statement);
        when(statement.param(eq("excludedTaskRunId"), isNull(), eq(Types.VARCHAR))).thenReturn(statement);
        when(statement.params(anyMap())).thenReturn(statement);
        when(statement.query(any(RowMapper.class))).thenReturn(query);
        when(query.optional()).thenReturn(Optional.empty());
        JdbcScheduledTaskRepository repository = new JdbcScheduledTaskRepository(
                jdbcClient,
                new ObjectMapper().findAndRegisterModules());

        repository.findActiveRunByTaskKey(new ScheduledTaskKey("daily.cleanup"));

        verify(statement).param("excludedTaskRunId", null, Types.VARCHAR);
    }
}
