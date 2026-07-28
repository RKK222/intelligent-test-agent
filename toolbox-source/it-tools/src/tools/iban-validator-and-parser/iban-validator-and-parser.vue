<script setup lang="ts">
import { ValidationErrorsIBAN, extractIBAN, friendlyFormatIBAN, isQRIBAN, validateIBAN } from 'ibantools';
import { getKnownErrorCodes } from './iban-validator-and-parser.service';
import type { CKeyValueListItems } from '@/ui/c-key-value-list/c-key-value-list.types';

const rawIban = ref('');
const { t } = useI18n();
const errorMessages = computed<Record<ValidationErrorsIBAN, string>>(() => ({
  [ValidationErrorsIBAN.NoIBANProvided]: t('tools.iban-validator-and-parser.ui.errors.noIban'),
  [ValidationErrorsIBAN.NoIBANCountry]: t('tools.iban-validator-and-parser.ui.errors.noCountry'),
  [ValidationErrorsIBAN.WrongBBANLength]: t('tools.iban-validator-and-parser.ui.errors.wrongBbanLength'),
  [ValidationErrorsIBAN.WrongBBANFormat]: t('tools.iban-validator-and-parser.ui.errors.wrongBbanFormat'),
  [ValidationErrorsIBAN.ChecksumNotNumber]: t('tools.iban-validator-and-parser.ui.errors.checksumNotNumber'),
  [ValidationErrorsIBAN.WrongIBANChecksum]: t('tools.iban-validator-and-parser.ui.errors.wrongChecksum'),
  [ValidationErrorsIBAN.WrongAccountBankBranchChecksum]: t('tools.iban-validator-and-parser.ui.errors.wrongBranchChecksum'),
  [ValidationErrorsIBAN.QRIBANNotAllowed]: t('tools.iban-validator-and-parser.ui.errors.qrNotAllowed'),
}));

const ibanInfo = computed<CKeyValueListItems>(() => {
  const iban = rawIban.value.toUpperCase().replace(/\s/g, '').replace(/-/g, '');

  if (iban === '') {
    return [];
  }

  const { valid: isIbanValid, errorCodes } = validateIBAN(iban);
  const { countryCode, bban } = extractIBAN(iban);
  const errors = getKnownErrorCodes(errorCodes).map(code => errorMessages.value[code]);

  return [

    {
      label: t('tools.iban-validator-and-parser.ui.isValid'),
      value: isIbanValid,
      showCopyButton: false,
    },
    {
      label: t('tools.iban-validator-and-parser.ui.errorsLabel'),
      value: errors.length === 0 ? undefined : errors,
      hideOnNil: true,
      showCopyButton: false,
    },
    {
      label: t('tools.iban-validator-and-parser.ui.isQrIban'),
      value: isQRIBAN(iban),
      showCopyButton: false,
    },
    {
      label: t('tools.iban-validator-and-parser.ui.countryCode'),
      value: countryCode,
    },
    {
      label: 'BBAN',
      value: bban,
    },
    {
      label: t('tools.iban-validator-and-parser.ui.friendlyFormat'),
      value: friendlyFormatIBAN(iban),
    },
  ];
});

const ibanExamples = [
  'FR7630006000011234567890189',
  'DE89370400440532013000',
  'GB29NWBK60161331926819',
];
</script>

<template>
  <div>
    <c-input-text v-model:value="rawIban" :placeholder="t('tools.iban-validator-and-parser.ui.inputPlaceholder')" test-id="iban-input" />

    <c-card v-if="ibanInfo.length > 0" mt-5>
      <c-key-value-list :items="ibanInfo" data-test-id="iban-info" />
    </c-card>

    <c-card :title="t('tools.iban-validator-and-parser.ui.examples')" mt-5>
      <div v-for="iban in ibanExamples" :key="iban">
        <c-text-copyable :value="iban" font-mono :displayed-value="friendlyFormatIBAN(iban)" />
      </div>
    </c-card>
  </div>
</template>
