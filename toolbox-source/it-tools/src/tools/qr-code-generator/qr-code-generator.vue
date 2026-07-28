<script setup lang="ts">
import type { QRCodeErrorCorrectionLevel } from 'qrcode';
import { useQRCode } from './useQRCode';
import { useDownloadFileFromBase64 } from '@/composable/downloadBase64';

const foreground = ref('#000000ff');
const background = ref('#ffffffff');
const errorCorrectionLevel = ref<QRCodeErrorCorrectionLevel>('medium');
const { t } = useI18n();

const errorCorrectionLevels = ['low', 'medium', 'quartile', 'high'];
const errorCorrectionOptions = computed(() => errorCorrectionLevels.map(value => ({
  label: t(`tools.qrcode-generator.ui.errorCorrection.${value}`),
  value,
})));

const text = ref('https://it-tools.tech');
const { qrcode } = useQRCode({
  text,
  color: {
    background,
    foreground,
  },
  errorCorrectionLevel,
  options: { width: 1024 },
});

const { download } = useDownloadFileFromBase64({ source: qrcode, filename: 'qr-code.png' });
</script>

<template>
  <c-card>
    <n-grid x-gap="12" y-gap="12" cols="1 600:3">
      <n-gi span="2">
        <c-input-text
          v-model:value="text"
          label-position="left"
          label-width="130px"
          label-align="right"
          :label="t('tools.qrcode-generator.ui.textLabel')"
          multiline
          rows="1"
          autosize
          :placeholder="t('tools.qrcode-generator.ui.textPlaceholder')"
          mb-6
        />
        <n-form label-width="130" label-placement="left">
          <n-form-item :label="t('tools.qrcode-generator.ui.foregroundColor')">
            <n-color-picker v-model:value="foreground" :modes="['hex']" />
          </n-form-item>
          <n-form-item :label="t('tools.qrcode-generator.ui.backgroundColor')">
            <n-color-picker v-model:value="background" :modes="['hex']" />
          </n-form-item>
          <c-select
            v-model:value="errorCorrectionLevel"
            :label="t('tools.qrcode-generator.ui.errorResistance')"
            label-position="left"
            label-width="130px"
            label-align="right"
            :options="errorCorrectionOptions"
          />
        </n-form>
      </n-gi>
      <n-gi>
        <div flex flex-col items-center gap-3>
          <n-image :src="qrcode" width="200" />
          <c-button @click="download">
            {{ t('tools.qrcode-generator.ui.download') }}
          </c-button>
        </div>
      </n-gi>
    </n-grid>
  </c-card>
</template>
