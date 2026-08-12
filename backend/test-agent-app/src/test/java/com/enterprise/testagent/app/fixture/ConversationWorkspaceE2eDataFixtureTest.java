package com.enterprise.testagent.app.fixture;

import static org.assertj.core.api.Assertions.assertThat;

import com.enterprise.testagent.common.pagination.PageRequest;
import com.enterprise.testagent.domain.configuration.ApplicationDefinition;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ApplicationMember;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspace;
import com.enterprise.testagent.domain.configuration.ApplicationWorkspaceId;
import com.enterprise.testagent.domain.configuration.CodeRepository;
import com.enterprise.testagent.domain.configuration.CodeRepositoryDeploymentMode;
import com.enterprise.testagent.domain.configuration.CodeRepositoryId;
import com.enterprise.testagent.domain.configuration.CodeRepositoryType;
import com.enterprise.testagent.domain.event.RunEventDraft;
import com.enterprise.testagent.domain.event.RunEventType;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersion;
import com.enterprise.testagent.domain.managedworkspace.ApplicationWorkspaceVersionId;
import com.enterprise.testagent.domain.managedworkspace.ManagedWorkspaceStatus;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspace;
import com.enterprise.testagent.domain.managedworkspace.PersonalWorkspaceId;
import com.enterprise.testagent.domain.run.Run;
import com.enterprise.testagent.domain.run.RunId;
import com.enterprise.testagent.domain.run.RunPersistenceAnchor;
import com.enterprise.testagent.domain.run.RunRuntimeInput;
import com.enterprise.testagent.domain.run.RunRuntimeManifest;
import com.enterprise.testagent.domain.run.RunStorageMode;
import com.enterprise.testagent.domain.run.RunStatus;
import com.enterprise.testagent.domain.run.TokenUsage;
import com.enterprise.testagent.domain.session.ConversationSourceType;
import com.enterprise.testagent.domain.session.Session;
import com.enterprise.testagent.domain.session.SessionId;
import com.enterprise.testagent.domain.session.SessionMessage;
import com.enterprise.testagent.domain.session.SessionMessageId;
import com.enterprise.testagent.domain.session.SessionMessageRole;
import com.enterprise.testagent.domain.session.SessionStatus;
import com.enterprise.testagent.domain.user.UserId;
import com.enterprise.testagent.domain.workspace.Workspace;
import com.enterprise.testagent.domain.workspace.WorkspaceId;
import com.enterprise.testagent.domain.workspace.WorkspaceStatus;
import com.enterprise.testagent.persistence.JdbcManagedWorkspaceRepository;
import com.enterprise.testagent.persistence.JdbcSessionRepository;
import com.enterprise.testagent.persistence.JdbcWorkspaceRepository;
import com.enterprise.testagent.persistence.RedisRunRuntimeStore;
import com.enterprise.testagent.persistence.mybatis.ConfigurationManagementMapper;
import com.enterprise.testagent.persistence.mybatis.MyBatisConfigurationManagementRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisRunRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisRunSummaryPersistenceRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisSessionHistoryRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisSessionMessageRepository;
import com.enterprise.testagent.persistence.mybatis.MyBatisUserWorkspaceQueryRepository;
import com.enterprise.testagent.persistence.mybatis.PersonalWorkspaceMapper;
import com.enterprise.testagent.persistence.mybatis.RunMapper;
import com.enterprise.testagent.persistence.mybatis.RunSummaryMapper;
import com.enterprise.testagent.persistence.mybatis.SessionHistoryMapper;
import com.enterprise.testagent.persistence.mybatis.SessionMessageMapper;
import com.enterprise.testagent.persistence.mybatis.UserWorkspaceQueryMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.apache.ibatis.mapping.VendorDatabaseIdProvider;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * 仅由显式本地脚本启用的对话与工作空间 E2E 造数夹具。
 *
 * <p>关系数据只通过既有 Repository 写入，运行态 permission/subagent 通过既有 Redis Run 数据面写入；
 * 固定业务 ID 让脚本可以重复执行，且不会把演示数据放进生产 Flyway。</p>
 */
@EnabledIfSystemProperty(named = "testagent.conversation-workspace.e2e.enabled", matches = "true")
class ConversationWorkspaceE2eDataFixtureTest {

