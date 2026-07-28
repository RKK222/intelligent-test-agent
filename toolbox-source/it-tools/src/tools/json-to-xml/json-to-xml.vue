<script setup lang="ts">
import convert from 'xml-js';
import JSON5 from 'json5';
import { withDefaultOnError } from '@/utils/defaults';
import type { UseValidationRule } from '@/composable/validation';

const defaultValue = '{"a":{"_attributes":{"x":"1.234","y":"It\'s"}}}';
const { t } = useI18n();
function transformer(value: string) {
  return withDefaultOnError(() => {
    return convert.js2xml(JSON5.parse(value), { compact: true });
  }, '');
}

const rules = computed<UseValidationRule<string>[]>(() => [
  {
    validator: (v: string) => v === '' || JSON5.parse(v),
    message: t('tools.json-to-xml.ui.invalidJson'),
  },
]);
</script>

<template>
  <format-transformer
    :input-label="t('tools.json-to-xml.ui.inputLabel')"
    :input-default="defaultValue"
    :input-placeholder="t('tools.json-to-xml.ui.inputPlaceholder')"
    :output-label="t('tools.json-to-xml.ui.outputLabel')"
    output-language="xml"
    :transformer="transformer"
    :input-validation-rules="rules"
  />
</template>
