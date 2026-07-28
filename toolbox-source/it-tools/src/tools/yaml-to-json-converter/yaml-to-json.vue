<script setup lang="ts">
import { parse as parseYaml } from 'yaml';
import type { UseValidationRule } from '@/composable/validation';
import { isNotThrowing } from '@/utils/boolean';
import { withDefaultOnError } from '@/utils/defaults';

function transformer(value: string) {
  return withDefaultOnError(() => {
    const obj = parseYaml(value);
    return obj ? JSON.stringify(obj, null, 3) : '';
  }, '');
}
const { t } = useI18n();

const rules = computed<UseValidationRule<string>[]>(() => [
  {
    validator: (value: string) => isNotThrowing(() => parseYaml(value)),
    message: t('tools.yaml-to-json-converter.ui.invalidYaml'),
  },
]);
</script>

<template>
  <format-transformer
    :input-label="t('tools.yaml-to-json-converter.ui.inputLabel')"
    :input-placeholder="t('tools.yaml-to-json-converter.ui.inputPlaceholder')"
    :output-label="t('tools.yaml-to-json-converter.ui.outputLabel')"
    output-language="json"
    :input-validation-rules="rules"
    :transformer="transformer"
  />
</template>
