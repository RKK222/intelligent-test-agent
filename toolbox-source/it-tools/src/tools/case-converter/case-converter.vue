<script setup lang="ts">
import {
  camelCase,
  capitalCase,
  constantCase,
  dotCase,
  headerCase,
  noCase,
  paramCase,
  pascalCase,
  pathCase,
  sentenceCase,
  snakeCase,
} from 'change-case';
import InputCopyable from '../../components/InputCopyable.vue';

const baseConfig = {
  stripRegexp: /[^A-Za-zÀ-ÖØ-öø-ÿ]+/gi,
};

const input = ref('lorem ipsum dolor sit amet');
const { t } = useI18n();

const formats = computed(() => [
  {
    label: t('tools.case-converter.ui.lowercase'),
    value: input.value.toLocaleLowerCase(),
  },
  {
    label: t('tools.case-converter.ui.uppercase'),
    value: input.value.toLocaleUpperCase(),
  },
  {
    label: t('tools.case-converter.ui.camelCase'),
    value: camelCase(input.value, baseConfig),
  },
  {
    label: t('tools.case-converter.ui.capitalCase'),
    value: capitalCase(input.value, baseConfig),
  },
  {
    label: t('tools.case-converter.ui.constantCase'),
    value: constantCase(input.value, baseConfig),
  },
  {
    label: t('tools.case-converter.ui.dotCase'),
    value: dotCase(input.value, baseConfig),
  },
  {
    label: t('tools.case-converter.ui.headerCase'),
    value: headerCase(input.value, baseConfig),
  },
  {
    label: t('tools.case-converter.ui.noCase'),
    value: noCase(input.value, baseConfig),
  },
  {
    label: t('tools.case-converter.ui.paramCase'),
    value: paramCase(input.value, baseConfig),
  },
  {
    label: t('tools.case-converter.ui.pascalCase'),
    value: pascalCase(input.value, baseConfig),
  },
  {
    label: t('tools.case-converter.ui.pathCase'),
    value: pathCase(input.value, baseConfig),
  },
  {
    label: t('tools.case-converter.ui.sentenceCase'),
    value: sentenceCase(input.value, baseConfig),
  },
  {
    label: t('tools.case-converter.ui.snakeCase'),
    value: snakeCase(input.value, baseConfig),
  },
  {
    label: t('tools.case-converter.ui.mockingCase'),
    value: input.value
      .split('')
      .map((char, index) => (index % 2 === 0 ? char.toUpperCase() : char.toLowerCase()))
      .join(''),
  },
]);

const inputLabelAlignmentConfig = {
  labelPosition: 'left',
  labelWidth: '120px',
  labelAlign: 'right',
};
</script>

<template>
  <c-card>
    <c-input-text
      v-model:value="input"
      :label="t('tools.case-converter.ui.inputLabel')"
      :placeholder="t('tools.case-converter.ui.inputPlaceholder')"
      raw-text
      v-bind="inputLabelAlignmentConfig"
    />

    <div my-16px divider />

    <InputCopyable
      v-for="format in formats"
      :key="format.label"
      :value="format.value"
      :label="format.label"
      v-bind="inputLabelAlignmentConfig"
      mb-1
    />
  </c-card>
</template>
