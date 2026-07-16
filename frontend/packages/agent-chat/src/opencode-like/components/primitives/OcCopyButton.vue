<script lang="ts">
export type OcCopyButtonProps = {
  value: string;
};
</script>

<script setup lang="ts">
import { ref } from "vue";
import { Check, Copy } from "lucide-vue-next";
import { copyTextToClipboard } from "@test-agent/ui-kit";
import OcIconButton from "./OcIconButton.vue";

const props = defineProps<OcCopyButtonProps>();
const copied = ref(false);

async function copyValue() {
  if (await copyTextToClipboard(props.value)) {
    copied.value = true;
    setTimeout(() => {
      copied.value = false;
    }, 1200);
  } else {
    copied.value = false;
  }
}
</script>

<template>
  <OcIconButton :label="copied ? '已复制' : '复制'" @click="copyValue">
    <Check v-if="copied" class="oc-icon-button__icon" />
    <Copy v-else class="oc-icon-button__icon" />
  </OcIconButton>
</template>
