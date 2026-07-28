<script setup lang="ts">
import { useStorage } from '@vueuse/core';
import { convert } from './list-converter.models';
import type { ConvertOptions } from './list-converter.types';

const { t } = useI18n();
const sortOrderOptions = computed(() => [
  {
    label: t('tools.list-converter.ui.sortAscending'),
    value: 'asc',
    disabled: false,
  },
  {
    label: t('tools.list-converter.ui.sortDescending'),
    value: 'desc',
    disabled: false,
  },
]);

const conversionConfig = useStorage<ConvertOptions>('list-converter:conversionConfig', {
  lowerCase: false,
  trimItems: true,
  removeDuplicates: true,
  keepLineBreaks: false,
  itemPrefix: '',
  itemSuffix: '',
  listPrefix: '',
  listSuffix: '',
  reverseList: false,
  sortList: null,
  separator: ', ',
});

function transformer(value: string) {
  return convert(value, conversionConfig.value);
}
</script>

<template>
  <div style="flex: 0 0 100%">
    <div style="margin: 0 auto; max-width: 600px">
      <c-card>
        <div flex>
          <div>
            <n-form-item :label="t('tools.list-converter.ui.trimItems')" label-placement="left" label-width="150" :show-feedback="false" mb-2>
              <n-switch v-model:value="conversionConfig.trimItems" />
            </n-form-item>
            <n-form-item :label="t('tools.list-converter.ui.removeDuplicates')" label-placement="left" label-width="150" :show-feedback="false" mb-2>
              <n-switch v-model:value="conversionConfig.removeDuplicates" data-test-id="removeDuplicates" />
            </n-form-item>
            <n-form-item
              :label="t('tools.list-converter.ui.lowercase')"
              label-placement="left"
              label-width="150"
              :show-feedback="false"
              mb-2
            >
              <n-switch v-model:value="conversionConfig.lowerCase" />
            </n-form-item>
            <n-form-item :label="t('tools.list-converter.ui.keepLineBreaks')" label-placement="left" label-width="150" :show-feedback="false" mb-2>
              <n-switch v-model:value="conversionConfig.keepLineBreaks" />
            </n-form-item>
          </div>
          <div flex-1>
            <c-select
              v-model:value="conversionConfig.sortList"
              :label="t('tools.list-converter.ui.sortList')"
              label-position="left"
              label-width="120px"
              label-align="right"
              mb-2
              :options="sortOrderOptions"
              w-full
              :disabled="conversionConfig.reverseList"
              data-test-id="sortList"
              :placeholder="t('tools.list-converter.ui.sortPlaceholder')"
            />

            <c-input-text
              v-model:value="conversionConfig.separator"
              :label="t('tools.list-converter.ui.separator')"
              label-position="left"
              label-width="120px"
              label-align="right"
              mb-2
              placeholder=","
            />

            <n-form-item :label="t('tools.list-converter.ui.wrapItem')" label-placement="left" label-width="120" :show-feedback="false" mb-2>
              <c-input-text
                v-model:value="conversionConfig.itemPrefix"
                :placeholder="t('tools.list-converter.ui.itemPrefix')"
                test-id="itemPrefix"
              />
              <c-input-text
                v-model:value="conversionConfig.itemSuffix"
                :placeholder="t('tools.list-converter.ui.itemSuffix')"
                test-id="itemSuffix"
              />
            </n-form-item>
            <n-form-item :label="t('tools.list-converter.ui.wrapList')" label-placement="left" label-width="120" :show-feedback="false" mb-2>
              <c-input-text
                v-model:value="conversionConfig.listPrefix"
                :placeholder="t('tools.list-converter.ui.listPrefix')"
                test-id="listPrefix"
              />
              <c-input-text
                v-model:value="conversionConfig.listSuffix"
                :placeholder="t('tools.list-converter.ui.listSuffix')"
                test-id="listSuffix"
              />
            </n-form-item>
          </div>
        </div>
      </c-card>
    </div>
  </div>
  <format-transformer
    :input-label="t('tools.list-converter.ui.inputLabel')"
    :input-placeholder="t('tools.list-converter.ui.inputPlaceholder')"
    :output-label="t('tools.list-converter.ui.outputLabel')"
    :transformer="transformer"
  />
</template>
