<script setup lang="ts">
import { generateNumeronym } from './numeronym-generator.service';

const word = ref('');
const { t } = useI18n();

const numeronym = computed(() => generateNumeronym(word.value));
</script>

<template>
  <div flex flex-col items-center gap-4>
    <c-input-text v-model:value="word" :placeholder="t('tools.numeronym-generator.ui.inputPlaceholder')" size="large" clearable test-id="word-input" />

    <icon-mdi-arrow-down text-30px />

    <input-copyable :value="numeronym" size="large" readonly :placeholder="t('tools.numeronym-generator.ui.outputPlaceholder')" test-id="numeronym" />
  </div>
</template>
