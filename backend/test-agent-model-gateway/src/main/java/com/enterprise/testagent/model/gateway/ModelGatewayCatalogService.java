package com.enterprise.testagent.model.gateway;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModel;
import com.enterprise.testagent.domain.configuration.InternalModelProviderModelRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRepository;
import com.enterprise.testagent.domain.configuration.InternalModelProviderRuntimeConfig;
import com.enterprise.testagent.domain.configuration.ModelCapability;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 将已探测的公开模型 ID 解析为当前启用供应商及密钥。 */
@Service
public class ModelGatewayCatalogService {

    private final InternalModelProviderRepository providerRepository;
    private final InternalModelProviderModelRepository modelRepository;

    public ModelGatewayCatalogService(
            InternalModelProviderRepository providerRepository,
            InternalModelProviderModelRepository modelRepository) {
        this.providerRepository = Objects.requireNonNull(providerRepository);
        this.modelRepository = Objects.requireNonNull(modelRepository);
    }

    /** 只返回当前供应商可用且至少一项能力探测成功的模型。 */
    public List<ModelGatewayModelView> listModels() {
        Map<String, InternalModelProviderRuntimeConfig> providers = runtimeProviders();
        return modelRepository.findEnabled().stream()
                .filter(InternalModelProviderModel::routable)
                .filter(model -> providers.containsKey(model.providerId()))
                .sorted(Comparator.comparing(InternalModelProviderModel::modelId))
                .map(model -> new ModelGatewayModelView(
                        model.modelId(),
                        model.displayName(),
                        model.contextLimit(),
                        model.probedCapabilities(),
                        null))
                .toList();
    }

    /** 解析特定端点所需能力，不允许请求方自行选择 provider。 */
    public ResolvedModel resolve(String modelId, ModelCapability requiredCapability) {
        if (modelId == null || modelId.isBlank()) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "模型 ID 不能为空");
        }
        InternalModelProviderModel model = modelRepository.findByModelId(modelId.trim())
                .filter(InternalModelProviderModel::routable)
                .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "企业模型不存在或未通过探测"));
        if (!model.probedCapabilities().contains(requiredCapability)) {
            throw new PlatformException(ErrorCode.VALIDATION_ERROR, "企业模型未通过该端点能力探测");
        }
        InternalModelProviderRuntimeConfig provider = runtimeProviders().get(model.providerId());
        if (provider == null || provider.authToken() == null || provider.authToken().isBlank()) {
            throw new PlatformException(ErrorCode.OPENCODE_UNAVAILABLE, "企业模型供应商当前不可用");
        }
        return new ResolvedModel(provider, model);
    }

    private Map<String, InternalModelProviderRuntimeConfig> runtimeProviders() {
        Map<String, InternalModelProviderRuntimeConfig> providers = new LinkedHashMap<>();
        for (InternalModelProviderRuntimeConfig config : providerRepository.findEnabledRuntimeConfigs()) {
            if (config.authToken() != null && !config.authToken().isBlank()) {
                providers.put(config.provider().providerId(), config);
            }
        }
        return Map.copyOf(providers);
    }
}
