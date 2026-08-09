package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessage;
import com.enterprise.testagent.domain.session.SessionMessageId;
import com.enterprise.testagent.domain.session.SessionMessageRepository;
import com.enterprise.testagent.domain.session.SessionMessageRole;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.persistence.mybatis.MyBatisSessionMessageRepository;
import com.enterprise.testagent.persistence.mybatis.SessionMessageMapper;
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

/** 验证会话消息通过 MyBatis XML 完整保存分享代操作归因。 */
class MyBatisSessionMessageRepositoryIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-09T08:00:00Z");
    private static final SessionId SESSION = new SessionId("ses_message_share_test");
    private static final UserId ACTOR = new UserId("usr_message_share_actor");

    private SingleConnectionDataSource dataSource;
    private SessionMessageRepository repository;

    @BeforeEach
    void setUp() throws Exception {
        dataSource = new SingleConnectionDataSource(
                "jdbc:h2:mem:testagent_message_%s;MODE=PostgreSQL;DATABASE_TO_LOWER=true"
                        .formatted(UUID.randomUUID().toString().replace("-", "")),
                "sa", "", true);
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .target("20260715213000").load().migrate();
        JdbcClient jdbc = JdbcClient.create(dataSource);
        jdbc.sql("alter table session_messages add column sender_unified_auth_id varchar(128)").update();
        jdbc.sql("alter table session_messages add column sent_by_shared_user boolean not null default false").update();
        seed(jdbc);

        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        factoryBean.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory factory = factoryBean.getObject();
        SessionMessageMapper mapper = new SqlSessionTemplate(factory).getMapper(SessionMessageMapper.class);
        repository = new MyBatisSessionMessageRepository(mapper);
    }

    @AfterEach
    void tearDown() {
        dataSource.destroy();
    }

    @Test
    void roundTripsDelegatedSenderAndPagesMessages() {
        SessionMessage message = new SessionMessage(
                new SessionMessageId("msg_message_share_test"), SESSION, SessionMessageRole.USER,
                "代所属人执行测试", NOW, "trace_message_share")
                .withSender(ACTOR, "ucid_message_share", true);

        repository.save(message);

        assertThat(repository.findById(message.messageId())).contains(message);
        assertThat(repository.findBySessionId(SESSION, new PageRequest(1, 20)).items())
                .singleElement()
                .satisfies(saved -> {
                    assertThat(saved.senderUserId()).isEqualTo(ACTOR);
                    assertThat(saved.senderUnifiedAuthId()).isEqualTo("ucid_message_share");
                    assertThat(saved.sentBySharedUser()).isTrue();
                });
    }

    private void seed(JdbcClient jdbc) {
        jdbc.sql("insert into users(user_id,unified_auth_id,username,password_hash,status,created_at,updated_at) "
                        + "values(:userId,'ucid_message_share','分享用户','hash','ACTIVE',:now,:now)")
                .param("userId", ACTOR.value()).param("now", NOW).update();
        jdbc.sql("insert into workspaces(workspace_id,name,root_path,status,trace_id,created_at,updated_at) "
                        + "values('wrk_message_share','消息工作区','/tmp/message','ACTIVE','trace_message',:now,:now)")
                .param("now", NOW).update();
        jdbc.sql("insert into sessions(session_id,workspace_id,title,status,trace_id,created_at,updated_at) "
                        + "values(:sessionId,'wrk_message_share','消息会话','ACTIVE','trace_message',:now,:now)")
                .param("sessionId", SESSION.value()).param("now", NOW).update();
    }
}
