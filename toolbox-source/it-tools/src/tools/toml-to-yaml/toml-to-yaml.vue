<script setup lang="ts">
import { parse as parseToml } from 'iarna-toml-esm';
import { stringify as stringifyToYaml } from 'yaml';
import { withDefaultOnError } from '../../utils/defaults';
import { isValidToml } from '../toml-to-json/toml.services';
import type { UseValidationRule } from '@/composable/validation';

const transformer = (value: string) => value.trim() === '' ? '' : withDefaultOnError(() => stringifyToYaml(parseToml(value)), '');
const { t } = useI18n();

const rules = computed<UseValidationRule<string>[]>(() => [
  {
    validator: isValidToml,
    message: t('tools.toml-to-yaml.ui.invalidToml'),
  },
]);
</script>

<template>
  <format-transformer
    :input-label="t('tools.toml-to-yaml.ui.inputLabel')"
    :input-placeholder="t('tools.toml-to-yaml.ui.inputPlaceholder')"
    :output-label="t('tools.toml-to-yaml.ui.outputLabel')"
    output-language="yaml"
    :input-validation-rules="rules"
    :transformer="transformer"
  />
</template>
