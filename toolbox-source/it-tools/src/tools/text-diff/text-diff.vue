<script setup lang="ts">
const { t } = useI18n();
</script>

<template>
  <c-card w-full important:flex-1 important:pa-0>
    <c-diff-editor
      :original-text="t('tools.text-diff.ui.originalText')"
      :modified-text="t('tools.text-diff.ui.modifiedText')"
    />
  </c-card>
</template>
