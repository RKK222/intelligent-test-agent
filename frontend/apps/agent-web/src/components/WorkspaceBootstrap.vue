<script lang="ts">
export type WorkspaceBootstrapProps = { loading: boolean };
</script>

<script setup lang="ts">
import { ref } from "vue";
import { Button, Input } from "@test-agent/ui-kit";

const props = defineProps<WorkspaceBootstrapProps>();
const emit = defineEmits<{ create: [payload: { name: string; rootPath: string }] }>();

const name = ref("local-tests");
const rootPath = ref("");

function submit() {
  if (!name.value.trim() || !rootPath.value.trim()) return;
  emit("create", { name: name.value, rootPath: rootPath.value });
}
</script>

<template>
  <div class="flex h-full flex-col justify-center gap-3 p-4">
    <div>
      <div class="text-[13px] font-semibold text-slate-100">注册 Workspace</div>
      <div class="mt-1 text-[12px] text-slate-500">选择后端可访问的本地测试项目路径</div>
    </div>
    <Input v-model="name" placeholder="名称" />
    <Input v-model="rootPath" placeholder="/Users/huang/workspace/my-tests" />
    <Button variant="primary" :disabled="loading || !name.trim() || !rootPath.trim()" @click="submit">
      {{ loading ? "创建中" : "创建" }}
    </Button>
  </div>
</template>