    private static final ApplicationId APP_ID = new ApplicationId("app_e2e_oss_quality");
    private static final CodeRepositoryId SPRING_REPOSITORY_ID = new CodeRepositoryId("repo_e2e_spring_petclinic");
    private static final CodeRepositoryId PLAYWRIGHT_REPOSITORY_ID = new CodeRepositoryId("repo_e2e_playwright");
    private static final CodeRepositoryId VUE_REPOSITORY_ID = new CodeRepositoryId("repo_e2e_vue_core");
    private static final ApplicationWorkspaceId APP_WORKSPACE_ID = new ApplicationWorkspaceId("awp_e2e_spring_petclinic");
    private static final ApplicationWorkspaceVersionId VERSION_ID = new ApplicationWorkspaceVersionId("awv_e2e_20260812");
    private static final PersonalWorkspaceId PERSONAL_WORKSPACE_ID = new PersonalWorkspaceId("per_e2e_oss_quality");
    private static final WorkspaceId VERSION_RUNTIME_ID = new WorkspaceId("wrk_e2e_oss_quality_version");
    private static final WorkspaceId PERSONAL_RUNTIME_ID = new WorkspaceId("wrk_e2e_oss_quality_personal");
    private static final String TRACE_ID = "trace_e2e_conversation_workspace_fixture";

    private static final List<ConversationScenario> SCENARIOS = List.of(
            new ConversationScenario(
                    "ordinary",
                    "[E2E] 普通对话：解释退款规则",
                    "请用三句话解释订单退款规则，并指出 100 元这个边界。",
                    "已支付且未全额退款的订单可以申请退款；累计退款金额不能超过实付金额；100 元及以下自动通过，超过 100 元进入人工复核。",
                    List.of(),
                    RunStatus.SUCCEEDED),
            new ConversationScenario(
                    "requirement",
                    "[E2E] 需求分析：订单退款",
                    "请阅读 requirements/order-refund-requirement.md，提取功能、非功能和安全验收点。",
                    "已提取金额规则、幂等、人工复核、渠道超时补偿、对象级权限、性能与日志脱敏要求，并建议覆盖并发重复提交。",
                    List.of(
                            toolPart("part_e2e_requirement_read", "requirement", "read", "completed",
                                    Map.of("path", "requirements/order-refund-requirement.md"),
                                    "读取需求文档完成")),
                    RunStatus.SUCCEEDED),
            new ConversationScenario(
                    "design",
                    "[E2E] 详细设计评审：订单退款",
                    "评审 docs/design/order-refund-detailed-design.md，重点检查状态机、事务和超时恢复。",
                    "设计覆盖了终态不可回退、幂等键唯一约束、乐观锁和超时补偿；建议补充回调乱序与并发审核的案例。",
                    List.of(
                            toolPart("part_e2e_design_read", "design", "read", "completed",
                                    Map.of("path", "docs/design/order-refund-detailed-design.md"),
                                    "读取详细设计完成")),
                    RunStatus.SUCCEEDED),
            new ConversationScenario(
                    "skill",
                    "[E2E] Skill：从详细设计生成案例",
                    "@e2e-detailed-design-cases 根据需求和详细设计生成退款测试案例。",
                    "已调用 E2E 详细设计案例生成 Skill，产出正常、边界、幂等、权限和超时恢复案例，样例见 test-data/refund-cases.csv。",
                    List.of(
                            toolPart("part_e2e_skill", "skill", "skill", "completed",
                                    Map.of(
                                            "name", "e2e-detailed-design-cases",
                                            "paths", List.of(
                                                    "requirements/order-refund-requirement.md",
                                                    "docs/design/order-refund-detailed-design.md")),
                                    "Skill 已生成 5 条代表性测试案例")),
                    RunStatus.SUCCEEDED),
            new ConversationScenario(
                    "agent",
                    "[E2E] Agent：接口安全与兼容评审",
                    "@e2e-api-reviewer 评审退款接口的权限、幂等、日志脱敏和向后兼容。",
                    "Agent 发现四项重点：对象级鉴权、幂等键冲突语义、渠道超时的稳定错误转换，以及新增响应字段保持可选。",
                    List.of(
                            Map.of(
                                    "id", "part_e2e_agent_reference",
                                    "type", "agent",
                                    "name", "e2e-api-reviewer",
                                    "source", Map.of("value", "@e2e-api-reviewer", "start", 0, "end", 17)),
                            toolPart("part_e2e_agent_read", "agent", "read", "completed",
                                    Map.of("path", "docs/design/order-refund-detailed-design.md"),
                                    "Agent 已完成接口评审")),
                    RunStatus.SUCCEEDED),
            new ConversationScenario(
                    "permission",
                    "[E2E] Permission：执行只读测试",
                    "请运行 ./mvnw test -Dtest=RefundServiceTest 验证退款服务。",
                    "准备执行测试命令，当前等待用户授权。",
                    List.of(),
                    RunStatus.RUNNING),
            new ConversationScenario(
                    "subagent",
                    "[E2E] Subagent：并行分析前后端",
                    "请召唤子智能体，并行分析退款接口后端状态机与前端错误展示。",
                    "已由两个分析方向并行检查；子智能体卡片包含后端状态机结论，并汇总了前端错误展示建议。",
                    List.of(taskPart()),
                    RunStatus.SUCCEEDED));

