package com.enterprise.testagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.domain.workflowcapability.CheckoutTicketPayload;
import com.enterprise.testagent.domain.workflowcapability.WorkflowModelGrantPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/** 在企业基线Redis 5上锁定一次性checkout和同token滑动模型委托语义。 */
@Testcontainers(disabledWithoutDocker = true)
class RedisWorkflowCapabilityStoreIntegrationTest {

    private static final String TICKET_DIGEST = "a".repeat(64);
    private static final String GRANT_DIGEST = "b".repeat(64);

    @Container
    private static final GenericContainer<?> REDIS = new GenericContainer<>(
            DockerImageName.parse("redis:5.0.14-alpine"))
            .withExposedPorts(6379);

    @Test
    void consumesCheckoutOnceAndRefreshesTheSameOpaqueGrantAtomically() {
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(
                REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        try {
            StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
            redis.afterPropertiesSet();
            RedisWorkflowCapabilityStore store = new RedisWorkflowCapabilityStore(
                    redis,
                    new ObjectMapper().findAndRegisterModules());
            Instant initialExpiry = Instant.now().plusSeconds(60);
            store.saveCheckoutTicket(
                    TICKET_DIGEST,
                    new CheckoutTicketPayload(
                            "usr_12345678",
                            "c".repeat(64),
                            "repo_12345678",
                            "task_12345678",
                            "run_12345678",
                            "runner-a",
                            "runner-public-key",
                            "feature/impact",
                            "main",
                            initialExpiry),
                    Duration.ofSeconds(60));

            assertThat(store.consumeCheckoutTicket(TICKET_DIGEST)).isPresent();
            assertThat(store.consumeCheckoutTicket(TICKET_DIGEST)).isEmpty();

            String grantId = "wfgrantid_12345678";
            WorkflowModelGrantPayload initial = grant(grantId, initialExpiry);
            store.saveModelGrant(grantId, GRANT_DIGEST, initial, Duration.ofSeconds(30));
            Instant refreshedExpiry = initialExpiry.plusSeconds(60);

            assertThat(store.refreshModelGrant(
                            grantId,
                            grant(grantId, refreshedExpiry),
                            Duration.ofSeconds(90)))
                    .isTrue();
            assertThat(store.findModelGrant(GRANT_DIGEST))
                    .get()
                    .extracting(WorkflowModelGrantPayload::grantId, WorkflowModelGrantPayload::expiresAt)
                    .containsExactly(grantId, refreshedExpiry);
            assertThat(store.findModelGrantById(grantId)).isPresent();

            store.revokeModelGrants(
                    "task_12345678", "run_12345678", Duration.ofSeconds(90));
            assertThat(store.findModelGrant(GRANT_DIGEST)).isEmpty();
            assertThat(store.findModelGrantById(grantId)).isEmpty();

            String lateGrantId = "wfgrantid_late_12345678";
            String lateGrantDigest = "d".repeat(64);
            store.saveModelGrant(
                    lateGrantId,
                    lateGrantDigest,
                    grant(lateGrantId, refreshedExpiry),
                    Duration.ofSeconds(90));
            assertThat(store.findModelGrant(lateGrantDigest)).isEmpty();
            assertThat(store.findModelGrantById(lateGrantId)).isEmpty();
            Set<String> keys = redis.keys("*");
            assertThat(keys).allMatch(key -> key.startsWith("test-agent:workflow-capability:"));
        } finally {
            connectionFactory.destroy();
        }
    }

    private static WorkflowModelGrantPayload grant(String grantId, Instant expiresAt) {
        return new WorkflowModelGrantPayload(
                grantId,
                "usr_12345678",
                "AUTH_12345678",
                "c".repeat(64),
                "workflow",
                "task_12345678",
                "run_12345678",
                List.of("codex", "agentscope-synthesis"),
                expiresAt);
    }
}
