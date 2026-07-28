<script setup lang="ts">
import { useTimestamp } from '@vueuse/core';
import { useThemeVars } from 'naive-ui';
import { useQRCode } from '../qr-code-generator/useQRCode';
import { base32toHex, buildKeyUri, generateSecret, generateTOTP, getCounterFromTime } from './otp.service';
import TokenDisplay from './token-display.vue';
import { useStyleStore } from '@/stores/style.store';
import InputCopyable from '@/components/InputCopyable.vue';
import { computedRefreshable } from '@/composable/computedRefreshable';

const now = useTimestamp();
const interval = computed(() => (now.value / 1000) % 30);
const theme = useThemeVars();
const styleStore = useStyleStore();

const secret = ref(generateSecret());
const { t } = useI18n();

function refreshSecret() {
  secret.value = generateSecret();
}

const [tokens] = computedRefreshable(
  () => ({
    previous: generateTOTP({ key: secret.value, now: now.value - 30000 }),
    current: generateTOTP({ key: secret.value, now: now.value }),
    next: generateTOTP({ key: secret.value, now: now.value + 30000 }),
  }),
  { throttle: 500 },
);

const keyUri = computed(() => buildKeyUri({ secret: secret.value }));

const { qrcode } = useQRCode({
  text: keyUri,
  color: {
    background: computed(() => (styleStore.isDarkTheme ? '#ffffff' : '#00000000')),
    foreground: '#000000',
  },
  options: { width: 210 },
});

const secretValidationRules = computed(() => [
  {
    message: t('tools.otp-generator.ui.secretBase32Error'),
    validator: (value: string) => value.toUpperCase().match(/^[A-Z234567]+$/),
  },
  {
    message: t('tools.otp-generator.ui.secretRequired'),
    validator: (value: string) => value !== '',
  },
]);
</script>

<template>
  <div style="max-width: 350px">
    <c-input-text
      v-model:value="secret"
      :label="t('tools.otp-generator.ui.secret')"
      :placeholder="t('tools.otp-generator.ui.secretPlaceholder')"
      mb-5
      :validation-rules="secretValidationRules"
    >
      <template #suffix>
        <c-tooltip :tooltip="t('tools.otp-generator.ui.generateSecret')">
          <c-button circle variant="text" size="small" @click="refreshSecret">
            <icon-mdi-refresh />
          </c-button>
        </c-tooltip>
      </template>
    </c-input-text>

    <div>
      <TokenDisplay :tokens="tokens" />

      <n-progress :percentage="(100 * interval) / 30" :color="theme.primaryColor" :show-indicator="false" />
      <div style="text-align: center">
        {{ t('tools.otp-generator.ui.nextIn', { seconds: String(Math.floor(30 - interval)).padStart(2, '0') }) }}
      </div>
    </div>
    <div mt-4 flex flex-col items-center justify-center gap-3>
      <n-image :src="qrcode" />
      <c-button :href="keyUri" target="_blank">
        {{ t('tools.otp-generator.ui.openKeyUri') }}
      </c-button>
    </div>
  </div>
  <div style="max-width: 350px">
    <InputCopyable
      :label="t('tools.otp-generator.ui.secretHex')"
      :value="base32toHex(secret)"
      readonly
      :placeholder="t('tools.otp-generator.ui.secretHexPlaceholder')"
      mb-5
    />

    <InputCopyable
      :label="t('tools.otp-generator.ui.epoch')"
      :value="Math.floor(now / 1000).toString()"
      readonly
      mb-5
      :placeholder="t('tools.otp-generator.ui.epochPlaceholder')"
    />

    <p>{{ t('tools.otp-generator.ui.iteration') }}</p>

    <InputCopyable
      :value="String(getCounterFromTime({ now, timeStep: 30 }))"
      readonly
      :label="t('tools.otp-generator.ui.count')"
      label-position="left"
      label-width="90px"
      label-align="right"
      :placeholder="t('tools.otp-generator.ui.countPlaceholder')"
    />

    <InputCopyable
      :value="getCounterFromTime({ now, timeStep: 30 }).toString(16).padStart(16, '0')"
      readonly
      :placeholder="t('tools.otp-generator.ui.paddedHexPlaceholder')"
      label-position="left"
      label-width="90px"
      label-align="right"
      :label="t('tools.otp-generator.ui.paddedHex')"
    />
  </div>
</template>

<style lang="less" scoped>
.n-progress {
  margin-top: 10px;
  ::v-deep(.n-progress-graph-line-fill) {
    transition-duration: 0.05s !important;
  }
}

.token {
  text-align: center;
  &.token-current {
    font-size: 20px;
  }
}
</style>