    @Test
    void seedOpenSourceRepositoriesWorkspaceDocumentsAndConversationScenes() throws Exception {
        Map<String, String> environment = readEnvironmentFile(Path.of(requiredProperty(
                "testagent.conversation-workspace.e2e.env-file")));
        Path workspaceRoot = Path.of(requiredProperty(
                "testagent.conversation-workspace.e2e.workspace-root")).toAbsolutePath().normalize();
        String fixtureCommit = requiredProperty("testagent.conversation-workspace.e2e.commit");
        String linuxServerId = requiredProperty("testagent.conversation-workspace.e2e.linux-server-id");
        UserId userId = new UserId(System.getProperty(
                "testagent.conversation-workspace.e2e.user-id", "usr_test_dev"));
        assertWorkspaceFixture(workspaceRoot);

        DriverManagerDataSource dataSource = dataSource(environment);
        SqlSessionTemplate template = sqlSessionTemplate(dataSource);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        JdbcClient jdbcClient = JdbcClient.create(dataSource);
        var configuration = new MyBatisConfigurationManagementRepository(
                template.getMapper(ConfigurationManagementMapper.class));
        var managed = new JdbcManagedWorkspaceRepository(
                jdbcClient, objectMapper, template.getMapper(PersonalWorkspaceMapper.class));
        var workspaces = new JdbcWorkspaceRepository(jdbcClient);
        var sessions = new JdbcSessionRepository(jdbcClient);
        var runs = new MyBatisRunRepository(template.getMapper(RunMapper.class));
        var runSummaries = new MyBatisRunSummaryPersistenceRepository(
                template.getMapper(RunSummaryMapper.class));
        var messages = new MyBatisSessionMessageRepository(template.getMapper(SessionMessageMapper.class));
        var history = new MyBatisSessionHistoryRepository(template.getMapper(SessionHistoryMapper.class));
        var userWorkspaces = new MyBatisUserWorkspaceQueryRepository(
                template.getMapper(UserWorkspaceQueryMapper.class));
        Instant now = Instant.now();

        seedWorkspace(configuration, managed, workspaces, workspaceRoot, fixtureCommit, linuxServerId, userId, now);
        Map<String, SeededConversation> seeded = seedConversations(
                sessions, runs, runSummaries, messages, objectMapper, userId, now);

        LettuceConnectionFactory redisConnection = redisConnection(environment);
        try {
            StringRedisTemplate redisTemplate = new StringRedisTemplate(redisConnection);
            redisTemplate.afterPropertiesSet();
            RedisRunRuntimeStore runtimeStore = new RedisRunRuntimeStore(redisTemplate, objectMapper);
            seedPermissionRuntime(runtimeStore, seeded.get("permission"), userId, workspaceRoot, now);
            seedSubagentRuntime(runtimeStore, seeded.get("subagent"), userId, workspaceRoot, now);

            assertThat(runtimeStore.findManifest(seeded.get("permission").runId()))
                    .get()
                    .extracting(RunRuntimeManifest::attention)
                    .isEqualTo("PERMISSION");
            assertThat(runtimeStore.replayAfter(seeded.get("subagent").runId(), 0, 100)
                    .snapshot().events())
                    .extracting(RunEventDraft::type)
                    .contains(RunEventType.SESSION_CHILD_DISCOVERED, RunEventType.MESSAGE_PART_UPDATED);
        } finally {
            redisConnection.destroy();
        }

        assertThat(configuration.findRepositoriesByApplication(APP_ID))
                .extracting(repository -> repository.repositoryId().value())
                .contains(SPRING_REPOSITORY_ID.value(), PLAYWRIGHT_REPOSITORY_ID.value(), VUE_REPOSITORY_ID.value());
        assertThat(userWorkspaces.findUserWorkspaces(userId, new PageRequest(1, 100)).items())
                .extracting(workspace -> workspace.workspaceId().value())
                .contains(PERSONAL_RUNTIME_ID.value());
        assertThat(history.findUserHistory(userId, "[E2E]", new PageRequest(1, 100)).items())
                .extracting(item -> item.session().title())
                .containsExactlyInAnyOrderElementsOf(SCENARIOS.stream().map(ConversationScenario::title).toList());
        assertThat(messages.findBySessionId(
                seeded.get("skill").sessionId(), new PageRequest(1, 100)).items())
                .hasSize(2);
    }

