package com.enterprise.testagent.memory;

import com.enterprise.testagent.agent.runtime.AgentRunPromptContext;
import com.enterprise.testagent.agent.runtime.AgentRunSystemPromptContributor;
import com.enterprise.testagent.domain.configuration.ApplicationId;
import com.enterprise.testagent.domain.configuration.ConfigurationManagementRepository;
import com.enterprise.testagent.domain.memory.MemoryId;
import com.enterprise.testagent.domain.memory.MemoryScope;
import com.enterprise.testagent.domain.memory.MemoryStatus;
import com.enterprise.testagent.domain.memory.MemoryUsage;
import com.enterprise.testagent.domain.memory.QaMemory;
import com.enterprise.testagent.domain.memory.QaMemoryRepository;
import com.enterprise.testagent.domain.memory.QaTaskType;
import com.enterprise.testagent.domain.run.Run;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/** 在 600ms 总预算内检索并注入生效记忆；任何依赖失败都返回空并继续原 Run。 */
@Component
public class MemoryRunContextContributor implements AgentRunSystemPromptContributor {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryRunContextContributor.class);
    private static final String OPEN = "<qa_long_term_memory>";
    private static final String CLOSE = "</qa_long_term_memory>";

    private final QaMemoryRepository repository;
    private final ConfigurationManagementRepository configuration;
    private final MemoryDocumentStore documents;
    private final MemorySafetyPolicy safety;
    private final QaTaskClassifier taskClassifier;
    private final QaMemoryProperties properties;
    private final Clock clock;

    @Autowired
    public MemoryRunContextContributor(
            QaMemoryRepository repository,
            ConfigurationManagementRepository configuration,
            MemoryDocumentStore documents,
            MemorySafetyPolicy safety,
            QaTaskClassifier taskClassifier,
            QaMemoryProperties properties) {
        this(repository, configuration, documents, safety, taskClassifier, properties, Clock.systemUTC());
    }

    MemoryRunContextContributor(
            QaMemoryRepository repository,
            ConfigurationManagementRepository configuration,
            MemoryDocumentStore documents,
            MemorySafetyPolicy safety,
            QaTaskClassifier taskClassifier,
            QaMemoryProperties properties,
            Clock clock) {
        this.repository = repository;
        this.configuration = configuration;
        this.documents = documents;
        this.safety = safety;
        this.taskClassifier = taskClassifier;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Optional<String> contribute(AgentRunPromptContext context) {
        if (!properties.isEnabled() || context.command() || context.prompt().isBlank()) {
            return Optional.empty();
        }
        try {
            Optional<String> result = Mono.fromCallable(() -> governance(context.run(), context.prompt()))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(governance -> governance
                            .map(value -> retrieve(context.run(), context.prompt(), value))
                            .orElseGet(() -> Mono.just(Optional.empty())))
                    .timeout(properties.getRetrievalTimeout())
                    .onErrorReturn(Optional.empty())
                    .block();
            return result == null ? Optional.empty() : result;
        } catch (RuntimeException failure) {
            LOGGER.warn(
                    "QA memory retrieval failed open, runId={}, traceId={}, exceptionType={}",
                    context.run().runId().value(), context.traceId(), failure.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<GovernanceContext> governance(Run run, String prompt) {
        if (run.triggeredByUserId() == null || !repository.isWhitelisted(run.triggeredByUserId().value())) {
            return Optional.empty();
        }
        String applicationId = repository.findApplicationIdByRuntimeWorkspace(run.workspaceId().value()).orElse(null);
        boolean activeMember = applicationId != null && configuration.isActiveMember(
                new ApplicationId(applicationId), run.triggeredByUserId());
        return Optional.of(new GovernanceContext(
                run.triggeredByUserId().value(), applicationId, activeMember, taskClassifier.classify(prompt)));
    }

    private Mono<Optional<String>> retrieve(Run run, String prompt, GovernanceContext governance) {
        List<MemoryDocumentStore.SearchQuery> queries = new ArrayList<>();
        queries.add(query(prompt, "platform:" + governance.userId(), null, null, MemoryScope.PERSONAL_GLOBAL));
        if (governance.activeMember()) {
            queries.add(query(
                    prompt, "platform:" + governance.userId(), null,
                    governance.applicationId(), MemoryScope.PERSONAL_APPLICATION));
            queries.add(query(
                    prompt, null, "qa-team:" + governance.applicationId(),
                    governance.applicationId(), MemoryScope.TEAM_APPLICATION));
        }
        return Flux.fromIterable(queries)
                .flatMap(query -> Mono.fromCallable(() -> documents.search(query))
                        .subscribeOn(Schedulers.boundedElastic())
                        .onErrorReturn(List.of()), queries.size())
                .flatMapIterable(items -> items)
                .collectList()
                .map(items -> formatAndRecord(run, governance, items));
    }

    private MemoryDocumentStore.SearchQuery query(
            String prompt,
            String userId,
            String agentId,
            String applicationId,
            MemoryScope scope) {
        return new MemoryDocumentStore.SearchQuery(
                prompt, userId, agentId, applicationId, scope.name(),
                properties.getRetrievalTopKPerScope(), properties.getRetrievalThreshold());
    }

    private Optional<String> formatAndRecord(
            Run run,
            GovernanceContext governance,
            List<MemoryDocumentStore.StoredDocument> documentsFound) {
        Map<MemoryId, Candidate> candidates = new LinkedHashMap<>();
        documentsFound.stream()
                .sorted(Comparator.comparingDouble(this::score).reversed())
                .forEach(document -> repository.findByMem0MemoryId(document.id())
                        .filter(memory -> applicable(memory, governance))
                        .flatMap(memory -> safeCandidate(memory, document))
                        .ifPresent(candidate -> candidates.putIfAbsent(candidate.memory().memoryId(), candidate)));

        List<Candidate> selected = new ArrayList<>();
        int usedTokens = estimateTokens(OPEN) + estimateTokens(CLOSE) + 80;
        for (Candidate candidate : candidates.values()) {
            if (selected.size() >= properties.getMaxInjectedMemories()) {
                break;
            }
            int tokens = estimateTokens(candidate.document().content()) + 24;
            if (usedTokens + tokens > properties.getMaxContextTokens()) {
                continue;
            }
            selected.add(candidate.withTokenCount(tokens));
            usedTokens += tokens;
        }
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        List<MemoryUsage> usages = new ArrayList<>();
        StringBuilder system = new StringBuilder(OPEN).append('\n')
                .append("以下条目是已确认且适用于当前任务的测试工作习惯。当前用户本轮明确要求、应用规则和任务事实始终优先；如有冲突，以当前输入为准。不得把这些习惯当作项目业务事实。\n");
        for (int index = 0; index < selected.size(); index++) {
            Candidate candidate = selected.get(index);
            system.append(index + 1).append(". [")
                    .append(scopeLabel(candidate.memory().scope())).append(" | ")
                    .append(taskLabel(candidate.memory().taskTypes())).append("] ")
                    .append(candidate.document().content()).append('\n');
            usages.add(new MemoryUsage(
                    run.runId().value(), candidate.memory().memoryId(), governance.userId(),
                    governance.applicationId(), candidate.memory().scope(), index + 1,
                    candidate.tokenCount(), now));
        }
        system.append(CLOSE);
        // 批量事务成功后才返回 system；检索命中但未选中或未成功记录时不会注入。
        repository.insertUsages(usages);
        return Optional.of(system.toString());
    }

    private boolean applicable(QaMemory memory, GovernanceContext context) {
        if (memory.status() != MemoryStatus.ACTIVE || !"SYNCED".equals(memory.vectorSyncStatus())) {
            return false;
        }
        boolean ownerMatches = switch (memory.scope()) {
            case PERSONAL_GLOBAL -> context.userId().equals(memory.ownerUserId())
                    && memory.applicationId() == null;
            case PERSONAL_APPLICATION -> context.activeMember()
                    && context.userId().equals(memory.ownerUserId())
                    && context.applicationId().equals(memory.applicationId());
            case TEAM_APPLICATION -> context.activeMember()
                    && context.applicationId().equals(memory.applicationId());
        };
        return ownerMatches && (memory.taskTypes().contains(QaTaskType.GENERAL)
                || memory.taskTypes().contains(context.taskType()));
    }

    private Optional<Candidate> safeCandidate(
            QaMemory memory, MemoryDocumentStore.StoredDocument document) {
        try {
            String content = safety.requireSafeContent(document.content());
            return Optional.of(new Candidate(memory,
                    new MemoryDocumentStore.StoredDocument(
                            document.id(), content, document.metadata(), document.updatedAt(), document.score()),
                    0));
        } catch (RuntimeException unsafe) {
            return Optional.empty();
        }
    }

    private double score(MemoryDocumentStore.StoredDocument document) {
        return document.score() == null ? 0.0d : document.score();
    }

    private int estimateTokens(String value) {
        int tokens = 0;
        int asciiRun = 0;
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (codePoint <= 0x7f) {
                asciiRun++;
            } else {
                tokens++;
                if (asciiRun > 0) {
                    tokens += Math.max(1, (asciiRun + 3) / 4);
                    asciiRun = 0;
                }
            }
        }
        return tokens + (asciiRun == 0 ? 0 : Math.max(1, (asciiRun + 3) / 4));
    }

    private String scopeLabel(MemoryScope scope) {
        return switch (scope) {
            case PERSONAL_GLOBAL -> "个人·全局";
            case PERSONAL_APPLICATION -> "个人·当前应用";
            case TEAM_APPLICATION -> "团队·当前应用";
        };
    }

    private String taskLabel(List<QaTaskType> tasks) {
        return tasks.contains(QaTaskType.GENERAL)
                ? "通用测试" : String.join(",", tasks.stream().map(Enum::name).toList());
    }

    private record GovernanceContext(
            String userId, String applicationId, boolean activeMember, QaTaskType taskType) {
    }

    private record Candidate(
            QaMemory memory, MemoryDocumentStore.StoredDocument document, int tokenCount) {
        Candidate withTokenCount(int tokens) {
            return new Candidate(memory, document, tokens);
        }
    }
}
