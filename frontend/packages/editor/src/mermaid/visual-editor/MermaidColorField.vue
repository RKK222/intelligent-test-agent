<script setup lang="ts">
import { ref, watch } from "vue";
import { normalizeMermaidHexColor } from "../style-directives";

const props = withDefaults(defineProps<{
  label: string;
  modelValue?: string;
  disabled?: boolean;
  disabledHint?: string;
}>(), { disabled: false });
const emit = defineEmits<{
  "update:modelValue": [value: string | undefined];
  draft: [value: string];
}>();
const raw = ref(props.modelValue ?? "");
const invalid = ref(false);

const PRESET_COLORS = [
  { name: "白色", value: "#ffffff" },
  { name: "黑色", value: "#172033" },
  { name: "灰色", value: "#64748b" },
  { name: "红色", value: "#d92d20" },
  { name: "橙色", value: "#f97316" },
  { name: "黄色", value: "#eab308" },
  { name: "绿色", value: "#10b981" },
  { name: "蓝色", value: "#2563eb" },
  { name: "紫色", value: "#8b5cf6" }
];

watch(() => props.modelValue, (value) => {
  raw.value = value ?? "";
  invalid.value = false;
});

function updateHex(value: string) {
  raw.value = value;
  emit("draft", value);
  if (!value.trim()) {
    invalid.value = false;
    emit("update:modelValue", undefined);
    return;
  }
  const isCompleteSixDigit = /^#[0-9a-f]{6}$/i.test(value.trim());
  const isHexPrefix = /^#[0-9a-f]{0,6}$/i.test(value.trim());
  invalid.value = !isHexPrefix;
  if (isCompleteSixDigit) {
    const normalized = normalizeMermaidHexColor(value)!;
    emit("update:modelValue", normalized);
  }
}

function finalizeHex() {
  if (!raw.value.trim()) return;
  const normalized = normalizeMermaidHexColor(raw.value);
  invalid.value = !normalized;
  if (!normalized) return;
  raw.value = normalized;
  emit("draft", normalized);
  emit("update:modelValue", normalized);
}

function updatePicker(value: string) {
  const normalized = normalizeMermaidHexColor(value);
  if (!normalized) return;
  raw.value = normalized;
  invalid.value = false;
  emit("draft", normalized);
  emit("update:modelValue", normalized);
}

function reset() {
  raw.value = "";
  invalid.value = false;
  emit("draft", "");
  emit("update:modelValue", undefined);
}
</script>

