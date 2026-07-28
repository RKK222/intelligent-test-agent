<script setup lang="ts">
import { useCopy } from '@/composable/copy';
import { useValidation } from '@/composable/validation';
import { isNotThrowing } from '@/utils/boolean';
import { withDefaultOnError } from '@/utils/defaults';

const encodeInput = ref('Hello world :)');
const { t } = useI18n();
const encodeOutput = computed(() => withDefaultOnError(() => encodeURIComponent(encodeInput.value), ''));

const encodedValidation = useValidation<string>({
  source: encodeInput,
  rules: computed(() => [
    {
      validator: value => isNotThrowing(() => encodeURIComponent(value)),
      message: t('tools.url-encoder.ui.parseError'),
    },
  ]),
});

const { copy: copyEncoded } = useCopy({ source: encodeOutput, text: t('tools.url-encoder.ui.encodedCopied') });

const decodeInput = ref('Hello%20world%20%3A)');
const decodeOutput = computed(() => withDefaultOnError(() => decodeURIComponent(decodeInput.value), ''));

const decodeValidation = useValidation<string>({
  source: decodeInput,
  rules: computed(() => [
    {
      validator: value => isNotThrowing(() => decodeURIComponent(value)),
      message: t('tools.url-encoder.ui.parseError'),
    },
  ]),
});

const { copy: copyDecoded } = useCopy({ source: decodeOutput, text: t('tools.url-encoder.ui.decodedCopied') });
</script>

<template>
  <c-card :title="t('tools.url-encoder.ui.encode')">
    <c-input-text
      v-model:value="encodeInput"
      :label="t('tools.url-encoder.ui.inputString')"
      :validation="encodedValidation"
      multiline
      autosize
      :placeholder="t('tools.url-encoder.ui.encodePlaceholder')"
      rows="2"
      mb-3
    />

    <c-input-text
      :label="t('tools.url-encoder.ui.encodedString')"
      :value="encodeOutput"
      multiline
      autosize
      readonly
      :placeholder="t('tools.url-encoder.ui.encodedPlaceholder')"
      rows="2"
      mb-3
    />

    <div flex justify-center>
      <c-button @click="copyEncoded()">
        {{ t('common.copy') }}
      </c-button>
    </div>
  </c-card>
  <c-card :title="t('tools.url-encoder.ui.decode')">
    <c-input-text
      v-model:value="decodeInput"
      :label="t('tools.url-encoder.ui.encodedInput')"
      :validation="decodeValidation"
      multiline
      autosize
      :placeholder="t('tools.url-encoder.ui.decodePlaceholder')"
      rows="2"
      mb-3
    />

    <c-input-text
      :label="t('tools.url-encoder.ui.decodedString')"
      :value="decodeOutput"
      multiline
      autosize
      readonly
      :placeholder="t('tools.url-encoder.ui.decodedPlaceholder')"
      rows="2"
      mb-3
    />

    <div flex justify-center>
      <c-button @click="copyDecoded()">
        {{ t('common.copy') }}
      </c-button>
    </div>
  </c-card>
</template>
