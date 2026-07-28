<script setup lang="ts">
import InputCopyable from '../../components/InputCopyable.vue';
import { isNotThrowing } from '@/utils/boolean';
import { withDefaultOnError } from '@/utils/defaults';

const urlToParse = ref('https://me:pwd@it-tools.tech:3000/url-parser?key1=value&key2=value2#the-hash');
const { t } = useI18n();

const urlParsed = computed(() => withDefaultOnError(() => new URL(urlToParse.value), undefined));
const urlValidationRules = computed(() => [
  {
    validator: (value: string) => isNotThrowing(() => new URL(value)),
    message: t('tools.url-parser.ui.invalidUrl'),
  },
]);

const properties = computed<{ title: string; key: keyof URL }[]>(() => [
  { title: t('tools.url-parser.ui.protocol'), key: 'protocol' },
  { title: t('tools.url-parser.ui.username'), key: 'username' },
  { title: t('tools.url-parser.ui.password'), key: 'password' },
  { title: t('tools.url-parser.ui.hostname'), key: 'hostname' },
  { title: t('tools.url-parser.ui.port'), key: 'port' },
  { title: t('tools.url-parser.ui.path'), key: 'pathname' },
  { title: t('tools.url-parser.ui.params'), key: 'search' },
]);
</script>

<template>
  <c-card>
    <c-input-text
      v-model:value="urlToParse"
      :label="t('tools.url-parser.ui.inputLabel')"
      :placeholder="t('tools.url-parser.ui.inputPlaceholder')"
      raw-text
      :validation-rules="urlValidationRules"
    />

    <n-divider />

    <InputCopyable
      v-for="{ title, key } in properties"
      :key="key"
      :label="title"
      :value="(urlParsed?.[key] as string) ?? ''"
      readonly
      label-position="left"
      label-width="110px"
      mb-2
      placeholder=" "
    />

    <div
      v-for="[k, v] in Object.entries(Object.fromEntries(urlParsed?.searchParams.entries() ?? []))"
      :key="k"
      mb-2
      w-full
      flex
    >
      <div style="flex: 1 0 110px">
        <icon-mdi-arrow-right-bottom />
      </div>

      <InputCopyable :value="k" readonly />
      <InputCopyable :value="v" readonly />
    </div>
  </c-card>
</template>

<style lang="less" scoped>
.n-input-group-label {
  text-align: right;
}
.n-input-group {
  margin: 2px 0;
}
</style>
