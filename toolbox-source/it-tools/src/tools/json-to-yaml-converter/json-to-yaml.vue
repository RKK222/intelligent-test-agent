<script setup lang="ts">
import { stringify } from 'yaml';
import JSON5 from 'json5';
import type { UseValidationRule } from '@/composable/validation';
import { isNotThrowing } from '@/utils/boolean';
import { withDefaultOnError } from '@/utils/defaults';

const transformer = (value: string) => withDefaultOnError(() => stringify(JSON5.parse(value)), '');
const { t } = useI18n();

const rules = computed<UseValidationRule<string>[]>(() => [
  {
    validator: (value: string) => value === '' || isNotThrowing(() => stringify(JSON5.parse(value))),
    message: t('tools.json-to-yaml-converter.ui.invalidJson'),
  },
]);
</script>

<template>
  <format-transformer
    :input-label="t('tools.json-to-yaml-converter.ui.inputLabel')"
    :input-placeholder="t('tools.json-to-yaml-converter.ui.inputPlaceholder')"
    :output-label="t('tools.json-to-yaml-converter.ui.outputLabel')"
    output-language="yaml"
    :input-validation-rules="rules"
    :transformer="transformer"
  />
</template>
