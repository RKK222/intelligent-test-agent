package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionTitleUpdateRepository;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.persistence.mybatis.MyBatisSessionTitleUpdateRepository;
import com.enterprise.testagent.persistence.mybatis.SessionTitleUpdateMapper;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.session.SqlSessionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

/**
 * 验证标题兜底使用 XML compare-and-set，避免异步旧快照覆盖新标题。
 */
class MyBatisSessionTitleUpdateRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-07-10T12:00:00Z");
    private static final WorkspaceId WORKSPACE_ID = new WorkspaceId("wrk_title1234567890abcdef");
    private static final SessionId SESSION_ID = new SessionId("ses_title1234567890abcdef");

    private SingleConnectionDataSource dataSource;
    private JdbcClient jdbcClient;
    private SessionTitleUpdateRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_title_%s;MODE=PostgreSQL;DATABASE_TO_UPPER=false"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa",
                "",
                true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbcClient = JdbcClient.create(dataSource);
        seedSession();
        SqlSessionFactory factory = sqlSessionFactory();
        repository = new MyBatisSessionTitleUpdateRepository(
                new SqlSessionTemplate(factory).getMapper(SessionTitleUpdateMapper.class));
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void updateTitleIfCurrentDoesNotOverwriteChangedTitle() {
        assertThat(repository.updateTitleIfCurrent(
                        SESSION_ID,
                        "首条消息临时标题",
                        "OpenCode 原生标题",
                        NOW.plusSeconds(1),
                        "trace_title1234567890abcdef"))
                .isTrue();
        assertThat(repository.updateTitleIfCurrent(
                        SESSION_ID,
                        "首条消息临时标题",
                        "过期兜底标题",
                        NOW.plusSeconds(2),
                        "trace_title1234567890abcdef"))
                .isFalse();
        assertThat(jdbcClient.sql("select title from sessions where session_id = :sessionId")
                        .param("sessionId", SESSION_ID.value())
                        .query(String.class)
                        .single())
                .isEqualTo("OpenCode 原生标题");
    }

    private void seedSession() {
        jdbcClient.sql("""
                insert into workspaces(workspace_id, name, root_path, status, trace_id, created_at, updated_at)
                values(:workspaceId, 'title workspace', '/tmp/title', 'ACTIVE', 'trace_title1234567890abcdef', :now, :now)
                """)
                .param("workspaceId", WORKSPACE_ID.value())
                .param("now", NOW)
                .update();
        jdbcClient.sql("""
                insert into sessions(session_id, workspace_id, title, status, trace_id, created_at, updated_at)
                values(:sessionId, :workspaceId, '首条消息临时标题', 'ACTIVE', 'trace_title1234567890abcdef', :now, :now)
                """)
                .param("sessionId", SESSION_ID.value())
                .param("workspaceId", WORKSPACE_ID.value())
                .param("now", NOW)
                .update();
    }

    private SqlSessionFactory sqlSessionFactory() throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        return factoryBean.getObject();
    }
}
