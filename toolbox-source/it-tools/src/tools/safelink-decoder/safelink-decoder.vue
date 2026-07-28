<script setup lang="ts">
import { decodeSafeLinksURL } from './safelink-decoder.service';
import TextareaCopyable from '@/components/TextareaCopyable.vue';

const inputSafeLinkUrl = ref('');
const { t } = useI18n();
const outputDecodedUrl = computed(() => {
  try {
    return decodeSafeLinksURL(inputSafeLinkUrl.value) ?? '';
  }
  catch (e: any) {
    return e instanceof Error && e.message === 'INVALID_SAFELINK_URL'
      ? t('tools.safelink-decoder.ui.invalidUrl')
      : t('tools.safelink-decoder.ui.unknownError');
  }
});
</script>

<template>
  <div>
    <c-input-text
      v-model:value="inputSafeLinkUrl"
      raw-text
      :placeholder="t('tools.safelink-decoder.ui.inputPlaceholder')"
      autofocus
      :label="t('tools.safelink-decoder.ui.inputLabel')"
    />

    <n-divider />

    <n-form-item :label="t('tools.safelink-decoder.ui.outputLabel')">
      <TextareaCopyable :value="outputDecodedUrl" :word-wrap="true" />
    </n-form-item>
  </div>
</template>
