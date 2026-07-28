<script setup lang="ts">
import { useCopy } from '@/composable/copy';

const props = withDefaults(defineProps<{ value?: string }>(), { value: '' });
const { value } = toRefs(props);

const { t } = useI18n();
const { copy, isJustCopied } = useCopy({ source: value, createToast: false });
const tooltipText = computed(() => isJustCopied.value ? t('common.copied') : t('common.copyToClipboard'));
</script>

<template>
  <c-tooltip :tooltip="tooltipText">
    <span cursor-pointer font-mono @click="copy()">{{ value }}</span>
  </c-tooltip>
</template>
