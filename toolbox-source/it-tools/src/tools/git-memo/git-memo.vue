<script setup lang="ts">
import { useThemeVars } from 'naive-ui';

const themeVars = useThemeVars();
const { t } = useI18n();
</script>

<template>
  <div>
    <c-markdown :markdown="t('tools.git-memo.ui.content')" />
  </div>
</template>

<style lang="less" scoped>
::v-deep(pre) {
  margin: 0;
  padding: 15px 22px;
  background-color: v-bind('themeVars.cardColor');
  border-radius: 4px;
  overflow: auto;
}
</style>
