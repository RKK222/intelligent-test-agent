<script setup lang="ts">
import cronstrue from 'cronstrue';
import 'cronstrue/locales/zh_CN';
import { isValidCron } from 'cron-validator';
import { useStyleStore } from '@/stores/style.store';

function isCronValid(v: string) {
  return isValidCron(v, { allowBlankDay: true, alias: true, seconds: true });
}

const styleStore = useStyleStore();
const { t } = useI18n();

const cron = ref('40 * * * *');
const cronstrueConfig = reactive({
  verbose: true,
  dayOfWeekStartIndexZero: true,
  use24HourTimeFormat: true,
  throwExceptionOnParseError: true,
  locale: 'zh_CN',
});

const helpers = computed(() => [
  {
    symbol: '*',
    meaning: t('tools.crontab-generator.ui.helpers.anyValue'),
    example: '* * * *',
    equivalent: t('tools.crontab-generator.ui.helpers.everyMinute'),
  },
  {
    symbol: '-',
    meaning: t('tools.crontab-generator.ui.helpers.range'),
    example: '1-10 * * *',
    equivalent: t('tools.crontab-generator.ui.helpers.minutesRange'),
  },
  {
    symbol: ',',
    meaning: t('tools.crontab-generator.ui.helpers.list'),
    example: '1,10 * * *',
    equivalent: t('tools.crontab-generator.ui.helpers.minutesList'),
  },
  {
    symbol: '/',
    meaning: t('tools.crontab-generator.ui.helpers.step'),
    example: '*/10 * * *',
    equivalent: t('tools.crontab-generator.ui.helpers.everyTenMinutes'),
  },
  {
    symbol: '@yearly',
    meaning: t('tools.crontab-generator.ui.helpers.yearly'),
    example: '@yearly',
    equivalent: '0 0 1 1 *',
  },
  {
    symbol: '@annually',
    meaning: t('tools.crontab-generator.ui.helpers.sameAsYearly'),
    example: '@annually',
    equivalent: '0 0 1 1 *',
  },
  {
    symbol: '@monthly',
    meaning: t('tools.crontab-generator.ui.helpers.monthly'),
    example: '@monthly',
    equivalent: '0 0 1 * *',
  },
  {
    symbol: '@weekly',
    meaning: t('tools.crontab-generator.ui.helpers.weekly'),
    example: '@weekly',
    equivalent: '0 0 * * 0',
  },
  {
    symbol: '@daily',
    meaning: t('tools.crontab-generator.ui.helpers.daily'),
    example: '@daily',
    equivalent: '0 0 * * *',
  },
  {
    symbol: '@midnight',
    meaning: t('tools.crontab-generator.ui.helpers.sameAsDaily'),
    example: '@midnight',
    equivalent: '0 0 * * *',
  },
  {
    symbol: '@hourly',
    meaning: t('tools.crontab-generator.ui.helpers.hourly'),
    example: '@hourly',
    equivalent: '0 * * * *',
  },
  {
    symbol: '@reboot',
    meaning: t('tools.crontab-generator.ui.helpers.reboot'),
    example: '',
    equivalent: '',
  },
]);
const helperHeaders = computed(() => ({
  symbol: t('tools.crontab-generator.ui.symbol'),
  meaning: t('tools.crontab-generator.ui.meaning'),
  example: t('tools.crontab-generator.ui.example'),
  equivalent: t('tools.crontab-generator.ui.equivalent'),
}));

const cronString = computed(() => {
  if (isCronValid(cron.value)) {
    return cronstrue.toString(cron.value, cronstrueConfig);
  }
  return ' ';
});

const cronValidationRules = computed(() => [
  {
    validator: (value: string) => isCronValid(value),
    message: t('tools.crontab-generator.ui.invalidCron'),
  },
]);
</script>

<template>
  <c-card>
    <div mx-auto max-w-sm>
      <c-input-text
        v-model:value="cron"
        size="large"
        placeholder="* * * * *"
        :validation-rules="cronValidationRules"
        mb-3
      />
    </div>

    <div class="cron-string">
      {{ cronString }}
    </div>

    <n-divider />

    <div flex justify-center>
      <n-form :show-feedback="false" label-width="170" label-placement="left">
        <n-form-item :label="t('tools.crontab-generator.ui.verbose')">
          <n-switch v-model:value="cronstrueConfig.verbose" />
        </n-form-item>
        <n-form-item :label="t('tools.crontab-generator.ui.use24Hour')">
          <n-switch v-model:value="cronstrueConfig.use24HourTimeFormat" />
        </n-form-item>
        <n-form-item :label="t('tools.crontab-generator.ui.daysStartAtZero')">
          <n-switch v-model:value="cronstrueConfig.dayOfWeekStartIndexZero" />
        </n-form-item>
      </n-form>
    </div>
  </c-card>
  <c-card>
    <pre>
┌──────────── {{ t('tools.crontab-generator.ui.diagram.optionalSeconds') }} (0 - 59)
| ┌────────── {{ t('tools.crontab-generator.ui.diagram.minute') }} (0 - 59)
| | ┌──────── {{ t('tools.crontab-generator.ui.diagram.hour') }} (0 - 23)
| | | ┌────── {{ t('tools.crontab-generator.ui.diagram.dayOfMonth') }} (1 - 31)
| | | | ┌──── {{ t('tools.crontab-generator.ui.diagram.month') }} (1 - 12) {{ t('tools.crontab-generator.ui.diagram.or') }} jan,feb,mar,apr ...
| | | | | ┌── {{ t('tools.crontab-generator.ui.diagram.dayOfWeek') }} (0 - 6, {{ t('tools.crontab-generator.ui.diagram.sundayZero') }}) {{ t('tools.crontab-generator.ui.diagram.or') }} sun,mon ...
| | | | | |
* * * * * * {{ t('tools.crontab-generator.ui.diagram.command') }}</pre>

    <div v-if="styleStore.isSmallScreen">
      <c-card v-for="{ symbol, meaning, example, equivalent } in helpers" :key="symbol" mb-3 important:border-none>
        <div>
          {{ t('tools.crontab-generator.ui.symbol') }}：<strong>{{ symbol }}</strong>
        </div>
        <div>
          {{ t('tools.crontab-generator.ui.meaning') }}：<strong>{{ meaning }}</strong>
        </div>
        <div>
          {{ t('tools.crontab-generator.ui.example') }}：
          <strong><code>{{ example }}</code></strong>
        </div>
        <div>
          {{ t('tools.crontab-generator.ui.equivalent') }}：<strong>{{ equivalent }}</strong>
        </div>
      </c-card>
    </div>

    <c-table v-else :data="helpers" :headers="helperHeaders" />
  </c-card>
</template>

<style lang="less" scoped>
::v-deep(input) {
  font-size: 30px;
  font-family: monospace;
  padding: 5px;
  text-align: center;
}

.cron-string {
  text-align: center;
  font-size: 22px;
  opacity: 0.8;
  margin: 5px 0 15px;
}

pre {
  overflow: auto;
  padding: 10px 0;
}
</style>