<template>
  <div class="ta-mermaid-color-field" :class="{ 'is-disabled': disabled }">
    <div class="ta-mermaid-color-field__container">
      <span class="ta-mermaid-color-field__label">{{ label }}</span>
      <div class="ta-mermaid-color-field__controls">
        <button
          v-for="color in PRESET_COLORS"
          :key="color.value"
          type="button"
          class="ta-mermaid-color-preset-square"
          :style="{ backgroundColor: color.value }"
          :aria-label="`选择${color.name}`"
          :title="color.name"
          :class="{ 'is-active': modelValue === color.value }"
          :disabled="disabled"
          @click="updatePicker(color.value)"
        />
        <div class="ta-mermaid-color-palette-wrapper">
          <button
            type="button"
            class="ta-mermaid-color-palette-btn"
            title="选择自定义颜色"
            aria-label="自定义颜色"
            :disabled="disabled"
          >
            <svg viewBox="0 0 24 24" width="10" height="10" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M12 22C17.5228 22 22 17.5228 22 12C22 6.47715 17.5228 2 12 2C6.47715 2 2 6.47715 2 12C2 14.7255 3.09032 17.1962 4.85857 19C5.0377 19.179 5.22998 19.3456 5.43431 19.4983C6.11545 20.0073 6.94273 20.2471 7.74706 20.0963C8.61853 19.9329 9.38147 19.4265 9.77317 18.6431C10.0264 18.1366 10.548 17.8182 11.1122 17.8182H12.8878C13.452 17.8182 13.9736 18.1366 14.2268 18.6431C14.6185 19.4265 15.3815 19.9329 16.2529 20.0963C17.0573 20.2471 17.8845 20.0073 18.5657 19.4983C18.77 19.3456 18.9623 19.179 19.1414 19M12 22V17.8182" />
              <circle cx="7.5" cy="10.5" r="1.5" fill="currentColor"/>
              <circle cx="11.5" cy="7.5" r="1.5" fill="currentColor"/>
              <circle cx="16.5" cy="9.5" r="1.5" fill="currentColor"/>
            </svg>
          </button>
          <input
            type="color"
            class="ta-mermaid-color-field__hidden-picker"
            :value="modelValue ?? '#172033'"
            :disabled="disabled"
            @input="updatePicker(($event.target as HTMLInputElement).value)"
          />
        </div>
        <button
          type="button"
          class="ta-mermaid-color-preset-square is-reset-btn"
          :title="`恢复默认${label}`"
          :aria-label="`恢复默认${label}`"
          :disabled="disabled || !modelValue"
          @click="reset"
        >
          ❌
        </button>
        <input
          type="text"
          class="ta-mermaid-color-field__hidden-input"
          :aria-label="label"
          :value="raw"
          placeholder="#RRGGBB"
          maxlength="7"
          :disabled="disabled"
          :aria-invalid="invalid"
          @input="updateHex(($event.target as HTMLInputElement).value)"
          @blur="finalizeHex"
          @keydown.enter="finalizeHex"
        />
      </div>
    </div>
    <small v-if="invalid" role="alert">请输入 #RGB 或 #RRGGBB</small>
  </div>
</template>

<style scoped>
.ta-mermaid-color-field { display: block; }
.ta-mermaid-color-field__container { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.ta-mermaid-color-field__label { color: var(--ta-muted, #64748b); font-size: 11px; white-space: nowrap; }
.ta-mermaid-color-field__controls { display: flex; align-items: center; gap: 4px; }
.ta-mermaid-color-preset-square { width: 14px; height: 14px; padding: 0; border: 1px solid var(--ta-border, #dbe2ea); border-radius: 2px; cursor: pointer; transition: transform 100ms ease, border-color 100ms ease; box-sizing: border-box; }
.ta-mermaid-color-preset-square:hover { transform: scale(1.2); border-color: var(--ta-border-strong, #94a3b8); }
.ta-mermaid-color-preset-square.is-active { box-shadow: 0 0 0 1.5px var(--primary, #4f46e5); border-color: #fff; }
.ta-mermaid-color-preset-square.is-reset-btn { display: flex; align-items: center; justify-content: center; font-size: 8px; line-height: 1; border-color: var(--ta-border, #dbe2ea); background: var(--ta-surface, #fff); }
.ta-mermaid-color-preset-square.is-reset-btn:disabled { opacity: 0.4; cursor: not-allowed; }
.ta-mermaid-color-palette-wrapper { position: relative; width: 14px; height: 14px; display: inline-block; }
.ta-mermaid-color-palette-btn { display: flex; align-items: center; justify-content: center; width: 14px; height: 14px; padding: 0; border: 1px solid var(--ta-border, #dbe2ea); border-radius: 2px; background: var(--ta-surface, #fff); color: var(--ta-muted, #64748b); cursor: pointer; transition: transform 100ms ease, border-color 100ms ease, color 100ms ease; box-sizing: border-box; }
.ta-mermaid-color-palette-wrapper:hover .ta-mermaid-color-palette-btn { transform: scale(1.2); border-color: var(--ta-border-strong, #94a3b8); color: var(--ta-ink, #172033); }
.ta-mermaid-color-field__hidden-picker { position: absolute; top: 0; left: 0; width: 100%; height: 100%; opacity: 0.01; cursor: pointer; padding: 0; border: none; margin: 0; box-sizing: border-box; }
.ta-mermaid-color-field__hidden-picker:disabled { cursor: not-allowed; }
.ta-mermaid-color-field__hidden-input { display: none; }
</style>
