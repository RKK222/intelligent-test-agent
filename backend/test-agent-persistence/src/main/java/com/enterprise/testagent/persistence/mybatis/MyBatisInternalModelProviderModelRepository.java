package com.enterprise.testagent.persistence.mybatis;

import com.enterprise.testagent.domain.configuration.InternalModelProviderModel;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModelRepository;
import com.enterprise.testagent.domain.configuration.ModelCapability;
import com.enterprise.testagent.domain.configuration.ModelProbeResult;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 内部模型目录与逐能力探测的 MyBatis XML 仓储。 */
@Repository
public class MyBatisInternalModelProviderModelRepository implements InternalModelProviderModelRepository {

    private final InternalModelProviderModelMapper mapper;

    public MyBatisInternalModelProviderModelRepository(InternalModelProviderModelMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<InternalModelProviderModel> findByProviderId(String providerId) {
        return toDomain(mapper.findByProviderId(providerId));
    }

    @Override
    public List<InternalModelProviderModel> findEnabled() {
        return toDomain(mapper.findEnabled());
    }

    @Override
    public Optional<InternalModelProviderModel> findByModelId(String modelId) {
        InternalModelProviderModelRow row = mapper.findByModelId(modelId);
        if (row == null) {
            return Optional.empty();
        }
        return toDomain(List.of(row)).stream().findFirst();
    }

    /** 覆盖供应商目录时删除旧探测，防止更换上游 ID 后沿用过期结果。 */
    @Override
    @Transactional
    public void replaceForProvider(
            String providerId,
            List<InternalModelProviderModel> models,
            Instant updatedAt) {
        mapper.deleteByProviderId(providerId);
        if (models == null) {
            return;
        }
        for (InternalModelProviderModel model : models) {
            if (!providerId.equals(model.providerId())) {
                throw new IllegalArgumentException("model providerId does not match replacement scope");
            }
            mapper.insertModel(toRow(model, updatedAt));
        }
    }

    @Override
    public void saveProbeResult(ModelProbeResult result) {
        mapper.upsertProbe(new ModelProbeResultRow(
                result.providerId(),
                result.modelId(),
                result.capability().name(),
                result.succeeded(),
                result.probedAt()));
    }

    private List<InternalModelProviderModel> toDomain(List<InternalModelProviderModelRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<String> modelIds = rows.stream().map(InternalModelProviderModelRow::modelId).toList();
        Map<String, Set<ModelCapability>> succeeded = new HashMap<>();
        Map<String, Instant> lastProbedAt = new HashMap<>();
        for (ModelProbeResultRow probe : mapper.findProbesByModelIds(modelIds)) {
            lastProbedAt.merge(probe.modelId(), probe.probedAt(), (left, right) ->
                    left.isAfter(right) ? left : right);
            if (probe.succeeded()) {
                succeeded.computeIfAbsent(probe.modelId(), ignored -> EnumSet.noneOf(ModelCapability.class))
                        .add(ModelCapability.valueOf(probe.capability()));
            }
        }
        List<InternalModelProviderModel> result = new ArrayList<>(rows.size());
        for (InternalModelProviderModelRow row : rows) {
            result.add(new InternalModelProviderModel(
                    row.providerId(),
                    row.modelId(),
                    row.upstreamModelId(),
                    row.displayName(),
                    row.contextLimit(),
                    row.enabled(),
                    declaredCapabilities(row),
                    succeeded.getOrDefault(row.modelId(), Set.of()),
                    lastProbedAt.get(row.modelId()),
                    row.createdAt(),
                    row.updatedAt()));
        }
        return List.copyOf(result);
    }

    private static InternalModelProviderModelRow toRow(InternalModelProviderModel model, Instant updatedAt) {
        Set<ModelCapability> capabilities = model.declaredCapabilities();
        return new InternalModelProviderModelRow(
                model.providerId(),
                model.modelId(),
                model.upstreamModelId(),
                model.displayName(),
                model.contextLimit(),
                model.enabled(),
                capabilities.contains(ModelCapability.CHAT),
                capabilities.contains(ModelCapability.TOOLS),
                capabilities.contains(ModelCapability.VISION),
                capabilities.contains(ModelCapability.REASONING),
                capabilities.contains(ModelCapability.EMBEDDING),
                capabilities.contains(ModelCapability.RERANK),
                capabilities.contains(ModelCapability.IMAGE),
                capabilities.contains(ModelCapability.SPEECH),
                capabilities.contains(ModelCapability.TRANSCRIPTION),
                model.createdAt() == null ? updatedAt : model.createdAt(),
                updatedAt);
    }

    private static Set<ModelCapability> declaredCapabilities(InternalModelProviderModelRow row) {
        EnumSet<ModelCapability> capabilities = EnumSet.noneOf(ModelCapability.class);
        add(capabilities, ModelCapability.CHAT, row.capabilityChat());
        add(capabilities, ModelCapability.TOOLS, row.capabilityTools());
        add(capabilities, ModelCapability.VISION, row.capabilityVision());
        add(capabilities, ModelCapability.REASONING, row.capabilityReasoning());
        add(capabilities, ModelCapability.EMBEDDING, row.capabilityEmbedding());
        add(capabilities, ModelCapability.RERANK, row.capabilityRerank());
        add(capabilities, ModelCapability.IMAGE, row.capabilityImage());
        add(capabilities, ModelCapability.SPEECH, row.capabilitySpeech());
        add(capabilities, ModelCapability.TRANSCRIPTION, row.capabilityTranscription());
        return capabilities;
    }

    private static void add(Set<ModelCapability> target, ModelCapability capability, boolean present) {
        if (present) {
            target.add(capability);
        }
    }
}
