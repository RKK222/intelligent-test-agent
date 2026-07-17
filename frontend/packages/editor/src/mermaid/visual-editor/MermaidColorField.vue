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
  // `#RGB` 同时也是六位颜色的输入前缀，键入过程中不能立即扩展，否则剩余三位无法继续输入。
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
    <span class="ta-mermaid-color-field__label">{{ label }}</span>
    <div class="ta-mermaid-color-field__controls">
      <input
        type="color"
        :aria-label="`${label}取色器`"
        :value="modelValue ?? '#172033'"
        :disabled="disabled"
        @input="updatePicker(($event.target as HTMLInputElement).value)"
      />
      <input
        type="text"
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
      <button type="button" :aria-label="`恢复默认${label}`" :disabled="disabled || !modelValue" @click="reset">默认</button>
    </div>
    <small v-if="invalid" role="alert">请输入 #RGB 或 #RRGGBB</small>
    <small v-else-if="disabled && disabledHint">{{ disabledHint }}</small>
  </div>
</template>

<style scoped>
.ta-mermaid-color-field { display: grid; gap: 4px; }
.ta-mermaid-color-field__label { color: var(--ta-muted, #64748b); font-size: 11px; }
.ta-mermaid-color-field__controls { display: grid; grid-template-columns: 30px minmax(0, 1fr) auto; gap: 5px; align-items: center; }
.ta-mermaid-color-field input, .ta-mermaid-color-field button { box-sizing: border-box; min-height: 28px; border: 1px solid var(--ta-border, #dbe2ea); border-radius: 5px; background: var(--ta-surface, #fff); color: var(--ta-ink, #172033); font: inherit; font-size: 12px; }
.ta-mermaid-color-field input[type="color"] { width: 30px; padding: 2px; cursor: pointer; }
.ta-mermaid-color-field input[type="text"] { width: 100%; padding: 0 7px; font-family: var(--ta-font-mono, monospace); }
.ta-mermaid-color-field button { padding: 0 7px; cursor: pointer; }
.ta-mermaid-color-field button:disabled, .ta-mermaid-color-field input:disabled { cursor: not-allowed; opacity: 0.55; }
.ta-mermaid-color-field input[aria-invalid="true"] { border-color: var(--destructive, #d92d20); }
.ta-mermaid-color-field small { color: var(--ta-muted, #64748b); font-size: 10px; line-height: 1.25; }
.ta-mermaid-color-field small[role="alert"] { color: var(--destructive, #d92d20); }
</style>
