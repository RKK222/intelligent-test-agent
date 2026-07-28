<script setup lang="ts">
import { escape, unescape } from 'lodash';

import { useCopy } from '@/composable/copy';

const { t } = useI18n();

const escapeInput = ref('<title>IT Tool</title>');
const escapeOutput = computed(() => escape(escapeInput.value));
const { copy: copyEscaped } = useCopy({ source: escapeOutput });

const unescapeInput = ref('&lt;title&gt;IT Tool&lt;/title&gt;');
const unescapeOutput = computed(() => unescape(unescapeInput.value));
const { copy: copyUnescaped } = useCopy({ source: unescapeOutput });
</script>

<template>
  <c-card :title="t('tools.html-entities.ui.escapeTitle')">
    <n-form-item :label="t('tools.html-entities.ui.inputString')">
      <c-input-text
        v-model:value="escapeInput"
        multiline
        :placeholder="t('tools.html-entities.ui.escapePlaceholder')"
        rows="3"
        autosize
        raw-text
      />
    </n-form-item>

    <n-form-item :label="t('tools.html-entities.ui.escapedString')">
      <c-input-text
        multiline
        readonly
        :placeholder="t('tools.html-entities.ui.escapedPlaceholder')"
        :value="escapeOutput"
        rows="3"
        autosize
      />
    </n-form-item>

    <div flex justify-center>
      <c-button @click="copyEscaped()">
        {{ t('common.copy') }}
      </c-button>
    </div>
  </c-card>
  <c-card :title="t('tools.html-entities.ui.unescapeTitle')">
    <n-form-item :label="t('tools.html-entities.ui.escapedInput')">
      <c-input-text
        v-model:value="unescapeInput"
        multiline
        :placeholder="t('tools.html-entities.ui.unescapePlaceholder')"
        rows="3"
        autosize
        raw-text
      />
    </n-form-item>

    <n-form-item :label="t('tools.html-entities.ui.unescapedString')">
      <c-input-text
        :value="unescapeOutput"
        multiline
        readonly
        :placeholder="t('tools.html-entities.ui.unescapedPlaceholder')"
        rows="3"
        autosize
      />
    </n-form-item>

    <div flex justify-center>
      <c-button @click="copyUnescaped()">
        {{ t('common.copy') }}
      </c-button>
    </div>
  </c-card>
</template>
