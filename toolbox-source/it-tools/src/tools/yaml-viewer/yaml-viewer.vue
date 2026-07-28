<script setup lang="ts">
import yaml from 'yaml';
import { useStorage } from '@vueuse/core';
import { formatYaml } from './yaml-models';
import { withDefaultOnError } from '@/utils/defaults';
import { useValidation } from '@/composable/validation';
import TextareaCopyable from '@/components/TextareaCopyable.vue';

const inputElement = ref<HTMLElement>();
const { t } = useI18n();

const rawYaml = useStorage('yaml-prettify:raw-yaml', '');
const indentSize = useStorage('yaml-prettify:indent-size', 2);
const sortKeys = useStorage('yaml-prettify:sort-keys', false);

const cleanYaml = computed(() => withDefaultOnError(() => formatYaml({ rawYaml, indentSize, sortKeys }), ''));

const rawYamlValidation = useValidation<string>({
  source: rawYaml,
  rules: computed(() => [
    {
      validator: v => v === '' || yaml.parse(v),
      message: t('tools.yaml-prettify.ui.invalidYaml'),
    },
  ]),
});
</script>

<template>
  <div style="flex: 0 0 100%">
    <div style="margin: 0 auto; max-width: 600px" flex justify-center gap-3>
      <n-form-item :label="t('tools.yaml-prettify.ui.sortKeys')" label-placement="left" label-width="100">
        <n-switch v-model:value="sortKeys" />
      </n-form-item>
      <n-form-item :label="t('tools.yaml-prettify.ui.indentSize')" label-placement="left" label-width="100" :show-feedback="false">
        <n-input-number v-model:value="indentSize" min="1" max="10" style="width: 100px" />
      </n-form-item>
    </div>
  </div>

  <n-form-item
    :label="t('tools.yaml-prettify.ui.inputLabel')"
    :feedback="rawYamlValidation.message"
    :validation-status="rawYamlValidation.status"
  >
    <c-input-text
      ref="inputElement"
      v-model:value="rawYaml"
      :placeholder="t('tools.yaml-prettify.ui.inputPlaceholder')"
      rows="20"
      multiline
      autocomplete="off"
      autocorrect="off"
      autocapitalize="off"
      spellcheck="false"
      monospace
    />
  </n-form-item>
  <n-form-item :label="t('tools.yaml-prettify.ui.outputLabel')">
    <TextareaCopyable :value="cleanYaml" language="yaml" :follow-height-of="inputElement" />
  </n-form-item>
</template>

<style lang="less" scoped>
.result-card {
  position: relative;
  .copy-button {
    position: absolute;
    top: 10px;
    right: 10px;
  }
}
</style>
