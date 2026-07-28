<script setup lang="ts">
import { codesByCategories } from './http-status-codes.constants';
import { useFuzzySearch } from '@/composable/fuzzySearch';

const search = ref('');
const { t } = useI18n();
const localizedCodesByCategories = codesByCategories.map(({ categoryKey, codes }) => ({
  category: t(categoryKey),
  codes: codes.map(({ nameKey, descriptionKey, ...code }) => ({
    ...code,
    name: t(nameKey),
    description: t(descriptionKey),
  })),
}));

const { searchResult } = useFuzzySearch({
  search,
  data: localizedCodesByCategories.flatMap(({ codes, category }) => codes.map(code => ({ ...code, category }))),
  options: {
    keys: [{ name: 'code', weight: 3 }, { name: 'name', weight: 2 }, 'description', 'category'],
  },
});

const codesByCategoryFiltered = computed(() => {
  if (!search.value) {
    return localizedCodesByCategories;
  }

  return [{ category: t('tools.http-status-codes.ui.searchResults'), codes: searchResult.value }];
});
</script>

<template>
  <div>
    <c-input-text
      v-model:value="search"
      :placeholder="t('tools.http-status-codes.ui.searchPlaceholder')"
      autofocus raw-text mb-10
    />

    <div v-for="{ codes, category } of codesByCategoryFiltered" :key="category" mb-8>
      <div mb-2 text-xl>
        {{ category }}
      </div>

      <c-card v-for="{ code, description, name, type } of codes" :key="code" mb-2>
        <div text-lg font-bold>
          {{ code }} {{ name }}
        </div>
        <div op-70>
          {{ description }} {{ type !== 'HTTP' ? t('tools.http-status-codes.ui.forProtocol', { type }) : '' }}
        </div>
      </c-card>
    </div>
  </div>
</template>
