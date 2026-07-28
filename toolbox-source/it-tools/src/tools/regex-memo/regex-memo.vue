<script setup lang="ts">
import { useThemeVars } from 'naive-ui';

const themeVars = useThemeVars();
const { t } = useI18n();
// Vue I18n 会把正则中的花括号和 Markdown 表格竖线解析为消息语法，渲染前再还原技术符号。
const content = computed(() => t('tools.regex-memo.ui.content')
  .split('¦').join('|')
  .split('⦃').join('{')
  .split('⦄').join('}'));
</script>

<template>
  <div>
    <c-markdown :markdown="content" />
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
::v-deep(table) {
  border-collapse: collapse;
}
::v-deep(table), ::v-deep(td), ::v-deep(th) {
  border: 1px solid v-bind('themeVars.textColor1');
  padding: 5px;
}
::v-deep(a) {
  color: v-bind('themeVars.textColor1');
}
</style>
