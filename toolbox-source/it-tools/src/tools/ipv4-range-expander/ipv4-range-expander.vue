<script setup lang="ts">
import { Exchange } from '@vicons/tabler';
import { isValidIpv4 } from '../ipv4-address-converter/ipv4-address-converter.service';
import type { Ipv4RangeExpanderResult } from './ipv4-range-expander.types';
import { calculateCidr } from './ipv4-range-expander.service';
import ResultRow from './result-row.vue';
import { useValidation } from '@/composable/validation';

const rawStartAddress = useStorage('ipv4-range-expander:startAddress', '192.168.1.1');
const rawEndAddress = useStorage('ipv4-range-expander:endAddress', '192.168.6.255');
const { t } = useI18n();

const result = computed(() => calculateCidr({ startIp: rawStartAddress.value, endIp: rawEndAddress.value }));

interface CalculatedValue {
  label: string
  testId: string
  getOldValue: (result: Ipv4RangeExpanderResult | undefined) => string | undefined
  getNewValue: (result: Ipv4RangeExpanderResult | undefined) => string | undefined
}

const calculatedValues = computed<CalculatedValue[]>(() => [
  {
    label: t('tools.ipv4-range-expander.ui.startAddress'),
    testId: 'start-address',
    getOldValue: () => rawStartAddress.value,
    getNewValue: result => result?.newStart,
  },
  {
    label: t('tools.ipv4-range-expander.ui.endAddress'),
    testId: 'end-address',
    getOldValue: () => rawEndAddress.value,
    getNewValue: result => result?.newEnd,
  },
  {
    label: t('tools.ipv4-range-expander.ui.addressesInRange'),
    testId: 'addresses-in-range',
    getOldValue: result => result?.oldSize?.toLocaleString('zh-CN'),
    getNewValue: result => result?.newSize?.toLocaleString('zh-CN'),
  },
  {
    label: 'CIDR',
    testId: 'cidr',
    getOldValue: () => '',
    getNewValue: result => result?.newCidr,
  },
]);

const startIpValidation = useValidation<string>({
  source: rawStartAddress,
  rules: computed(() => [{ message: t('tools.ipv4-range-expander.ui.invalidIpv4'), validator: ip => isValidIpv4({ ip }) }]),
});
const endIpValidation = useValidation<string>({
  source: rawEndAddress,
  rules: computed(() => [{ message: t('tools.ipv4-range-expander.ui.invalidIpv4'), validator: ip => isValidIpv4({ ip }) }]),
});

const showResult = computed(() => endIpValidation.isValid && startIpValidation.isValid && result.value !== undefined);

function onSwitchStartEndClicked() {
  const tmpStart = rawStartAddress.value;
  rawStartAddress.value = rawEndAddress.value;
  rawEndAddress.value = tmpStart;
}
</script>

<template>
  <div>
    <div mb-4 flex gap-4>
      <c-input-text
        v-model:value="rawStartAddress"
        :label="t('tools.ipv4-range-expander.ui.startAddress')"
        :placeholder="t('tools.ipv4-range-expander.ui.startPlaceholder')"
        :validation="startIpValidation"
        clearable
      />

      <c-input-text
        v-model:value="rawEndAddress"
        :label="t('tools.ipv4-range-expander.ui.endAddress')"
        :placeholder="t('tools.ipv4-range-expander.ui.endPlaceholder')"
        :validation="endIpValidation"
        clearable
      />
    </div>

    <n-table v-if="showResult" data-test-id="result">
      <thead>
        <tr>
          <th scope="col">
&nbsp;
          </th>
          <th scope="col">
            {{ t('tools.ipv4-range-expander.ui.oldValue') }}
          </th>
          <th scope="col">
            {{ t('tools.ipv4-range-expander.ui.newValue') }}
          </th>
        </tr>
      </thead>
      <tbody>
        <ResultRow
          v-for="{ label, testId, getOldValue, getNewValue } in calculatedValues"
          :key="testId"
          :label="label"
          :test-id="testId"
          :old-value="getOldValue(result)"
          :new-value="getNewValue(result)"
        />
      </tbody>
    </n-table>
    <n-alert
      v-else-if="startIpValidation.isValid && endIpValidation.isValid"
      :title="t('tools.ipv4-range-expander.ui.invalidRangeTitle')"
      type="error"
    >
      <div my-3 op-70>
        {{ t('tools.ipv4-range-expander.ui.invalidRangeDescription') }}
      </div>

      <c-button @click="onSwitchStartEndClicked">
        <n-icon mr-2 :component="Exchange" depth="3" size="22" />
        {{ t('tools.ipv4-range-expander.ui.switchAddresses') }}
      </c-button>
    </n-alert>
  </div>
</template>
