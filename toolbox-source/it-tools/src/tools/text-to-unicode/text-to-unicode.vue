<script setup lang="ts">
import { convertTextToUnicode, convertUnicodeToText } from './text-to-unicode.service';
import { useCopy } from '@/composable/copy';

const inputText = ref('');
const { t } = useI18n();
const unicodeFromText = computed(() => inputText.value.trim() === '' ? '' : convertTextToUnicode(inputText.value));
const { copy: copyUnicode } = useCopy({ source: unicodeFromText });

const inputUnicode = ref('');
const textFromUnicode = computed(() => inputUnicode.value.trim() === '' ? '' : convertUnicodeToText(inputUnicode.value));
const { copy: copyText } = useCopy({ source: textFromUnicode });
</script>

<template>
  <c-card :title="t('tools.text-to-unicode.ui.textToUnicode')">
    <c-input-text v-model:value="inputText" multiline :placeholder="t('tools.text-to-unicode.ui.textExample')" :label="t('tools.text-to-unicode.ui.textInputLabel')" autosize autofocus raw-text test-id="text-to-unicode-input" />
    <c-input-text v-model:value="unicodeFromText" :label="t('tools.text-to-unicode.ui.unicodeOutputLabel')" multiline raw-text readonly mt-2 :placeholder="t('tools.text-to-unicode.ui.unicodeOutputPlaceholder')" test-id="text-to-unicode-output" />
    <div mt-2 flex justify-center>
      <c-button :disabled="!unicodeFromText" @click="copyUnicode()">
        {{ t('tools.text-to-unicode.ui.copyUnicode') }}
      </c-button>
    </div>
  </c-card>

  <c-card :title="t('tools.text-to-unicode.ui.unicodeToText')">
    <c-input-text v-model:value="inputUnicode" multiline :placeholder="t('tools.text-to-unicode.ui.unicodeInputPlaceholder')" :label="t('tools.text-to-unicode.ui.unicodeInputLabel')" autosize raw-text test-id="unicode-to-text-input" />
    <c-input-text v-model:value="textFromUnicode" :label="t('tools.text-to-unicode.ui.textOutputLabel')" multiline raw-text readonly mt-2 :placeholder="t('tools.text-to-unicode.ui.textOutputPlaceholder')" test-id="unicode-to-text-output" />
    <div mt-2 flex justify-center>
      <c-button :disabled="!textFromUnicode" @click="copyText()">
        {{ t('tools.text-to-unicode.ui.copyText') }}
      </c-button>
    </div>
  </c-card>
</template>
