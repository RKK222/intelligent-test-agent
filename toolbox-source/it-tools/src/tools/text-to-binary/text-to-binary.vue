<script setup lang="ts">
import { convertAsciiBinaryToText, convertTextToAsciiBinary } from './text-to-binary.models';
import { withDefaultOnError } from '@/utils/defaults';
import { useCopy } from '@/composable/copy';
import { isNotThrowing } from '@/utils/boolean';

const inputText = ref('');
const { t } = useI18n();
const binaryFromText = computed(() => convertTextToAsciiBinary(inputText.value));
const { copy: copyBinary } = useCopy({ source: binaryFromText });

const inputBinary = ref('');
const textFromBinary = computed(() => withDefaultOnError(() => convertAsciiBinaryToText(inputBinary.value), ''));
const inputBinaryValidationRules = computed(() => [
  {
    validator: (value: string) => isNotThrowing(() => convertAsciiBinaryToText(value)),
    message: t('tools.text-to-binary.ui.invalidBinary'),
  },
]);
const { copy: copyText } = useCopy({ source: textFromBinary });
</script>

<template>
  <c-card :title="t('tools.text-to-binary.ui.textToBinary')">
    <c-input-text v-model:value="inputText" multiline :placeholder="t('tools.text-to-binary.ui.textExample')" :label="t('tools.text-to-binary.ui.textInputLabel')" autosize autofocus raw-text test-id="text-to-binary-input" />
    <c-input-text v-model:value="binaryFromText" :label="t('tools.text-to-binary.ui.binaryOutputLabel')" multiline raw-text readonly mt-2 :placeholder="t('tools.text-to-binary.ui.binaryOutputPlaceholder')" test-id="text-to-binary-output" />
    <div mt-2 flex justify-center>
      <c-button :disabled="!binaryFromText" @click="copyBinary()">
        {{ t('tools.text-to-binary.ui.copyBinary') }}
      </c-button>
    </div>
  </c-card>

  <c-card :title="t('tools.text-to-binary.ui.binaryToText')">
    <c-input-text v-model:value="inputBinary" multiline :placeholder="t('tools.text-to-binary.ui.binaryExample')" :label="t('tools.text-to-binary.ui.binaryInputLabel')" autosize raw-text :validation-rules="inputBinaryValidationRules" test-id="binary-to-text-input" />
    <c-input-text v-model:value="textFromBinary" :label="t('tools.text-to-binary.ui.textOutputLabel')" multiline raw-text readonly mt-2 :placeholder="t('tools.text-to-binary.ui.textOutputPlaceholder')" test-id="binary-to-text-output" />
    <div mt-2 flex justify-center>
      <c-button :disabled="!textFromBinary" @click="copyText()">
        {{ t('tools.text-to-binary.ui.copyText') }}
      </c-button>
    </div>
  </c-card>
</template>
