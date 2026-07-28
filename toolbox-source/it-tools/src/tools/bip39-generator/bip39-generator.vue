<script setup lang="ts">
import {
  chineseSimplifiedWordList,
  chineseTraditionalWordList,
  czechWordList,
  englishWordList,
  entropyToMnemonic,
  frenchWordList,
  generateEntropy,
  italianWordList,
  japaneseWordList,
  koreanWordList,
  mnemonicToEntropy,
  portugueseWordList,
  spanishWordList,
} from '@it-tools/bip39';
import { Copy, Refresh } from '@vicons/tabler';

import { useCopy } from '@/composable/copy';
import { useValidation } from '@/composable/validation';
import { isNotThrowing } from '@/utils/boolean';
import { withDefaultOnError } from '@/utils/defaults';

const wordLists = {
  'English': englishWordList,
  'Chinese simplified': chineseSimplifiedWordList,
  'Chinese traditional': chineseTraditionalWordList,
  'Czech': czechWordList,
  'French': frenchWordList,
  'Italian': italianWordList,
  'Japanese': japaneseWordList,
  'Korean': koreanWordList,
  'Portuguese': portugueseWordList,
  'Spanish': spanishWordList,
};
const languageLocaleKeys: Record<keyof typeof wordLists, string> = {
  'English': 'tools.bip39-generator.ui.languages.english',
  'Chinese simplified': 'tools.bip39-generator.ui.languages.chineseSimplified',
  'Chinese traditional': 'tools.bip39-generator.ui.languages.chineseTraditional',
  'Czech': 'tools.bip39-generator.ui.languages.czech',
  'French': 'tools.bip39-generator.ui.languages.french',
  'Italian': 'tools.bip39-generator.ui.languages.italian',
  'Japanese': 'tools.bip39-generator.ui.languages.japanese',
  'Korean': 'tools.bip39-generator.ui.languages.korean',
  'Portuguese': 'tools.bip39-generator.ui.languages.portuguese',
  'Spanish': 'tools.bip39-generator.ui.languages.spanish',
};
const { t } = useI18n();
const languageOptions = computed(() => Object.keys(wordLists).map(value => ({
  value,
  label: t(languageLocaleKeys[value as keyof typeof wordLists]),
})));

const entropy = ref(generateEntropy());
const passphraseInput = ref('');

const language = ref<keyof typeof wordLists>('English');
const passphrase = computed({
  get() {
    return withDefaultOnError(() => entropyToMnemonic(entropy.value, wordLists[language.value]), passphraseInput.value);
  },
  set(value: string) {
    passphraseInput.value = value;
    entropy.value = withDefaultOnError(() => mnemonicToEntropy(value, wordLists[language.value]), '');
  },
});

const entropyValidation = useValidation<string>({
  source: entropy,
  rules: computed(() => [
    {
      validator: value => value === '' || (value.length <= 32 && value.length >= 16 && value.length % 4 === 0),
      message: t('tools.bip39-generator.ui.entropyLengthError'),
    },
    {
      validator: value => /^[a-fA-F0-9]*$/.test(value),
      message: t('tools.bip39-generator.ui.entropyHexError'),
    },
  ]),
});

const mnemonicValidation = useValidation<string>({
  source: passphrase,
  rules: computed(() => [
    {
      validator: value => isNotThrowing(() => mnemonicToEntropy(value, wordLists[language.value])),
      message: t('tools.bip39-generator.ui.invalidMnemonic'),
    },
  ]),
});

function refreshEntropy() {
  entropy.value = generateEntropy();
}

const { copy: copyEntropy } = useCopy({ source: entropy, text: t('tools.bip39-generator.ui.entropyCopied') });
const { copy: copyPassphrase } = useCopy({ source: passphrase, text: t('tools.bip39-generator.ui.passphraseCopied') });
</script>

<template>
  <div>
    <n-grid cols="3" x-gap="12">
      <n-gi span="1">
        <c-select
          v-model:value="language"
          searchable
          :label="t('tools.bip39-generator.ui.language')"
          :options="languageOptions"
        />
      </n-gi>
      <n-gi span="2">
        <n-form-item
          :label="t('tools.bip39-generator.ui.entropy')"
          :feedback="entropyValidation.message"
          :validation-status="entropyValidation.status"
        >
          <n-input-group>
            <c-input-text v-model:value="entropy" :placeholder="t('tools.bip39-generator.ui.entropyPlaceholder')" />

            <c-button @click="refreshEntropy()">
              <n-icon size="22">
                <Refresh />
              </n-icon>
            </c-button>
            <c-button @click="copyEntropy()">
              <n-icon size="22">
                <Copy />
              </n-icon>
            </c-button>
          </n-input-group>
        </n-form-item>
      </n-gi>
    </n-grid>
    <n-form-item
      :label="t('tools.bip39-generator.ui.passphrase')"
      :feedback="mnemonicValidation.message"
      :validation-status="mnemonicValidation.status"
    >
      <n-input-group>
        <c-input-text v-model:value="passphrase" :placeholder="t('tools.bip39-generator.ui.passphrasePlaceholder')" raw-text />

        <c-button @click="copyPassphrase()">
          <n-icon size="22" :component="Copy" />
        </c-button>
      </n-input-group>
    </n-form-item>
  </div>
</template>
