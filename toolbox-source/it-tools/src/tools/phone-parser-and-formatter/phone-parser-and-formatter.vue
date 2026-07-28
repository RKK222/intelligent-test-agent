<script setup lang="ts">
import { getCountries, getCountryCallingCode, parsePhoneNumber } from 'libphonenumber-js/max';
import lookup from 'country-code-lookup';
import {
  getDefaultCountryCode,
} from './phone-parser-and-formatter.models';
import { withDefaultOnError } from '@/utils/defaults';
import { useValidation } from '@/composable/validation';

const rawPhone = ref('');
const defaultCountryCode = ref(getDefaultCountryCode());
const { t } = useI18n();
const regionNames = new Intl.DisplayNames(['zh-CN'], { type: 'region' });
const phoneTypeLabels = computed(() => ({
  MOBILE: t('tools.phone-parser-and-formatter.ui.types.mobile'),
  FIXED_LINE: t('tools.phone-parser-and-formatter.ui.types.fixedLine'),
  FIXED_LINE_OR_MOBILE: t('tools.phone-parser-and-formatter.ui.types.fixedOrMobile'),
  PERSONAL_NUMBER: t('tools.phone-parser-and-formatter.ui.types.personal'),
  PREMIUM_RATE: t('tools.phone-parser-and-formatter.ui.types.premium'),
  SHARED_COST: t('tools.phone-parser-and-formatter.ui.types.sharedCost'),
  TOLL_FREE: t('tools.phone-parser-and-formatter.ui.types.tollFree'),
  UAN: t('tools.phone-parser-and-formatter.ui.types.uan'),
  VOICEMAIL: t('tools.phone-parser-and-formatter.ui.types.voicemail'),
  VOIP: 'VoIP',
  PAGER: t('tools.phone-parser-and-formatter.ui.types.pager'),
}));
const validation = useValidation<string>({
  source: rawPhone,
  rules: computed(() => [
    {
      validator: value => value === '' || /^[0-9 +\-()]+$/.test(value),
      message: t('tools.phone-parser-and-formatter.ui.invalidPhone'),
    },
  ]),
});

const parsedDetails = computed(() => {
  if (!validation.isValid) {
    return undefined;
  }

  const parsed = withDefaultOnError(() => parsePhoneNumber(rawPhone.value, defaultCountryCode.value), undefined);

  if (!parsed) {
    return undefined;
  }

  return [
    {
      label: t('tools.phone-parser-and-formatter.ui.countryCode'),
      value: parsed.country,
    },
    {
      label: t('tools.phone-parser-and-formatter.ui.country'),
      value: parsed.country ? regionNames.of(parsed.country) : undefined,
    },
    {
      label: t('tools.phone-parser-and-formatter.ui.callingCode'),
      value: parsed.countryCallingCode,
    },
    {
      label: t('tools.phone-parser-and-formatter.ui.isValid'),
      value: parsed.isValid() ? t('common.yes') : t('common.no'),
    },
    {
      label: t('tools.phone-parser-and-formatter.ui.isPossible'),
      value: parsed.isPossible() ? t('common.yes') : t('common.no'),
    },
    {
      label: t('tools.phone-parser-and-formatter.ui.type'),
      value: parsed.getType() ? phoneTypeLabels.value[parsed.getType()!] : undefined,
    },
    {
      label: t('tools.phone-parser-and-formatter.ui.internationalFormat'),
      value: parsed.formatInternational(),
    },
    {
      label: t('tools.phone-parser-and-formatter.ui.nationalFormat'),
      value: parsed.formatNational(),
    },
    {
      label: t('tools.phone-parser-and-formatter.ui.e164Format'),
      value: parsed.format('E.164'),
    },
    {
      label: t('tools.phone-parser-and-formatter.ui.rfc3966Format'),
      value: parsed.format('RFC3966'),
    },
  ];
});

const countriesOptions = getCountries().map(code => ({
  label: `${regionNames.of(code) || lookup.byIso(code)?.country || code} (+${getCountryCallingCode(code)})`,
  value: code,
}));
</script>

<template>
  <div>
    <c-select v-model:value="defaultCountryCode" :label="t('tools.phone-parser-and-formatter.ui.defaultCountry')" :options="countriesOptions" searchable mb-5 />

    <c-input-text
      v-model:value="rawPhone"
      :placeholder="t('tools.phone-parser-and-formatter.ui.inputPlaceholder')"
      :label="t('tools.phone-parser-and-formatter.ui.inputLabel')"
      :validation="validation"
      mb-5
    />

    <n-table v-if="parsedDetails">
      <tbody>
        <tr v-for="{ label, value } in parsedDetails" :key="label">
          <td font-bold>
            {{ label }}
          </td>
          <td>
            <span-copyable v-if="value" :value="value" />
            <span v-else op-70>
              {{ t('tools.phone-parser-and-formatter.ui.unknown') }}
            </span>
          </td>
        </tr>
      </tbody>
    </n-table>
  </div>
</template>
