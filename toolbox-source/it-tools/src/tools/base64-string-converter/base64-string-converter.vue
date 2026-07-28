<script setup lang="ts">
import { useCopy } from '@/composable/copy';
import { base64ToText, isValidBase64, textToBase64 } from '@/utils/base64';
import { withDefaultOnError } from '@/utils/defaults';

const encodeUrlSafe = useStorage('base64-string-converter--encode-url-safe', false);
const decodeUrlSafe = useStorage('base64-string-converter--decode-url-safe', false);
const { t } = useI18n();

const textInput = ref('');
const base64Output = computed(() => textToBase64(textInput.value, { makeUrlSafe: encodeUrlSafe.value }));
const { copy: copyTextBase64 } = useCopy({ source: base64Output, text: t('tools.base64-string-converter.ui.base64Copied') });

const base64Input = ref('');
const textOutput = computed(() =>
  withDefaultOnError(() => base64ToText(base64Input.value.trim(), { makeUrlSafe: decodeUrlSafe.value }), ''),
);
const { copy: copyText } = useCopy({ source: textOutput, text: t('tools.base64-string-converter.ui.stringCopied') });
const b64ValidationRules = computed(() => [
  {
    message: t('tools.base64-string-converter.ui.invalidBase64'),
    validator: (value: string) => isValidBase64(value.trim(), { makeUrlSafe: decodeUrlSafe.value }),
  },
]);
const b64ValidationWatch = [decodeUrlSafe];
</script>

<template>
  <c-card :title="t('tools.base64-string-converter.ui.stringToBase64')">
    <n-form-item :label="t('tools.base64-string-converter.ui.encodeUrlSafe')" label-placement="left">
      <n-switch v-model:value="encodeUrlSafe" />
    </n-form-item>
    <c-input-text
      v-model:value="textInput"
      multiline
      :placeholder="t('tools.base64-string-converter.ui.stringPlaceholder')"
      rows="5"
      :label="t('tools.base64-string-converter.ui.stringToEncode')"
      raw-text
      mb-5
    />

    <c-input-text
      :label="t('tools.base64-string-converter.ui.base64OfString')"
      :value="base64Output"
      multiline
      readonly
      :placeholder="t('tools.base64-string-converter.ui.base64OutputPlaceholder')"
      rows="5"
      mb-5
    />

    <div flex justify-center>
      <c-button @click="copyTextBase64()">
        {{ t('tools.base64-string-converter.ui.copyBase64') }}
      </c-button>
    </div>
  </c-card>

  <c-card :title="t('tools.base64-string-converter.ui.base64ToString')">
    <n-form-item :label="t('tools.base64-string-converter.ui.decodeUrlSafe')" label-placement="left">
      <n-switch v-model:value="decodeUrlSafe" />
    </n-form-item>
    <c-input-text
      v-model:value="base64Input"
      multiline
      :placeholder="t('tools.base64-string-converter.ui.base64InputPlaceholder')"
      rows="5"
      :validation-rules="b64ValidationRules"
      :validation-watch="b64ValidationWatch"
      :label="t('tools.base64-string-converter.ui.base64ToDecode')"
      mb-5
    />

    <c-input-text
      v-model:value="textOutput"
      :label="t('tools.base64-string-converter.ui.decodedString')"
      :placeholder="t('tools.base64-string-converter.ui.decodedPlaceholder')"
      multiline
      rows="5"
      readonly
      mb-5
    />

    <div flex justify-center>
      <c-button @click="copyText()">
        {{ t('tools.base64-string-converter.ui.copyDecoded') }}
      </c-button>
    </div>
  </c-card>
</template>