    /** 创建应用、公开仓库配置、版本工作区与当前用户个人工作区。 */
    private void seedWorkspace(
            MyBatisConfigurationManagementRepository configuration,
            JdbcManagedWorkspaceRepository managed,
            JdbcWorkspaceRepository workspaces,
            Path workspaceRoot,
            String fixtureCommit,
            String linuxServerId,
            UserId userId,
            Instant now) {
        if (configuration.findApplication(APP_ID).isEmpty()) {
            configuration.saveApplication(new ApplicationDefinition(
                    APP_ID, "E2E 开源项目质量验证", true, now, now));
        }
        configuration.saveMember(ApplicationMember.active(APP_ID, userId, now));
        seedRepository(configuration, new CodeRepository(
                SPRING_REPOSITORY_ID,
                "https://github.com/spring-projects/spring-petclinic.git",
                "Spring PetClinic 测试工作库",
                "e2e-spring-petclinic",
                CodeRepositoryType.TEST_WORK_REPOSITORY.value(),
                CodeRepositoryDeploymentMode.EXTERNAL.value(),
                true,
                now,
                now));
        seedRepository(configuration, new CodeRepository(
                PLAYWRIGHT_REPOSITORY_ID,
                "https://github.com/microsoft/playwright.git",
                "Playwright 自动化参考库",
                "e2e-playwright",
                CodeRepositoryType.APPLICATION_CODE_REPOSITORY.value(),
                CodeRepositoryDeploymentMode.EXTERNAL.value(),
                false,
                now,
                now));
        seedRepository(configuration, new CodeRepository(
                VUE_REPOSITORY_ID,
                "https://github.com/vuejs/core.git",
                "Vue 前端资产参考库",
                "e2e-vue-core",
                CodeRepositoryType.APPLICATION_ASSET_REPOSITORY.value(),
                CodeRepositoryDeploymentMode.EXTERNAL.value(),
                false,
                now,
                now));
        for (CodeRepositoryId repositoryId : List.of(
                SPRING_REPOSITORY_ID, PLAYWRIGHT_REPOSITORY_ID, VUE_REPOSITORY_ID)) {
            configuration.linkRepository(APP_ID, repositoryId);
        }
        if (configuration.findWorkspace(APP_WORKSPACE_ID).isEmpty()) {
            configuration.saveWorkspace(new ApplicationWorkspace(
                    APP_WORKSPACE_ID,
                    APP_ID,
                    SPRING_REPOSITORY_ID,
                    "main",
                    "/",
                    "Spring PetClinic 退款验证工作空间",
                    true,
                    now,
                    now));
        }

        workspaces.save(new Workspace(
                VERSION_RUNTIME_ID,
                "Spring PetClinic E2E 版本基线",
                workspaceRoot.toString(),
                WorkspaceStatus.ACTIVE,
                now,
                now,
                linuxServerId,
                TRACE_ID));
        if (managed.findVersion(VERSION_ID).isEmpty()) {
            managed.saveVersion(new ApplicationWorkspaceVersion(
                    VERSION_ID,
                    APP_WORKSPACE_ID,
                    APP_ID,
                    SPRING_REPOSITORY_ID,
                    "e2e-20260812",
                    "main",
                    workspaceRoot.toString(),
                    workspaceRoot.toString(),
                    VERSION_RUNTIME_ID,
                    userId,
                    ManagedWorkspaceStatus.ACTIVE,
                    fixtureCommit,
                    now,
                    now,
                    now));
        } else {
            managed.updateVersionTargetCommit(VERSION_ID, fixtureCommit, now);
        }
        workspaces.save(new Workspace(
                PERSONAL_RUNTIME_ID,
                "Spring PetClinic 退款验证工作空间",
                workspaceRoot.toString(),
                WorkspaceStatus.ACTIVE,
                now,
                now,
                linuxServerId,
                TRACE_ID));
        if (managed.findPersonalWorkspace(PERSONAL_WORKSPACE_ID).isEmpty()) {
            managed.savePersonalWorkspace(new PersonalWorkspace(
                    PERSONAL_WORKSPACE_ID,
                    VERSION_ID,
                    APP_ID,
                    APP_WORKSPACE_ID,
                    userId,
                    "E2E 默认工作区",
                    "main",
                    workspaceRoot.toString(),
                    workspaceRoot.toString(),
                    PERSONAL_RUNTIME_ID,
                    fixtureCommit,
                    ManagedWorkspaceStatus.ACTIVE,
                    now,
                    now));
        }
    }

