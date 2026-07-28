<script setup lang="ts">
import { type FormatOptionsWithLanguage, format as formatSQL } from 'sql-formatter';
import TextareaCopyable from '@/components/TextareaCopyable.vue';
import { useStyleStore } from '@/stores/style.store';

const inputElement = ref<HTMLElement>();
const styleStore = useStyleStore();
const { t } = useI18n();
const config = reactive<FormatOptionsWithLanguage>({
  keywordCase: 'upper',
  useTabs: false,
  language: 'sql',
  indentStyle: 'standard',
  tabulateAlias: true,
});

const rawSQL = ref('select field1,field2,field3 from my_table where my_condition;');
const prettySQL = computed(() => formatSQL(rawSQL.value, config));
const keywordCaseOptions = computed(() => [
  { label: t('tools.sql-prettify.ui.uppercase'), value: 'upper' },
  { label: t('tools.sql-prettify.ui.lowercase'), value: 'lower' },
  { label: t('tools.sql-prettify.ui.preserve'), value: 'preserve' },
]);
const indentStyleOptions = computed(() => [
  { label: t('tools.sql-prettify.ui.standard'), value: 'standard' },
  { label: t('tools.sql-prettify.ui.tabularLeft'), value: 'tabularLeft' },
  { label: t('tools.sql-prettify.ui.tabularRight'), value: 'tabularRight' },
]);
</script>

<template>
  <div style="flex: 0 0 100%">
    <div style="max-width: 600px" :class="{ 'flex-col': styleStore.isSmallScreen }" mx-auto mb-5 flex gap-2>
      <c-select
        v-model:value="config.language"
        flex-1
        :label="t('tools.sql-prettify.ui.dialect')"
        :options="[
          { label: 'GCP BigQuery', value: 'bigquery' },
          { label: 'IBM DB2', value: 'db2' },
          { label: 'Apache Hive', value: 'hive' },
          { label: 'MariaDB', value: 'mariadb' },
          { label: 'MySQL', value: 'mysql' },
          { label: 'Couchbase N1QL', value: 'n1ql' },
          { label: 'Oracle PL/SQL', value: 'plsql' },
          { label: 'PostgreSQL', value: 'postgresql' },
          { label: 'Amazon Redshift', value: 'redshift' },
          { label: 'Spark', value: 'spark' },
          { label: 'Standard SQL', value: 'sql' },
          { label: 'sqlite', value: 'sqlite' },
          { label: 'SQL Server Transact-SQL', value: 'tsql' },
        ]"
      />
      <c-select
        v-model:value="config.keywordCase" :label="t('tools.sql-prettify.ui.keywordCase')"
        flex-1
        :options="keywordCaseOptions"
      />
      <c-select
        v-model:value="config.indentStyle" :label="t('tools.sql-prettify.ui.indentStyle')"
        flex-1
        :options="indentStyleOptions"
      />
    </div>
  </div>

  <n-form-item :label="t('tools.sql-prettify.ui.inputLabel')">
    <c-input-text
      ref="inputElement"
      v-model:value="rawSQL"
      :placeholder="t('tools.sql-prettify.ui.inputPlaceholder')"
      rows="20"
      multiline
      autocomplete="off"
      autocorrect="off"
      autocapitalize="off"
      spellcheck="false"
      monospace
    />
  </n-form-item>
  <n-form-item :label="t('tools.sql-prettify.ui.outputLabel')">
    <TextareaCopyable :value="prettySQL" language="sql" :follow-height-of="inputElement" />
  </n-form-item>
</template>

<style lang="less" scoped>
.result-card {
  position: relative;
  .copy-button {
    position: absolute;
    top: 10px;
    right: 10px;
  }
}
</style>
