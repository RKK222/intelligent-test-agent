import type { ModelInfo, ModelRef, ProviderInfo } from "@test-agent/shared-types";
import type { ModelCatalog } from "./types";

export function createModelCatalog(providers: ProviderInfo[] = [], models: ModelInfo[] = []): ModelCatalog {
  const providersById: Record<string, ProviderInfo> = {};
  const modelsByKey: ModelCatalog["modelsByKey"] = {};

  for (const provider of providers) {
    providersById[provider.providerId] = provider;
    for (const model of provider.models ?? []) {
      const normalized = { ...model, providerId: model.providerId ?? provider.providerId };
      modelsByKey[modelKey(normalized)] = normalized;
    }
  }

  for (const model of models) {
    modelsByKey[modelKey(model)] = model;
  }

  return { providersById, modelsByKey };
}

export function formatModelLabel(catalog: ModelCatalog, model?: ModelRef): string {
  if (!model?.id) {
    return "";
  }
  const key = modelKey(model);
  const found = catalog.modelsByKey[key] ?? catalog.modelsByKey[model.id];
  const providerId = model.providerId ?? found?.providerId;
  const providerName = providerId ? catalog.providersById[providerId]?.name ?? providerId : undefined;
  const modelName = found?.name ?? model.id;
  return providerName ? `${providerName} / ${modelName}` : modelName;
}

function modelKey(model: ModelRef): string {
  return model.providerId ? `${model.providerId}/${model.id}` : model.id;
}
