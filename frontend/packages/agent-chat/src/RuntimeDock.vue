<script lang="ts">
import type { PermissionRequest, QuestionRequest } from "@test-agent/shared-types";

export type RuntimeDockProps = {
  permissions: PermissionRequest[];
  questions: QuestionRequest[];
};
</script>

<script setup lang="ts">
import { computed, ref } from "vue";
import { AlertTriangle } from "lucide-vue-next";
import { Button, Input } from "@test-agent/ui-kit";
import { permissionPresentation } from "./permission-presentation";

const props = defineProps<RuntimeDockProps>();
const emit = defineEmits<{
  replyPermission: [requestId: string, decision: "once" | "always" | "reject"];
  replyQuestion: [requestId: string, answers: unknown[]];
  rejectQuestion: [requestId: string];
}>();

// 单选/文本题：按 questionId 记录选中的 option id 或输入文本。
const answers = ref<Record<string, string>>({});
// 多选题：按 questionId 记录选中的 option id 列表。
const multiAnswers = ref<Record<string, string[]>>({});
const permissionCards = computed(() => props.permissions.map((request) => ({
  request,
  presentation: permissionPresentation(request)
})));

// opencode 要求一次回复覆盖同一请求下的全部子问题，answers 为 List<List<String>>：
// 外层按子问题顺序排列，内层是该问题的选中 label。这里把同一请求所有子问题的答案按序组装。
function buildItemAnswers(item: QuestionRequest): unknown[][] {
  return item.questions.map((question) => {
    if (question.kind === "multiple") {
      return multiAnswers.value[question.questionId] ?? [];
    }
    const value = answers.value[question.questionId]?.trim();
    return value ? [value] : [];
  });
}

// 同一请求下所有必答子问题都已作答时才允许提交。
function canReplyItem(item: QuestionRequest): boolean {
  return item.questions.every((question) => {
    if (question.kind === "multiple") {
      return (multiAnswers.value[question.questionId]?.length ?? 0) > 0;
    }
    return Boolean(answers.value[question.questionId]?.trim());
  });
}
</script>

<template>
  <div v-if="permissions.length || questions.length" class="space-y-2">
    <div
      v-for="item in permissionCards"
      :key="item.request.requestId"
      class="rounded-md border border-[rgba(245,158,11,.3)] bg-[rgba(245,158,11,.08)] p-3"
    >
      <div class="flex items-center gap-2 text-[12px] font-semibold text-amber-100">
        <AlertTriangle :size="15" aria-hidden="true" />
        <span>{{ item.presentation.title }}</span>
      </div>
      <div v-if="item.presentation.description" class="mt-1 whitespace-pre-wrap text-[12px] text-amber-200/80">
        {{ item.presentation.description }}
      </div>
      <div v-if="item.presentation.patterns.length" class="mt-2 grid gap-1">
        <code
          v-for="pattern in item.presentation.patterns"
          :key="pattern"
          class="break-all rounded bg-black/15 px-2 py-1 text-[12px] text-amber-100"
        >{{ pattern }}</code>
      </div>
      <div class="mt-2 flex flex-wrap gap-2">
        <Button type="button" size="sm" variant="secondary" @click="emit('replyPermission', item.request.requestId, 'reject')">拒绝</Button>
        <Button type="button" size="sm" variant="secondary" @click="emit('replyPermission', item.request.requestId, 'always')">始终允许</Button>
        <Button type="button" size="sm" variant="primary" @click="emit('replyPermission', item.request.requestId, 'once')">允许一次</Button>
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
          <Input v-model="answers[question.questionId]" placeholder="回答" />
        </template>
        <template v-else-if="question.kind === 'multiple'">
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
        </template>
        <template v-else>
          <div class="flex flex-wrap gap-2">
            <Button
              v-for="option in (question.options?.length ? question.options : [{ id: 'confirm', label: '确认' }])"
              :key="option.id"
              type="button"
              size="sm"
              :variant="answers[question.questionId] === option.id ? 'primary' : 'secondary'"
              @click="answers[question.questionId] = option.id"
            >{{ option.label }}</Button>
          </div>
        </template>
      </div>
      <div class="mt-2 flex gap-2">
        <Button
          type="button"
          size="sm"
          variant="primary"
          :disabled="!canReplyItem(item)"
          @click="emit('replyQuestion', item.requestId, buildItemAnswers(item))"
        >回复</Button>
        <Button type="button" size="sm" variant="secondary" @click="emit('rejectQuestion', item.requestId)">拒绝</Button>
      </div>
    </div>
  </div>
</template>
