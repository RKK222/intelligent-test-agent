<script setup lang="ts">
import InputCopyable from '../../components/InputCopyable.vue';
import { InvalidDigitError, convertBase } from './integer-base-converter.model';

const inputProps = {
  'labelPosition': 'left',
  'labelWidth': '170px',
  'labelAlign': 'right',
  'readonly': true,
  'mb-2': '',
} as const;

const input = ref('42');
const inputBase = ref(10);
const outputBase = ref(42);
const { t } = useI18n();

function errorlessConvert(...args: Parameters<typeof convertBase>) {
  try {
    return convertBase(...args);
  }
  catch (err) {
    return '';
  }
}

const error = computed(() => {
  try {
    convertBase({ value: input.value, fromBase: inputBase.value, toBase: outputBase.value });
    return '';
  }
  catch (caught) {
    return caught instanceof InvalidDigitError
      ? t('tools.base-converter.ui.invalidDigit', { digit: caught.digit, base: caught.base })
      : t('tools.base-converter.ui.conversionError');
  }
});
</script>

<template>
  <div>
    <c-card>
      <c-input-text v-model:value="input" :label="t('tools.base-converter.ui.inputNumber')" :placeholder="t('tools.base-converter.ui.inputNumberPlaceholder')" label-position="left" label-width="110px" mb-2 label-align="right" />

      <n-form-item :label="t('tools.base-converter.ui.inputBase')" label-placement="left" label-width="110" :show-feedback="false">
        <n-input-number v-model:value="inputBase" max="64" min="2" :placeholder="t('tools.base-converter.ui.inputBasePlaceholder')" w-full />
      </n-form-item>

      <n-alert v-if="error" style="margin-top: 25px" type="error">
        {{ error }}
      </n-alert>
      <n-divider />

      <InputCopyable
        :label="t('tools.base-converter.ui.binary')"
        v-bind="inputProps"
        :value="errorlessConvert({ value: input, fromBase: inputBase, toBase: 2 })"
        :placeholder="t('tools.base-converter.ui.binaryPlaceholder')"
      />

      <InputCopyable
        :label="t('tools.base-converter.ui.octal')"
        v-bind="inputProps"
        :value="errorlessConvert({ value: input, fromBase: inputBase, toBase: 8 })"
        :placeholder="t('tools.base-converter.ui.octalPlaceholder')"
      />

      <InputCopyable
        :label="t('tools.base-converter.ui.decimal')"
        v-bind="inputProps"
        :value="errorlessConvert({ value: input, fromBase: inputBase, toBase: 10 })"
        :placeholder="t('tools.base-converter.ui.decimalPlaceholder')"
      />

      <InputCopyable
        :label="t('tools.base-converter.ui.hexadecimal')"
        v-bind="inputProps"
        :value="errorlessConvert({ value: input, fromBase: inputBase, toBase: 16 })"
        :placeholder="t('tools.base-converter.ui.hexadecimalPlaceholder')"
      />

      <InputCopyable
        label="Base64 (64)"
        v-bind="inputProps"
        :value="errorlessConvert({ value: input, fromBase: inputBase, toBase: 64 })"
        :placeholder="t('tools.base-converter.ui.base64Placeholder')"
      />

      <div flex items-baseline>
        <n-input-group style="width: 160px; margin-right: 10px">
          <n-input-group-label>{{ t('tools.base-converter.ui.custom') }}</n-input-group-label>
          <n-input-number v-model:value="outputBase" max="64" min="2" />
        </n-input-group>

        <InputCopyable
          flex-1
          v-bind="inputProps"
          :value="errorlessConvert({ value: input, fromBase: inputBase, toBase: outputBase })"
          :placeholder="t('tools.base-converter.ui.customPlaceholder', { base: outputBase })"
        />
      </div>
    </c-card>
  </div>
</template>

<style lang="less" scoped>
.n-input-group:not(:first-child) {
  margin-top: 5px;
}
</style>
