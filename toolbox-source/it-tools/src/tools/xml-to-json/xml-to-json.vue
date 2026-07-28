<script setup lang="ts">
import convert from 'xml-js';
import { isValidXML } from '../xml-formatter/xml-formatter.service';
import { withDefaultOnError } from '@/utils/defaults';
import type { UseValidationRule } from '@/composable/validation';

const defaultValue = '<a x="1.234" y="It\'s"/>';
const { t } = useI18n();
function transformer(value: string) {
  return withDefaultOnError(() => {
    return JSON.stringify(convert.xml2js(value, { compact: true }), null, 2);
  }, '');
}

const rules = computed<UseValidationRule<string>[]>(() => [
  {
    validator: isValidXML,
    message: t('tools.xml-to-json.ui.invalidXml'),
  },
]);
</script>

<template>
  <format-transformer
    :input-label="t('tools.xml-to-json.ui.inputLabel')"
    :input-default="defaultValue"
    :input-placeholder="t('tools.xml-to-json.ui.inputPlaceholder')"
    :output-label="t('tools.xml-to-json.ui.outputLabel')"
    output-language="json"
    :transformer="transformer"
    :input-validation-rules="rules"
  />
</template>
