<script setup lang="ts">
import { Netmask } from 'netmask';
import { useStorage } from '@vueuse/core';
import { ArrowLeft, ArrowRight } from '@vicons/tabler';
import { getIPClass } from './ipv4-subnet-calculator.models';
import { withDefaultOnError } from '@/utils/defaults';
import { isNotThrowing } from '@/utils/boolean';
import SpanCopyable from '@/components/SpanCopyable.vue';

const ip = useStorage('ipv4-subnet-calculator:ip', '192.168.0.1/24');
const { t } = useI18n();

const getNetworkInfo = (address: string) => new Netmask(address.trim());

const networkInfo = computed(() => withDefaultOnError(() => getNetworkInfo(ip.value), undefined));

const ipValidationRules = computed(() => [
  {
    message: t('tools.ipv4-subnet-calculator.ui.invalidAddress'),
    validator: (value: string) => isNotThrowing(() => getNetworkInfo(value.trim())),
  },
]);

interface NetworkInfoSection {
  label: string
  getValue: (blocks: Netmask) => string | undefined
  undefinedFallback?: string
}

const sections = computed<NetworkInfoSection[]>(() => [
  {
    label: t('tools.ipv4-subnet-calculator.ui.netmask'),
    getValue: block => block.toString(),
  },
  {
    label: t('tools.ipv4-subnet-calculator.ui.networkAddress'),
    getValue: ({ base }) => base,
  },
  {
    label: t('tools.ipv4-subnet-calculator.ui.networkMask'),
    getValue: ({ mask }) => mask,
  },
  {
    label: t('tools.ipv4-subnet-calculator.ui.binaryMask'),
    getValue: ({ bitmask }) => ('1'.repeat(bitmask) + '0'.repeat(32 - bitmask)).match(/.{8}/g)?.join('.') ?? '',
  },
  {
    label: t('tools.ipv4-subnet-calculator.ui.cidrNotation'),
    getValue: ({ bitmask }) => `/${bitmask}`,
  },
  {
    label: t('tools.ipv4-subnet-calculator.ui.wildcardMask'),
    getValue: ({ hostmask }) => hostmask,
  },
  {
    label: t('tools.ipv4-subnet-calculator.ui.networkSize'),
    getValue: ({ size }) => String(size),
  },
  {
    label: t('tools.ipv4-subnet-calculator.ui.firstAddress'),
    getValue: ({ first }) => first,
  },
  {
    label: t('tools.ipv4-subnet-calculator.ui.lastAddress'),
    getValue: ({ last }) => last,
  },
  {
    label: t('tools.ipv4-subnet-calculator.ui.broadcastAddress'),
    getValue: ({ broadcast }) => broadcast,
    undefinedFallback: t('tools.ipv4-subnet-calculator.ui.noBroadcast'),
  },
  {
    label: t('tools.ipv4-subnet-calculator.ui.ipClass'),
    getValue: ({ base: ip }) => getIPClass({ ip }),
    undefinedFallback: t('tools.ipv4-subnet-calculator.ui.unknownClass'),
  },
]);

function switchToBlock({ count = 1 }: { count?: number }) {
  const next = networkInfo.value?.next(count);

  if (next) {
    ip.value = next.toString();
  }
}
</script>

<template>
  <div>
    <c-input-text
      v-model:value="ip"
      :label="t('tools.ipv4-subnet-calculator.ui.inputLabel')"
      :placeholder="t('tools.ipv4-subnet-calculator.ui.inputPlaceholder')"
      :validation-rules="ipValidationRules"
      mb-4
    />

    <div v-if="networkInfo">
      <n-table>
        <tbody>
          <tr v-for="{ getValue, label, undefinedFallback } in sections" :key="label">
            <td font-bold>
              {{ label }}
            </td>
            <td>
              <SpanCopyable v-if="getValue(networkInfo)" :value="getValue(networkInfo)" />
              <span v-else op-70>
                {{ undefinedFallback }}
              </span>
            </td>
          </tr>
        </tbody>
      </n-table>

      <div mt-3 flex items-center justify-between>
        <c-button @click="switchToBlock({ count: -1 })">
          <n-icon :component="ArrowLeft" />
          {{ t('tools.ipv4-subnet-calculator.ui.previousBlock') }}
        </c-button>
        <c-button @click="switchToBlock({ count: 1 })">
          {{ t('tools.ipv4-subnet-calculator.ui.nextBlock') }}
          <n-icon :component="ArrowRight" />
        </c-button>
      </div>
    </div>
  </div>
</template>