    private void seedRepository(
            MyBatisConfigurationManagementRepository configuration,
            CodeRepository repository) {
        if (configuration.findRepository(repository.repositoryId()).isEmpty()) {
            configuration.saveRepository(repository);
        }
    }

    /** 保存七类可见历史会话；固定 ID 保证重复执行只更新 fixture 自身。 */
    private Map<String, SeededConversation> seedConversations(
            JdbcSessionRepository sessions,
            MyBatisRunRepository runs,
            MyBatisRunSummaryPersistenceRepository runSummaries,
            MyBatisSessionMessageRepository messages,
            ObjectMapper objectMapper,
            UserId userId,
            Instant now) throws Exception {
        Map<String, SeededConversation> seeded = new LinkedHashMap<>();
        for (int index = 0; index < SCENARIOS.size(); index++) {
            ConversationScenario scenario = SCENARIOS.get(index);
            SessionId sessionId = new SessionId("ses_e2e_" + scenario.key());
            RunId runId = new RunId("run_e2e_" + scenario.key());
            Instant createdAt = now.minus(Duration.ofDays(1)).plusSeconds(index * 60L);
            Instant updatedAt = now.minusSeconds((SCENARIOS.size() - index) * 5L);
            sessions.save(new Session(
                    sessionId,
                    PERSONAL_RUNTIME_ID,
                    scenario.title(),
                    SessionStatus.ACTIVE,
                    createdAt,
                    updatedAt,
                    TRACE_ID,
                    null,
                    null,
                    "permission".equals(scenario.key()),
                    ConversationSourceType.MANUAL,
                    null,
                    userId));
            Run run = new Run(
                    runId,
                    sessionId,
                    PERSONAL_RUNTIME_ID,
                    scenario.runStatus(),
                    createdAt.plusSeconds(1),
                    updatedAt,
                    TRACE_ID,
                    new TokenUsage(320L, 180L, 40L, 0L, 0L),
                    new BigDecimal("0.00230000"),
                    ConversationSourceType.MANUAL,
                    null,
                    userId,
                    "opencode",
                    "enterprise/Qwen3.6-27B");
            if ("permission".equals(scenario.key()) && runs.findById(runId).isEmpty()) {
                runSummaries.insertAnchor(new RunPersistenceAnchor(
                        runId,
                        sessionId,
                        PERSONAL_RUNTIME_ID,
                        RunStatus.RUNNING,
                        RunStorageMode.REDIS_SUMMARY,
                        0,
                        "req_e2e_permission",
                        "server-e2e-fixture",
                        "node_e2e_fixture",
                        "ocp_e2e_fixture",
                        sessionId.value(),
                        "msg_remote_e2e_permission_user",
                        null,
                        null,
                        null,
                        new SessionMessageId("msg_e2e_permission_summary"),
                        TRACE_ID,
                        createdAt.plusSeconds(1),
                        updatedAt,
                        now.plus(Duration.ofDays(7)),
                        ConversationSourceType.MANUAL,
                        null,
                        userId,
                        "opencode",
                        "enterprise/Qwen3.6-27B"));
            } else {
                runs.save(run);
            }
            SessionMessage userMessage = new SessionMessage(
                    new SessionMessageId("msg_e2e_" + scenario.key() + "_user"),
                    sessionId,
                    SessionMessageRole.USER,
                    scenario.userMessage(),
                    createdAt.plusSeconds(2),
                    TRACE_ID,
                    runId,
                    "opencode",
                    "msg_remote_e2e_" + scenario.key() + "_user",
                    null,
                    TokenUsage.empty(),
                    null,
                    createdAt.plusSeconds(2))
                    .withSource(ConversationSourceType.MANUAL, null, userId);
            messages.save(userMessage);
            messages.save(new SessionMessage(
                    new SessionMessageId("msg_e2e_" + scenario.key() + "_assistant"),
                    sessionId,
                    SessionMessageRole.ASSISTANT,
                    scenario.assistantMessage(),
                    createdAt.plusSeconds(5),
                    TRACE_ID,
                    runId,
                    "opencode",
                    "msg_remote_e2e_" + scenario.key() + "_assistant",
                    scenario.parts().isEmpty() ? null : objectMapper.writeValueAsString(scenario.parts()),
                    new TokenUsage(320L, 180L, 40L, 0L, 0L),
                    new BigDecimal("0.00230000"),
                    updatedAt));
            seeded.put(scenario.key(), new SeededConversation(sessionId, runId, scenario));
        }
        return Map.copyOf(seeded);
    }

