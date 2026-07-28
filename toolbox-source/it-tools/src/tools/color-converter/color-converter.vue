<script setup lang="ts">
import type { Colord } from 'colord';
import { colord, extend } from 'colord';
import _ from 'lodash';
import cmykPlugin from 'colord/plugins/cmyk';
import hwbPlugin from 'colord/plugins/hwb';
import namesPlugin from 'colord/plugins/names';
import lchPlugin from 'colord/plugins/lch';
import { buildColorFormat } from './color-converter.models';

extend([cmykPlugin, hwbPlugin, namesPlugin, lchPlugin]);
const { t } = useI18n();

function createFormats() {
  return {
    picker: buildColorFormat({
      label: t('tools.color-converter.ui.colorPicker'),
      format: (v: Colord) => v.toHex(),
      invalidMessage: t('tools.color-converter.ui.invalidColorPicker'),
      type: 'color-picker',
    }),
    hex: buildColorFormat({
      label: 'HEX',
      format: (v: Colord) => v.toHex(),
      placeholder: t('tools.color-converter.ui.example', { value: '#ff0000' }),
      invalidMessage: t('tools.color-converter.ui.invalidFormat', { format: 'HEX' }),
    }),
    rgb: buildColorFormat({
      label: 'rgb',
      format: (v: Colord) => v.toRgbString(),
      placeholder: t('tools.color-converter.ui.example', { value: 'rgb(255, 0, 0)' }),
      invalidMessage: t('tools.color-converter.ui.invalidFormat', { format: 'RGB' }),
    }),
    hsl: buildColorFormat({
      label: 'hsl',
      format: (v: Colord) => v.toHslString(),
      placeholder: t('tools.color-converter.ui.example', { value: 'hsl(0, 100%, 50%)' }),
      invalidMessage: t('tools.color-converter.ui.invalidFormat', { format: 'HSL' }),
    }),
    hwb: buildColorFormat({
      label: 'hwb',
      format: (v: Colord) => v.toHwbString(),
      placeholder: t('tools.color-converter.ui.example', { value: 'hwb(0, 0%, 0%)' }),
      invalidMessage: t('tools.color-converter.ui.invalidFormat', { format: 'HWB' }),
    }),
    lch: buildColorFormat({
      label: 'lch',
      format: (v: Colord) => v.toLchString(),
      placeholder: t('tools.color-converter.ui.example', { value: 'lch(53.24, 104.55, 40.85)' }),
      invalidMessage: t('tools.color-converter.ui.invalidFormat', { format: 'LCH' }),
    }),
    cmyk: buildColorFormat({
      label: 'cmyk',
      format: (v: Colord) => v.toCmykString(),
      placeholder: t('tools.color-converter.ui.example', { value: 'cmyk(0, 100%, 100%, 0)' }),
      invalidMessage: t('tools.color-converter.ui.invalidFormat', { format: 'CMYK' }),
    }),
    name: buildColorFormat({
      label: t('tools.color-converter.ui.name'),
      format: (v: Colord) => v.toName({ closest: true }) ?? t('tools.color-converter.ui.unknown'),
      placeholder: t('tools.color-converter.ui.example', { value: 'red' }),
      invalidMessage: t('tools.color-converter.ui.invalidName'),
    }),
  };
}
const formats = createFormats();

updateColorValue(colord('#1ea54c'));

function updateColorValue(value: Colord | undefined, omitLabel?: string) {
  if (value === undefined) {
    return;
  }

  if (!value.isValid()) {
    return;
  }

  _.forEach(formats, ({ value: valueRef, format }, key) => {
    if (key !== omitLabel) {
      valueRef.value = format(value);
    }
  });
}
</script>

<template>
  <c-card>
    <template v-for="({ label, parse, placeholder, validation, type }, key) in formats" :key="key">
      <input-copyable
        v-if="type === 'text'"
        v-model:value="formats[key].value.value"
        :test-id="`input-${key}`"
        :label="`${label}:`"
        label-position="left"
        label-width="100px"
        label-align="right"
        :placeholder="placeholder"
        :validation="validation"
        raw-text
        clearable
        mt-2
        @update:value="(v:string) => updateColorValue(parse(v), key)"
      />

      <n-form-item v-else-if="type === 'color-picker'" :label="`${label}:`" label-width="100" label-placement="left" :show-feedback="false">
        <n-color-picker
          v-model:value="formats[key].value.value"
          placement="bottom-end"
          @update:value="(v:string) => updateColorValue(parse(v), key)"
        />
      </n-form-item>
    </template>
  </c-card>
</template>
