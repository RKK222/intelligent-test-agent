<script setup lang="ts">
import type { SignatureInfo } from '../pdf-signature-checker.types';

const props = defineProps<{ signature: SignatureInfo }>();
const { signature } = toRefs(props);
const { t } = useI18n();

const tableHeaders = computed(() => ({
  validityPeriod: t('tools.pdf-signature-checker.ui.validityPeriod'),
  issuedBy: t('tools.pdf-signature-checker.ui.issuedBy'),
  issuedTo: t('tools.pdf-signature-checker.ui.issuedTo'),
  pemCertificate: t('tools.pdf-signature-checker.ui.pemCertificate'),
}));

function certificateOwnerItems(value: Record<string, string>) {
  return [
    { label: t('tools.pdf-signature-checker.ui.commonName'), value: value.commonName },
    { label: t('tools.pdf-signature-checker.ui.organizationName'), value: value.organizationName },
    { label: t('tools.pdf-signature-checker.ui.countryName'), value: value.countryName },
    { label: t('tools.pdf-signature-checker.ui.localityName'), value: value.localityName },
    { label: t('tools.pdf-signature-checker.ui.organizationalUnitName'), value: value.organizationalUnitName },
    { label: t('tools.pdf-signature-checker.ui.stateOrProvinceName'), value: value.stateOrProvinceName },
  ];
}

const certs = computed(() => signature.value.meta.certs.map((certificate, index) => ({
  ...certificate,
  validityPeriod: {
    notBefore: new Date(certificate.validityPeriod.notBefore).toLocaleString('zh-CN'),
    notAfter: new Date(certificate.validityPeriod.notAfter).toLocaleString('zh-CN'),
  },
  certificateName: t('tools.pdf-signature-checker.ui.certificate', { index: index + 1 }),
})),
);
</script>

<template>
  <div flex flex-col gap-2>
    <c-table :data="certs" :headers="tableHeaders">
      <template #validityPeriod="{ value }">
        <c-key-value-list
          :items="[{
            label: t('tools.pdf-signature-checker.ui.notBefore'),
            value: value.notBefore,
          }, {
            label: t('tools.pdf-signature-checker.ui.notAfter'),
            value: value.notAfter,
          }]"
        />
      </template>

      <template #issuedBy="{ value }">
        <c-key-value-list
          :items="certificateOwnerItems(value)"
        />
      </template>

      <template #issuedTo="{ value }">
        <c-key-value-list
          :items="certificateOwnerItems(value)"
        />
      </template>

      <template #pemCertificate="{ value }">
        <c-modal-value :value="value" :label="t('tools.pdf-signature-checker.ui.viewPem')">
          <template #value>
            <div break-all text-xs>
              {{ value }}
            </div>
          </template>
        </c-modal-value>
      </template>
    </c-table>
  </div>
</template>