    /** 构造可被用户级运行态摘要识别的未决 permission，并保留完整权限说明和匹配范围。 */
    private void seedPermissionRuntime(
            RedisRunRuntimeStore runtimeStore,
            SeededConversation conversation,
            UserId userId,
            Path workspaceRoot,
            Instant now) {
        resetFixtureRuntime(runtimeStore, conversation.runId());
        runtimeStore.initialize(
                runtimeManifest(conversation, userId, RunStatus.RUNNING, now),
                runtimeInput(conversation, workspaceRoot, now));
        runtimeStore.appendDurable(new RunEventDraft(
                conversation.runId(),
                RunEventType.PERMISSION_ASKED,
                TRACE_ID,
                now,
                Map.of(
                        "requestId", "perm_e2e_run_refund_test",
                        "sessionId", conversation.sessionId().value(),
                        "rootSessionId", conversation.sessionId().value(),
                        "isChildSession", false,
                        "type", "bash",
                        "title", "执行退款服务只读测试",
                        "description", "是否允许执行 ./mvnw test -Dtest=RefundServiceTest？",
                        "patterns", List.of("./mvnw test -Dtest=RefundServiceTest"))));
    }

    /** 构造 root task、child scope 和 child 输出，使历史页面能恢复可点击子智能体卡片。 */
    private void seedSubagentRuntime(
            RedisRunRuntimeStore runtimeStore,
            SeededConversation conversation,
            UserId userId,
            Path workspaceRoot,
            Instant now) {
        resetFixtureRuntime(runtimeStore, conversation.runId());
        runtimeStore.initialize(
                runtimeManifest(conversation, userId, RunStatus.RUNNING, now.minusSeconds(10)),
                runtimeInput(conversation, workspaceRoot, now.minusSeconds(10)));
        String rootSessionId = conversation.sessionId().value();
        String childSessionId = "ses_e2e_subagent_backend_child";
        String assistantMessageId = "msg_remote_e2e_subagent_assistant";
        Map<String, Object> rootScope = Map.of(
                "sessionId", rootSessionId,
                "rootSessionId", rootSessionId,
                "isChildSession", false);
        runtimeStore.appendDurable(new RunEventDraft(
                conversation.runId(),
                RunEventType.MESSAGE_UPDATED,
                TRACE_ID,
                now.minusSeconds(9),
                merge(rootScope, Map.of(
                        "messageId", assistantMessageId,
                        "message", Map.of(
                                "id", assistantMessageId,
                                "role", "assistant",
                                "text", conversation.scenario().assistantMessage())))));
        runtimeStore.appendDurable(new RunEventDraft(
                conversation.runId(),
                RunEventType.MESSAGE_PART_UPDATED,
                TRACE_ID,
                now.minusSeconds(8),
                merge(rootScope, Map.of(
                        "messageId", assistantMessageId,
                        "part", taskPart()))));
        runtimeStore.appendDurable(new RunEventDraft(
                conversation.runId(),
                RunEventType.SESSION_CHILD_DISCOVERED,
                TRACE_ID,
                now.minusSeconds(7),
                Map.of(
                        "sessionId", childSessionId,
                        "rootSessionId", rootSessionId,
                        "parentSessionId", rootSessionId,
                        "isChildSession", true,
                        "taskMessageId", assistantMessageId,
                        "taskPartId", "part_e2e_subagent_task",
                        "taskCallId", "call_e2e_subagent_task",
                        "status", "completed",
                        "metadata", Map.of(
                                "agent", "e2e-api-reviewer",
                                "title", "分析退款后端状态机"))));
        Map<String, Object> childScope = Map.of(
                "sessionId", childSessionId,
                "rootSessionId", rootSessionId,
                "parentSessionId", rootSessionId,
                "isChildSession", true,
                "taskMessageId", assistantMessageId,
                "taskPartId", "part_e2e_subagent_task",
                "taskCallId", "call_e2e_subagent_task");
        runtimeStore.appendDurable(new RunEventDraft(
                conversation.runId(),
                RunEventType.MESSAGE_UPDATED,
                TRACE_ID,
                now.minusSeconds(6),
                merge(childScope, Map.of(
                        "messageId", "msg_e2e_subagent_child_assistant",
                        "message", Map.of(
                                "id", "msg_e2e_subagent_child_assistant",
                                "role", "assistant",
                                "text", "后端状态机终态不可回退；超时应保持 PROCESSING 并交给补偿任务。")))));
        runtimeStore.appendDurable(new RunEventDraft(
                conversation.runId(),
                RunEventType.MESSAGE_PART_UPDATED,
                TRACE_ID,
                now.minusSeconds(5),
                merge(childScope, Map.of(
                        "messageId", "msg_e2e_subagent_child_assistant",
                        "part", Map.of(
                                "id", "part_e2e_subagent_child_text",
                                "messageID", "msg_e2e_subagent_child_assistant",
                                "type", "text",
                                "text", "后端状态机终态不可回退；超时应保持 PROCESSING 并交给补偿任务。")))));
        runtimeStore.appendDurable(new RunEventDraft(
                conversation.runId(),
                RunEventType.RUN_SUCCEEDED,
                TRACE_ID,
                now.minusSeconds(4),
                merge(rootScope, Map.of("status", "SUCCEEDED"))));
    }

