package com.icbc.testagent.persistence;

import com.icbc.testagent.common.error.ErrorCode;
import com.icbc.testagent.common.error.PlatformException;
import com.icbc.testagent.domain.model.AiModelConfig;
import com.icbc.testagent.domain.model.AiModelConfigRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * 大模型配置 JDBC Repository，内网模式从 ai_model_configs 表读取模型清单。
 */
@Repository
public class JdbcAiModelConfigRepository extends JdbcRepositorySupport implements AiModelConfigRepository {

    private static final TypeReference<Set<String>> MODALITIES_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> METADATA_TYPE = new TypeReference<>() {
    };

    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;
    private final RowMapper<AiModelConfig> rowMapper = (rs, rowNum) -> new AiModelConfig(
            rs.getString("provider_id"),
            rs.getString("model_id"),
            rs.getString("name"),
            rs.getBoolean("enabled"),
            rs.getBoolean("default_model"),
            readJson(rs.getString("input_modalities_json"), MODALITIES_TYPE),
            rs.getInt("context_limit"),
            rs.getInt("output_limit"),
            rs.getInt("sort_order"),
            readJson(rs.getString("metadata_json"), METADATA_TYPE),
            instant(rs, "created_at"),
            instant(rs, "updated_at"));

    /**
     * 注入 JdbcClient 和 ObjectMapper，模型附加属性以 JSON 文本持久化。
     */
    public JdbcAiModelConfigRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 保存模型配置；已存在时按 provider/model 做 upsert。
     */
    @Override
    public AiModelConfig save(AiModelConfig modelConfig) {
        jdbcClient.sql("""
                        insert into ai_model_configs(
                            provider_id, model_id, name, enabled, default_model, input_modalities_json,
                            context_limit, output_limit, sort_order, metadata_json, created_at, updated_at)
                        values (
                            :providerId, :modelId, :name, :enabled, :defaultModel, :inputModalitiesJson,
                            :contextLimit, :outputLimit, :sortOrder, :metadataJson, :createdAt, :updatedAt)
                        on conflict (provider_id, model_id) do update
                        set name = excluded.name,
                            enabled = excluded.enabled,
                            default_model = excluded.default_model,
                            input_modalities_json = excluded.input_modalities_json,
                            context_limit = excluded.context_limit,
                            output_limit = excluded.output_limit,
                            sort_order = excluded.sort_order,
                            metadata_json = excluded.metadata_json,
                            updated_at = excluded.updated_at
                        """)
                .param("providerId", modelConfig.providerId())
                .param("modelId", modelConfig.modelId())
                .param("name", modelConfig.name())
                .param("enabled", modelConfig.enabled())
                .param("defaultModel", modelConfig.defaultModel())
                .param("inputModalitiesJson", writeJson(modelConfig.inputModalities()))
                .param("contextLimit", modelConfig.contextLimit())
                .param("outputLimit", modelConfig.outputLimit())
                .param("sortOrder", modelConfig.sortOrder())
                .param("metadataJson", writeJson(modelConfig.metadata()))
                .param("createdAt", timestamp(modelConfig.createdAt()))
                .param("updatedAt", timestamp(modelConfig.updatedAt()))
                .update();
        return modelConfig;
    }

    /**
     * 判断指定模型配置是否已存在，用于启动 seed 时保留表内人工调整。
     */
    @Override
    public boolean existsByProviderAndModel(String providerId, String modelId) {
        Integer count = jdbcClient.sql("""
                        select count(1)
                        from ai_model_configs
                        where provider_id = :providerId and model_id = :modelId
                        """)
                .param("providerId", providerId)
                .param("modelId", modelId)
                .query(Integer.class)
                .single();
        return count != null && count > 0;
    }

    /**
     * 查询启用模型，默认模型排在最前，之后按 sort_order 和名称稳定排序。
     */
    @Override
    public List<AiModelConfig> findEnabledByProvider(String providerId) {
        return jdbcClient.sql("""
                        select provider_id, model_id, name, enabled, default_model, input_modalities_json,
                               context_limit, output_limit, sort_order, metadata_json, created_at, updated_at
                        from ai_model_configs
                        where provider_id = :providerId and enabled = true
                        order by default_model desc, sort_order asc, name asc
                        """)
                .param("providerId", providerId)
                .query(rowMapper)
                .list();
    }

    /**
     * 查询默认模型，若误配置多个默认值则按排序字段取第一个。
     */
    @Override
    public Optional<AiModelConfig> findDefaultByProvider(String providerId) {
        return jdbcClient.sql("""
                        select provider_id, model_id, name, enabled, default_model, input_modalities_json,
                               context_limit, output_limit, sort_order, metadata_json, created_at, updated_at
                        from ai_model_configs
                        where provider_id = :providerId and enabled = true and default_model = true
                        order by sort_order asc, name asc
                        limit 1
                        """)
                .param("providerId", providerId)
                .query(rowMapper)
                .optional();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "大模型配置 JSON 序列化失败", Map.of(), exception);
        }
    }

    private <T> T readJson(String json, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(json, typeReference);
        } catch (Exception exception) {
            throw new PlatformException(ErrorCode.INTERNAL_ERROR, "大模型配置 JSON 反序列化失败", Map.of(), exception);
        }
    }
}
