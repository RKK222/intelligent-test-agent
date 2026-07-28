<script setup lang="ts">
import { UAParser } from 'ua-parser-js';
import { Adjustments, Browser, Cpu, Devices, Engine } from '@vicons/tabler';
import UserAgentResultCards from './user-agent-result-cards.vue';
import type { UserAgentResultSection } from './user-agent-parser.types';
import { withDefaultOnError } from '@/utils/defaults';

const ua = ref(navigator.userAgent as string);
const { t } = useI18n();

// If not input in the ua field is present return an empty object of type UAParser.IResult because otherwise
// UAParser returns the values for the current Browser. This is confusing because results are shown for an empty
// UA field value.
function getUserAgentInfo(userAgent: string) {
  return userAgent.trim().length > 0
    ? UAParser(userAgent.trim())
    : ({ ua: '', browser: {}, cpu: {}, device: {}, engine: {}, os: {} } as UAParser.IResult);
}
const userAgentInfo = computed(() => withDefaultOnError(() => getUserAgentInfo(ua.value), undefined));

const sections = computed<UserAgentResultSection[]>(() => [
  {
    heading: t('tools.user-agent-parser.ui.browser'),
    icon: Browser,
    content: [
      {
        label: t('tools.user-agent-parser.ui.name'),
        getValue: block => block?.browser.name,
        undefinedFallback: t('tools.user-agent-parser.ui.noBrowserName'),
      },
      {
        label: t('tools.user-agent-parser.ui.version'),
        getValue: block => block?.browser.version,
        undefinedFallback: t('tools.user-agent-parser.ui.noBrowserVersion'),
      },
    ],
  },
  {
    heading: t('tools.user-agent-parser.ui.engine'),
    icon: Engine,
    content: [
      {
        label: t('tools.user-agent-parser.ui.name'),
        getValue: block => block?.engine.name,
        undefinedFallback: t('tools.user-agent-parser.ui.noEngineName'),
      },
      {
        label: t('tools.user-agent-parser.ui.version'),
        getValue: block => block?.engine.version,
        undefinedFallback: t('tools.user-agent-parser.ui.noEngineVersion'),
      },
    ],
  },
  {
    heading: t('tools.user-agent-parser.ui.os'),
    icon: Adjustments,
    content: [
      {
        label: t('tools.user-agent-parser.ui.name'),
        getValue: block => block?.os.name,
        undefinedFallback: t('tools.user-agent-parser.ui.noOsName'),
      },
      {
        label: t('tools.user-agent-parser.ui.version'),
        getValue: block => block?.os.version,
        undefinedFallback: t('tools.user-agent-parser.ui.noOsVersion'),
      },
    ],
  },
  {
    heading: t('tools.user-agent-parser.ui.device'),
    icon: Devices,
    content: [
      {
        label: t('tools.user-agent-parser.ui.model'),
        getValue: block => block?.device.model,
        undefinedFallback: t('tools.user-agent-parser.ui.noDeviceModel'),
      },
      {
        label: t('tools.user-agent-parser.ui.type'),
        getValue: block => block?.device.type,
        undefinedFallback: t('tools.user-agent-parser.ui.noDeviceType'),
      },
      {
        label: t('tools.user-agent-parser.ui.vendor'),
        getValue: block => block?.device.vendor,
        undefinedFallback: t('tools.user-agent-parser.ui.noDeviceVendor'),
      },
    ],
  },
  {
    heading: t('tools.user-agent-parser.ui.cpu'),
    icon: Cpu,
    content: [
      {
        label: t('tools.user-agent-parser.ui.architecture'),
        getValue: block => block?.cpu.architecture,
        undefinedFallback: t('tools.user-agent-parser.ui.noCpuArchitecture'),
      },
    ],
  },
]);
</script>

<template>
  <div>
    <c-input-text
      v-model:value="ua"
      :label="t('tools.user-agent-parser.ui.inputLabel')"
      multiline
      :placeholder="t('tools.user-agent-parser.ui.inputPlaceholder')"
      clearable
      raw-text
      rows="2"
      autosize
      monospace
      mb-3
    />

    <UserAgentResultCards :user-agent-info="userAgentInfo" :sections="sections" />
  </div>
</template>