    private void resetFixtureRuntime(RedisRunRuntimeStore runtimeStore, RunId runId) {
        if (runtimeStore.findManifest(runId).isPresent()) {
            runtimeStore.discardBeforeDispatch(runId);
        }
    }

    private RunRuntimeManifest runtimeManifest(
            SeededConversation conversation,
            UserId userId,
            RunStatus status,
            Instant now) {
        return new RunRuntimeManifest(
                conversation.runId(),
                RunStorageMode.REDIS_SUMMARY,
                userId,
                conversation.sessionId(),
                PERSONAL_RUNTIME_ID,
                "opencode",
                "req_e2e_" + conversation.scenario().key(),
                "msg_remote_e2e_" + conversation.scenario().key() + "_user",
                "server-e2e-fixture",
                null,
                "node_e2e_fixture",
                "ocp_e2e_fixture",
                conversation.sessionId().value(),
                status,
                0,
                0,
                1,
                0,
                false,
                0,
                0,
                null,
                null,
                null,
                now.plus(Duration.ofDays(7)),
                now,
                now);
    }

    private RunRuntimeInput runtimeInput(
            SeededConversation conversation,
            Path workspaceRoot,
            Instant now) {
        return new RunRuntimeInput(
                conversation.runId(),
                conversation.scenario().userMessage(),
                List.of(Map.of("type", "text", "text", conversation.scenario().userMessage())),
                "msg_remote_e2e_" + conversation.scenario().key() + "_user",
                now,
                workspaceRoot.toString(),
                null);
    }

    private static Map<String, Object> taskPart() {
        return Map.of(
                "id", "part_e2e_subagent_task",
                "messageID", "msg_remote_e2e_subagent_assistant",
                "type", "tool",
                "tool", "task",
                "callID", "call_e2e_subagent_task",
                "state", Map.of(
                        "status", "completed",
                        "input", Map.of(
                                "description", "分析退款后端状态机",
                                "prompt", "检查状态迁移、幂等与超时补偿",
                                "subagent_type", "e2e-api-reviewer"),
                        "metadata", Map.of(
                                "sessionID", "ses_e2e_subagent_backend_child",
                                "agent", "e2e-api-reviewer",
                                "title", "分析退款后端状态机"),
                        "output", "<task id=\"ses_e2e_subagent_backend_child\"><task_result>后端状态机终态不可回退；超时应保持 PROCESSING 并交给补偿任务。</task_result></task>"));
    }

