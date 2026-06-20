<script lang="ts">
import type { ModelInfo, ProviderInfo } from "@test-agent/shared-types";

export type ModelPickerProps = {
  models: ModelInfo[];
  providers: ProviderInfo[];
  selectedModel?: string;
};
</script>

<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from "vue";
import { Check, ChevronDown, Search, Sparkles, X } from "lucide-vue-next";
import { modelOptionValue } from "./chat-utils";

const props = defineProps<ModelPickerProps>();
const emit = defineEmits<{
  select: [payload: { providerId?: string; modelValue: string }];
}>();

const open = ref(false);
const query = ref("");
const rootRef = ref<HTMLElement | null>(null);
const inputRef = ref<HTMLInputElement | null>(null);

const allModels = computed(() => {
  const byValue = new Map<string, ModelInfo>();
  for (const model of props.models) {
    byValue.set(modelOptionValue(model), model);
  }
  for (const provider of props.providers) {
    for (const model of provider.models ?? []) {
      const providerModel = { ...model, providerId: model.providerId ?? provider.providerId };
      byValue.set(modelOptionValue(providerModel), providerModel);
    }
  }
  return [...byValue.values()];
});

const selected = computed(() => allModels.value.find((model) => modelOptionValue(model) === props.selectedModel));
const selectedLabel = computed(() => selected.value?.name ?? props.selectedModel?.split("/").at(-1) ?? "选择模型");

const providerGroups = computed(() => {
  const normalizedQuery = query.value.trim().toLowerCase();
  const modelsByProvider = new Map<string, ModelInfo[]>();
  for (const model of allModels.value) {
    if (normalizedQuery && !`${model.name} ${model.id}`.toLowerCase().includes(normalizedQuery)) {
      continue;
    }
    const providerId = model.providerId ?? "unknown";
    modelsByProvider.set(providerId, [...(modelsByProvider.get(providerId) ?? []), model]);
  }

  const knownProviderIds = new Set(props.providers.map((provider) => provider.providerId));
  const knownGroups = props.providers
    .map((provider) => ({
      provider,
      models: modelsByProvider.get(provider.providerId) ?? []
    }))
    .filter((group) => group.models.length > 0);

  const unknownGroups = [...modelsByProvider.entries()]
    .filter(([providerId]) => !knownProviderIds.has(providerId))
    .map(([providerId, models]) => ({
      provider: { providerId, name: providerId === "unknown" ? "未分组模型" : providerId },
      models
    }));

  return [...knownGroups, ...unknownGroups];
});

function onDocMouseDown(event: MouseEvent) {
  if (!rootRef.value?.contains(event.target as Node)) {
    open.value = false;
  }
}

watch(open, (isOpen) => {
  if (isOpen) {
    document.addEventListener("mousedown", onDocMouseDown);
    void nextTick(() => inputRef.value?.focus());
  } else {
    document.removeEventListener("mousedown", onDocMouseDown);
    query.value = "";
  }
});

onBeforeUnmount(() => document.removeEventListener("mousedown", onDocMouseDown));

function pick(model: ModelInfo) {
  emit("select", { providerId: model.providerId, modelValue: modelOptionValue(model) });
  open.value = false;
}
</script>

<template>
  <div ref="rootRef" class="ta-model-picker">
    <button
      type="button"
      class="ta-model-trigger"
      aria-label="选择模型"
      aria-haspopup="dialog"
      :aria-expanded="open"
      @click="open = !open"
    >
      <Sparkles class="h-3.5 w-3.5 text-[var(--ta-chat-muted)]" />
      <span class="ta-model-trigger-label">{{ selectedLabel }}</span>
      <ChevronDown class="h-3.5 w-3.5 text-[var(--ta-chat-muted)]" />
    </button>

    <div v-if="open" class="ta-model-menu" role="dialog" aria-label="模型选择">
      <div class="ta-model-search">
        <Search class="h-4 w-4 text-[var(--ta-chat-muted)]" />
        <input ref="inputRef" v-model="query" placeholder="搜索模型" />
        <button v-if="query" type="button" aria-label="清空搜索" @click="query = ''">
          <X class="h-3.5 w-3.5" />
        </button>
      </div>

      <div class="ta-model-list" role="listbox" aria-label="模型列表">
        <div v-if="providerGroups.length === 0" class="ta-model-empty">没有匹配的模型</div>
        <section v-for="group in providerGroups" v-else :key="group.provider.providerId" class="ta-model-provider-section">
          <h3>{{ group.provider.name }}</h3>
          <button
            v-for="model in group.models"
            :key="modelOptionValue(model)"
            type="button"
            role="option"
            :aria-selected="modelOptionValue(model) === selectedModel"
            class="ta-model-option"
            @click="pick(model)"
          >
            <span class="ta-model-name">{{ model.name }}</span>
            <span v-if="model.free" class="ta-model-badge">免费</span>
            <Check v-if="modelOptionValue(model) === selectedModel" class="ml-auto h-4 w-4 text-[var(--ta-chat-text)]" />
          </button>
        </section>
      </div>
    </div>
  </div>
</template>
