<script setup lang="ts">
import { computed, ref, watch } from "vue";
import { CheckCircle2, ChevronDown, Circle, HelpCircle, RotateCcw, SendHorizontal, ShieldAlert } from "lucide-vue-next";
import type { QuestionRequest } from "@test-agent/shared-types";
import { useSessionStore } from "@/stores/session";

const session = useSessionStore();
const todoOpen = ref(true);
const followupOpen = ref(true);
const revertOpen = ref(true);
const answers = ref<Record<string, string[]>>({});

const activePermission = computed(() => session.permissions[0]);
const activeQuestion = computed(() => session.questions[0]);
const doneTodos = computed(() => session.todos.filter((todo) => todo.status === "completed").length);

// 复刻 opencode composer 上方的待处理 dock，统一承载权限、问题和队列操作。
watch(
  activeQuestion,
  (request) => {
    if (!request) {
      return;
    }
    for (const question of request.questions) {
      answers.value[question.questionId] ??= [];
    }
  },
  { immediate: true }
);

function toggleAnswer(request: QuestionRequest, questionId: string, label: string, multi: boolean) {
  const current = answers.value[questionId] ?? [];
  if (multi) {
    answers.value[questionId] = current.includes(label) ? current.filter((item) => item !== label) : [...current, label];
  } else {
    answers.value[questionId] = [label];
  }
  for (const question of request.questions) {
    answers.value[question.questionId] ??= [];
  }
}

function submitQuestion(request: QuestionRequest) {
  void session.replyQuestion(
    request.requestId,
    request.questions.map((question) => answers.value[question.questionId] ?? [])
  );
}
</script>

<template>
  <section
    v-if="activePermission || activeQuestion || session.todos.length || session.followups.length || session.revertItems.length"
    class="session-dock-stack"
    aria-label="Session requests"
    role="region"
  >
    <article v-if="activePermission" class="session-dock permission-dock">
      <header class="dock-header">
        <span class="dock-icon warning"><ShieldAlert :size="16" /></span>
        <div>
          <strong>{{ activePermission.title ?? "Permission required" }}</strong>
          <small>{{ activePermission.description ?? activePermission.type }}</small>
        </div>
      </header>
      <code v-if="activePermission.pattern" class="dock-code">{{ activePermission.pattern }}</code>
      <footer class="dock-actions">
        <button type="button" class="icon-text" @click="session.replyPermission(activePermission.requestId, 'reject')">Deny</button>
        <button type="button" class="icon-text" @click="session.replyPermission(activePermission.requestId, 'always')">Allow always</button>
        <button type="button" class="primary-action" @click="session.replyPermission(activePermission.requestId, 'once')">Allow once</button>
      </footer>
    </article>

    <article v-if="activeQuestion" class="session-dock question-dock">
      <header class="dock-header">
        <span class="dock-icon"><HelpCircle :size="16" /></span>
        <div>
          <strong>Question</strong>
          <small>{{ activeQuestion.questions.length }} prompt{{ activeQuestion.questions.length === 1 ? "" : "s" }}</small>
        </div>
      </header>
      <div v-for="question in activeQuestion.questions" :key="question.questionId" class="question-block">
        <p>{{ question.text }}</p>
        <div class="question-options">
          <button
            v-for="option in question.options ?? []"
            :key="option.id"
            type="button"
            class="question-option"
            :aria-checked="(answers[question.questionId] ?? []).includes(option.label)"
            :role="question.kind === 'multiple' ? 'checkbox' : 'radio'"
            @click="toggleAnswer(activeQuestion, question.questionId, option.label, question.kind === 'multiple')"
          >
            <span class="option-mark">
              <CheckCircle2 v-if="(answers[question.questionId] ?? []).includes(option.label)" :size="14" />
              <Circle v-else :size="14" />
            </span>
            <span>
              <strong>{{ option.label }}</strong>
              <small v-if="option.description">{{ option.description }}</small>
            </span>
          </button>
        </div>
      </div>
      <footer class="dock-actions">
        <button type="button" class="icon-text" @click="session.rejectQuestion(activeQuestion.requestId)">Reject</button>
        <button type="button" class="primary-action" @click="submitQuestion(activeQuestion)">Submit answer</button>
      </footer>
    </article>

    <article v-if="session.todos.length" class="session-dock todo-dock">
      <button type="button" class="dock-toggle" @click="todoOpen = !todoOpen">
        <span>{{ doneTodos }} / {{ session.todos.length }} todos</span>
        <ChevronDown :size="15" :class="{ flipped: todoOpen }" />
      </button>
      <ul v-if="todoOpen" class="dock-list">
        <li v-for="todo in session.todos" :key="todo.id" :data-state="todo.status">
          <span class="state-dot" />
          <span>{{ todo.text }}</span>
          <small>{{ todo.status }}</small>
        </li>
      </ul>
    </article>

    <article v-if="session.followups.length" class="session-dock followup-dock">
      <button type="button" class="dock-toggle" @click="followupOpen = !followupOpen">
        <span>{{ session.followups.length }} queued follow-up{{ session.followups.length === 1 ? "" : "s" }}</span>
        <ChevronDown :size="15" :class="{ flipped: followupOpen }" />
      </button>
      <div v-if="followupOpen" class="queued-list">
        <div v-for="item in session.followups" :key="item.id" class="queued-row">
          <span>{{ item.text }}</span>
          <button type="button" class="icon-text" @click="session.editFollowup(item.id)">Edit</button>
          <button type="button" class="primary-action" @click="session.sendFollowup(item.id)">
            <SendHorizontal :size="14" />Send now
          </button>
        </div>
      </div>
    </article>

    <article v-if="session.revertItems.length" class="session-dock revert-dock">
      <button type="button" class="dock-toggle" @click="revertOpen = !revertOpen">
        <span>{{ session.revertItems.length }} reverted message{{ session.revertItems.length === 1 ? "" : "s" }}</span>
        <ChevronDown :size="15" :class="{ flipped: revertOpen }" />
      </button>
      <div v-if="revertOpen" class="queued-list">
        <div v-for="item in session.revertItems" :key="item.id" class="queued-row">
          <span><RotateCcw :size="14" />{{ item.text }}</span>
          <button type="button" class="primary-action" @click="session.restoreRevert(item.id)">Restore</button>
        </div>
      </div>
    </article>
  </section>
</template>
