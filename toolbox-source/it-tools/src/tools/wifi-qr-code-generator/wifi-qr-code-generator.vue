<script setup lang="ts">
import {
  EAPMethods,
  EAPPhase2Methods,
  useWifiQRCode,
} from './useQRCode';
import { useDownloadFileFromBase64 } from '@/composable/downloadBase64';

const foreground = ref('#000000ff');
const background = ref('#ffffffff');
const { t } = useI18n();

const ssid = ref();
const password = ref();
const eapMethod = ref();
const isHiddenSSID = ref(false);
const eapAnonymous = ref(false);
const eapIdentity = ref();
const eapPhase2Method = ref();
const encryptionOptions = computed(() => [
  { label: t('tools.wifi-qrcode-generator.ui.noPassword'), value: 'nopass' },
  { label: 'WPA/WPA2', value: 'WPA' },
  { label: 'WEP', value: 'WEP' },
  { label: 'WPA2-EAP', value: 'WPA2-EAP' },
]);

const { qrcode, encryption } = useWifiQRCode({
  ssid,
  password,
  eapMethod,
  isHiddenSSID,
  eapAnonymous,
  eapIdentity,
  eapPhase2Method,
  color: {
    background,
    foreground,
  },
  options: { width: 1024 },
});

const { download } = useDownloadFileFromBase64({ source: qrcode, filename: 'qr-code.png' });
</script>

<template>
  <c-card>
    <div grid grid-cols-1 gap-12>
      <div>
        <c-select
          v-model:value="encryption"
          mb-4
          :label="t('tools.wifi-qrcode-generator.ui.encryptionMethod')"
          default-value="WPA"
          label-position="left"
          label-width="130px"
          label-align="right"
          :options="encryptionOptions"
        />
        <div class="mb-6 flex flex-row items-center gap-2">
          <c-input-text
            v-model:value="ssid"
            label-position="left"
            label-width="130px"
            label-align="right"
            :label="t('tools.wifi-qrcode-generator.ui.ssid')"
            rows="1"
            autosize
            :placeholder="t('tools.wifi-qrcode-generator.ui.ssidPlaceholder')"
            mb-6
          />
          <n-checkbox v-model:checked="isHiddenSSID">
            {{ t('tools.wifi-qrcode-generator.ui.hiddenSsid') }}
          </n-checkbox>
        </div>
        <c-input-text
          v-if="encryption !== 'nopass'"
          v-model:value="password"
          label-position="left"
          label-width="130px"
          label-align="right"
          :label="t('tools.wifi-qrcode-generator.ui.password')"
          rows="1"
          autosize
          type="password"
          :placeholder="t('tools.wifi-qrcode-generator.ui.passwordPlaceholder')"
          mb-6
        />
        <c-select
          v-if="encryption === 'WPA2-EAP'"
          v-model:value="eapMethod"
          :label="t('tools.wifi-qrcode-generator.ui.eapMethod')"
          label-position="left"
          label-width="130px"
          label-align="right"
          :options="EAPMethods.map((method) => ({ label: method, value: method }))"
          searchable mb-4
        />
        <div v-if="encryption === 'WPA2-EAP'" class="mb-6 flex flex-row items-center gap-2">
          <c-input-text
            v-model:value="eapIdentity"
            label-position="left"
            label-width="130px"
            label-align="right"
            :label="t('tools.wifi-qrcode-generator.ui.identity')"
            rows="1"
            autosize
            :placeholder="t('tools.wifi-qrcode-generator.ui.identityPlaceholder')"
            mb-6
          />
          <n-checkbox v-model:checked="eapAnonymous">
            {{ t('tools.wifi-qrcode-generator.ui.anonymous') }}
          </n-checkbox>
        </div>
        <c-select
          v-if="encryption === 'WPA2-EAP'"
          v-model:value="eapPhase2Method"
          :label="t('tools.wifi-qrcode-generator.ui.eapPhase2Method')"
          label-position="left"
          label-width="130px"
          label-align="right"
          :options="EAPPhase2Methods.map((method) => ({ label: method, value: method }))"
          searchable mb-4
        />
        <n-form label-width="130" label-placement="left">
          <n-form-item :label="t('tools.wifi-qrcode-generator.ui.foregroundColor')">
            <n-color-picker v-model:value="foreground" :modes="['hex']" />
          </n-form-item>
          <n-form-item :label="t('tools.wifi-qrcode-generator.ui.backgroundColor')">
            <n-color-picker v-model:value="background" :modes="['hex']" />
          </n-form-item>
        </n-form>
      </div>
      <div v-if="qrcode">
        <div flex flex-col items-center gap-3>
          <img :alt="t('tools.wifi-qrcode-generator.ui.imageAlt')" :src="qrcode" width="200">
          <c-button @click="download">
            {{ t('tools.wifi-qrcode-generator.ui.download') }}
          </c-button>
        </div>
      </div>
    </div>
  </c-card>
</template>
