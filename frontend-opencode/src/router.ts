import type { RouteRecordRaw } from "vue-router";
import HomeView from "@/views/HomeView.vue";
import NewSessionView from "@/views/NewSessionView.vue";
import SessionView from "@/views/SessionView.vue";

export const routes: RouteRecordRaw[] = [
  { path: "/", name: "home", component: HomeView },
  { path: "/w/:workspaceId/session/:sessionId?", name: "session", component: SessionView, props: true },
  { path: "/new-session", name: "new-session", component: NewSessionView }
];
