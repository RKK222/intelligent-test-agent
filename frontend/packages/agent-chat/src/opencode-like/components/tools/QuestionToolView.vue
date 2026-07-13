<script setup lang="ts">
import type { MessagePart } from "@test-agent/shared-types";
import { computed } from "vue";
import OcToolShell from "../primitives/OcToolShell.vue";

type QuestionOptionView = {
  label: string;
  description?: string;
};

type QuestionView = {
  header?: string;
  text: string;
  answers: QuestionOptionView[];
};

const props = defineProps<{
  part: Extract<MessagePart, { type: "tool" }>;
  nested?: boolean;
}>();

const questions = computed<QuestionView[]>(() => {
  const rawQuestions = Array.isArray(props.part.input?.questions) ? props.part.input.questions : [];
  const rawAnswers = Array.isArray(props.part.metadata?.answers) ? props.part.metadata.answers : [];

  return rawQuestions
    .filter(isRecord)
    .map((question, index) => {
      const options = Array.isArray(question.options) ? question.options.filter(isRecord) : [];
      const answers = Array.isArray(rawAnswers[index]) ? rawAnswers[index].filter(isString) : [];
      return {
        header: readableText(question.header),
        text: readableText(question.question) ?? readableText(question.text) ?? `问题 ${index + 1}`,
        // OpenCode 的回答只保存 label；精确匹配预置选项后补回 description，自定义答案则直接保留原文。
        answers: answers.map((answer) => {
          const option = options.find((candidate) => readableText(candidate.label) === answer);
          return {
            label: answer,
            description: option ? readableText(option.description) : undefined
          };
        })
      };
    });
});

const subtitle = computed(() => {
  if (questions.value.length > 1) return `${questions.value.length} 个问题`;
  return questions.value[0]?.text ?? "";
});

const statusText = computed(() => {
  const status = props.part.status?.toLowerCase();
  return status === "completed" || status === "success" ? "已回答" : undefined;
});

const emptyAnswerText = computed(() => {
  const status = props.part.status?.toLowerCase();
  return status === "running" || status === "pending" ? "等待回答" : "暂无回答详情";
});

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isString(value: unknown): value is string {
  return typeof value === "string" && value.trim().length > 0;
}

function readableText(value: unknown): string | undefined {
  return typeof value === "string" && value.trim().length > 0 ? value.trim() : undefined;
}
</script>

<template>
  <OcToolShell
    data-testid="oc-question-tool"
    title="提问"
    :subtitle="subtitle"
    :status="part.status"
    :status-text="statusText"
    :default-open="false"
    :nested="nested"
  >
    <div class="oc-question-detail">
      <article v-for="(question, index) in questions" :key="`${part.partId}:${index}`" class="oc-question-detail__item">
        <div v-if="question.header" class="oc-question-detail__header">{{ question.header }}</div>
        <div class="oc-question-detail__prompt">{{ question.text }}</div>
        <div v-if="question.answers.length" class="oc-question-detail__answers">
          <div v-for="(answer, answerIndex) in question.answers" :key="`${answer.label}:${answerIndex}`" class="oc-question-detail__answer">
            <div class="oc-question-detail__answer-label">{{ answer.label }}</div>
            <div v-if="answer.description" class="oc-question-detail__answer-description">{{ answer.description }}</div>
          </div>
        </div>
        <div v-else class="oc-question-detail__pending">{{ emptyAnswerText }}</div>
      </article>
    </div>
  </OcToolShell>
</template>
