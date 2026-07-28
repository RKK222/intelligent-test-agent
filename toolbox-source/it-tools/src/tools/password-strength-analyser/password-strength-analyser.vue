<script setup lang="ts">
import { type CrackDuration, getPasswordCrackTimeEstimation } from './password-strength-analyser.service';

const password = ref('');
const { t } = useI18n();
const crackTimeEstimation = computed(() => getPasswordCrackTimeEstimation({ password: password.value }));
const durationUnitKeys: Record<string, { singular: string; plural: string }> = {
  MILLENNIUM: { singular: 'tools.password-strength-analyser.ui.duration.units.millennium.singular', plural: 'tools.password-strength-analyser.ui.duration.units.millennium.plural' },
  CENTURY: { singular: 'tools.password-strength-analyser.ui.duration.units.century.singular', plural: 'tools.password-strength-analyser.ui.duration.units.century.plural' },
  DECADE: { singular: 'tools.password-strength-analyser.ui.duration.units.decade.singular', plural: 'tools.password-strength-analyser.ui.duration.units.decade.plural' },
  YEAR: { singular: 'tools.password-strength-analyser.ui.duration.units.year.singular', plural: 'tools.password-strength-analyser.ui.duration.units.year.plural' },
  MONTH: { singular: 'tools.password-strength-analyser.ui.duration.units.month.singular', plural: 'tools.password-strength-analyser.ui.duration.units.month.plural' },
  WEEK: { singular: 'tools.password-strength-analyser.ui.duration.units.week.singular', plural: 'tools.password-strength-analyser.ui.duration.units.week.plural' },
  DAY: { singular: 'tools.password-strength-analyser.ui.duration.units.day.singular', plural: 'tools.password-strength-analyser.ui.duration.units.day.plural' },
  HOUR: { singular: 'tools.password-strength-analyser.ui.duration.units.hour.singular', plural: 'tools.password-strength-analyser.ui.duration.units.hour.plural' },
  MINUTE: { singular: 'tools.password-strength-analyser.ui.duration.units.minute.singular', plural: 'tools.password-strength-analyser.ui.duration.units.minute.plural' },
  SECOND: { singular: 'tools.password-strength-analyser.ui.duration.units.second.singular', plural: 'tools.password-strength-analyser.ui.duration.units.second.plural' },
};

function formatDuration(duration: CrackDuration) {
  if (duration.kind === 'INSTANT') {
    return t('tools.password-strength-analyser.ui.duration.instant');
  }
  if (duration.kind === 'LESS_THAN_SECOND') {
    return t('tools.password-strength-analyser.ui.duration.lessThanSecond');
  }
  return duration.parts.map(({ code, formattedQuantity, quantity }) => {
    const keys = durationUnitKeys[code];
    return `${formattedQuantity} ${t(quantity > 1 ? keys.plural : keys.singular)}`;
  }).join(t('tools.password-strength-analyser.ui.duration.separator'));
}

const crackDurationFormatted = computed(() => formatDuration(crackTimeEstimation.value.crackDuration));

const details = computed(() => [
  {
    label: t('tools.password-strength-analyser.ui.passwordLength'),
    value: crackTimeEstimation.value.passwordLength,
  },
  {
    label: t('tools.password-strength-analyser.ui.entropy'),
    value: Math.round(crackTimeEstimation.value.entropy * 100) / 100,
  },
  {
    label: t('tools.password-strength-analyser.ui.charsetSize'),
    value: crackTimeEstimation.value.charsetLength,
  },
  {
    label: t('tools.password-strength-analyser.ui.score'),
    value: `${Math.round(crackTimeEstimation.value.score * 100)} / 100`,
  },
]);
</script>

<template>
  <div flex flex-col gap-3>
    <c-input-text
      v-model:value="password"
      type="password"
      :placeholder="t('tools.password-strength-analyser.ui.passwordPlaceholder')"
      clearable
      autofocus
      raw-text
      test-id="password-input"
    />

    <c-card text-center>
      <div op-60>
        {{ t('tools.password-strength-analyser.ui.crackDuration') }}
      </div>
      <div text-2xl data-test-id="crack-duration">
        {{ crackDurationFormatted }}
      </div>
    </c-card>
    <c-card>
      <div v-for="({ label, value }) of details" :key="label" flex gap-3>
        <div flex-1 text-right op-60>
          {{ label }}
        </div>
        <div flex-1 text-left>
          {{ value }}
        </div>
      </div>
    </c-card>
    <div op-70>
      <span font-bold>{{ t('tools.password-strength-analyser.ui.noteLabel') }}</span>
      {{ t('tools.password-strength-analyser.ui.note') }}
    </div>
  </div>
</template>
