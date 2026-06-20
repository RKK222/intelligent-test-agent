<script lang="ts">
import type { PermissionRequest, QuestionRequest } from "@test-agent/shared-types";

export type RuntimeDockProps = {
  permissions: PermissionRequest[];
  questions: QuestionRequest[];
};
</script>

<script setup lang="ts">
import { ref } from "vue";
import { Button, Input } from "@test-agent/ui-kit";

defineProps<RuntimeDockProps>();
const emit = defineEmits<{
  replyPermission: [requestId: string, decision: "once" | "always" | "reject"];
  replyQuestion: [requestId: string, answers: unknown[]];
  rejectQuestion: [requestId: string];
}>();

const answers = ref<Record<string, string>>({});
const multiAnswers = ref<Record<string, string[]>>({});
</script>

<template>
  <div v-if="permissions.length || questions.length" class="space-y-2">
    <div
      v-for="item in permissions"
      :key="item.requestId"
      class="rounded-md border border-[rgba(245,158,11,.3)] bg-[rgba(245,158,11,.08)] p-3"
    >
      <div class="text-[12px] font-semibold text-amber-100">{{ item.title ?? item.type }}</div>
      <div class="mt-1 whitespace-pre-wrap text-[12px] text-amber-200/80">{{ item.description ?? item.pattern ?? item.requestId }}</div>
      <div class="mt-2 flex flex-wrap gap-2">
        <Button type="button" size="sm" variant="secondary" @click="emit('replyPermission', item.requestId, 'once')">一次</Button>
        <Button type="button" size="sm" variant="secondary" @click="emit('replyPermission', item.requestId, 'always')">始终</Button>
        <Button type="button" size="sm" variant="secondary" @click="emit('replyPermission', item.requestId, 'reject')">拒绝</Button>
      </div>
    </div>

    <div
      v-for="item in questions"
      :key="item.requestId"
      class="rounded-md border border-[rgba(34,211,238,.4)] bg-[rgba(34,211,238,.08)] p-3"
    >
      <div v-for="question in item.questions" :key="question.questionId" class="space-y-2">
        <div class="text-[12px] font-semibold text-cyan-100">{{ question.text }}</div>
        <template v-if="question.kind === 'text'">
          <div class="flex gap-2">
            <Input v-model="answers[question.questionId]" placeholder="回答" />
            <Button
              type="button"
              size="sm"
              variant="primary"
              :disabled="!answers[question.questionId]?.trim()"
              @click="emit('replyQuestion', item.requestId, [answers[question.questionId]?.trim()])"
            >回复</Button>
            <Button type="button" size="sm" variant="secondary" @click="emit('rejectQuestion', item.requestId)">拒绝</Button>
          </div>
        </template>
        <template v-else-if="question.kind === 'multiple'">
          <div class="space-y-2">
            <div class="flex flex-wrap gap-2">
              <label
                v-for="option in (question.options?.length ? question.options : [{ id: 'confirm', label: '确认' }])"
                :key="option.id"
                class="flex items-center gap-1 rounded border border-slate-800 px-2 py-1 text-[12px] text-slate-200"
              >
                <input
                  type="checkbox"
                  :checked="multiAnswers[question.questionId]?.includes(option.id) ?? false"
                  @change="(e) => {
                    const checked = (e.target as HTMLInputElement).checked;
                    const current = multiAnswers[question.questionId] ?? [];
                    multiAnswers[question.questionId] = checked
                      ? [...current, option.id]
                      : current.filter((id) => id !== option.id);
                  }"
                />
                {{ option.label }}
              </label>
            </div>
            <div class="flex gap-2">
              <Button
                type="button"
                size="sm"
                variant="primary"
                :disabled="!multiAnswers[question.questionId]?.length"
                @click="emit('replyQuestion', item.requestId, multiAnswers[question.questionId] ?? [])"
              >回复</Button>
              <Button type="button" size="sm" variant="secondary" @click="emit('rejectQuestion', item.requestId)">拒绝</Button>
            </div>
          </div>
        </template>
        <template v-else>
          <div class="flex flex-wrap gap-2">
            <Button
              v-for="option in (question.options?.length ? question.options : [{ id: 'confirm', label: '确认' }])"
              :key="option.id"
              type="button"
              size="sm"
              variant="secondary"
              @click="emit('replyQuestion', item.requestId, [option.id])"
            >{{ option.label }}</Button>
            <Button type="button" size="sm" variant="secondary" @click="emit('rejectQuestion', item.requestId)">拒绝</Button>
          </div>
        </template>
      </div>
    </div>
  </div>
</template>