    private static Map<String, Object> toolPart(
            String partId,
            String scenarioKey,
            String tool,
            String status,
            Map<String, Object> input,
            String output) {
        return Map.of(
                "id", partId,
                "sessionID", "ses_e2e_" + scenarioKey,
                "messageID", "msg_remote_e2e_" + scenarioKey + "_assistant",
                "type", "tool",
                "tool", tool,
                "callID", "call_" + partId,
                "state", Map.of("status", status, "input", input, "output", output));
    }

    private static Map<String, Object> merge(Map<String, Object> left, Map<String, Object> right) {
        Map<String, Object> merged = new LinkedHashMap<>(left);
        merged.putAll(right);
        return Map.copyOf(merged);
    }

    private static void assertWorkspaceFixture(Path workspaceRoot) {
        assertThat(workspaceRoot.resolve("requirements/order-refund-requirement.md")).isRegularFile();
        assertThat(workspaceRoot.resolve("docs/design/order-refund-detailed-design.md")).isRegularFile();
        assertThat(workspaceRoot.resolve(".opencode/agents/e2e-api-reviewer.md")).isRegularFile();
        assertThat(workspaceRoot.resolve(".opencode/skills/e2e-detailed-design-cases/SKILL.md")).isRegularFile();
    }

    private static DriverManagerDataSource dataSource(Map<String, String> environment) {
        return new DriverManagerDataSource(
                "jdbc:postgresql://%s:%s/%s".formatted(
                        requiredValue(environment, "TEST_AGENT_TEST_DB_HOST"),
                        environment.getOrDefault("TEST_AGENT_TEST_DB_PORT", "5432"),
                        requiredValue(environment, "TEST_AGENT_TEST_DB_NAME")),
                requiredValue(environment, "TEST_AGENT_TEST_DB_USERNAME"),
                requiredValue(environment, "TEST_AGENT_TEST_DB_PASSWORD"));
    }

    private static SqlSessionTemplate sqlSessionTemplate(DriverManagerDataSource dataSource) throws Exception {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        VendorDatabaseIdProvider databaseIdProvider = new VendorDatabaseIdProvider();
        Properties databaseIds = new Properties();
        databaseIds.setProperty("PostgreSQL", "postgresql");
        databaseIds.setProperty("H2", "h2");
        databaseIdProvider.setProperties(databaseIds);
        factory.setDatabaseIdProvider(databaseIdProvider);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mybatis/**/*.xml"));
        SqlSessionFactory sessionFactory = factory.getObject();
        return new SqlSessionTemplate(sessionFactory);
    }

    private static LettuceConnectionFactory redisConnection(Map<String, String> environment) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                requiredValue(environment, "TEST_AGENT_REDIS_HOST"),
                Integer.parseInt(environment.getOrDefault("TEST_AGENT_REDIS_PORT", "6379")));
        String password = environment.get("TEST_AGENT_REDIS_PASSWORD");
        if (password != null && !password.isBlank()) {
            configuration.setPassword(RedisPassword.of(password));
        }
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        return connectionFactory;
    }

    /** 只解析本地 fixture 所需的 KEY=VALUE，不执行环境文件内容。 */
    private static Map<String, String> readEnvironmentFile(Path path) throws Exception {
        Map<String, String> values = new HashMap<>();
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            if (trimmed.startsWith("export ")) {
                trimmed = trimmed.substring("export ".length());
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String value = trimmed.substring(separator + 1).trim();
            if (value.length() >= 2
                    && ((value.startsWith("\"") && value.endsWith("\""))
                    || (value.startsWith("'") && value.endsWith("'")))) {
                value = value.substring(1, value.length() - 1);
            }
            values.put(trimmed.substring(0, separator).trim(), value);
        }
        return values;
    }

    private static String requiredValue(Map<String, String> values, String name) {
        String value = values.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("本地环境文件缺少配置: " + name);
        }
        return value;
    }

    private static String requiredProperty(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少系统属性: " + name);
        }
        return value;
    }

    private record ConversationScenario(
            String key,
            String title,
            String userMessage,
            String assistantMessage,
            List<Map<String, Object>> parts,
            RunStatus runStatus) {

        private ConversationScenario {
            parts = List.copyOf(parts);
        }
    }

    private record SeededConversation(
            SessionId sessionId,
            RunId runId,
            ConversationScenario scenario) {
    }
}
