<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch, type CSSProperties } from "vue";
import { ChevronDown, LogOut, ShieldCheck, UserRound, X } from "lucide-vue-next";
import type { UserOpencodeProcess } from "@test-agent/shared-types";
import logoUrl from "../assets/figma/logo.svg";
import panelCloseUrl from "../assets/figma/panel-close.svg";

export type AppItem = {
  id: string;
  name: string;
  description?: string;
  icon?: string;
};

export type RuntimeInventoryItem = {
  id: string;
  name: string;
  description?: string;
  status?: string;
};

export type RuntimeInventorySummary = {
  agents: RuntimeInventoryItem[];
  skills: RuntimeInventoryItem[];
  mcp: RuntimeInventoryItem[];
  plugins: RuntimeInventoryItem[];
  mcpTools?: RuntimeInventoryItem[];
  mcpResources?: RuntimeInventoryItem[];
};

const props = withDefaults(
  defineProps<{
    workspaceName?: string;
    bottomOpen?: boolean;
    apps?: AppItem[];
    joinableApps?: { appId: string; appName: string }[];
    selectedAppId?: string;
    currentUserName?: string;
    currentUserRoleLabels?: string[];
    opencodeProcessStatus?: UserOpencodeProcess | null;
    opencodeProcessLoading?: boolean;
    showLeftPanel?: boolean;
    showRightPanel?: boolean;
    runtimeInventory?: RuntimeInventorySummary;
  }>(),
  {
    apps: () => [
      { id: "fgcms-psn", name: "F-GCMS-PSN", description: "色谱质谱联用平台" },
      { id: "lcs-test", name: "LCS-Test", description: "液相色谱测试套件" },
      { id: "gcms-2024", name: "GCMS-2024", description: "气相色谱质谱年度测试" },
      { id: "ms-runner", name: "MS-Runner", description: "质谱批量回归任务" }
    ],
    joinableApps: () => [],
    selectedAppId: "fgcms-psn",
    showLeftPanel: true,
    showRightPanel: true
  }
);

const leftPanelOpen = ref(props.showLeftPanel);
watch(() => props.showLeftPanel, (newVal) => {
  leftPanelOpen.value = newVal;
});
const leftPanelWidth = ref(262);
const leftPanelStyle = computed<CSSProperties>(() => ({
  width: leftPanelOpen.value ? `${leftPanelWidth.value}px` : "0px",
  opacity: leftPanelOpen.value ? 1 : 0,
  pointerEvents: leftPanelOpen.value ? ("auto" as const) : ("none" as const)
}));
const rightPanelWidth = ref(450);
const rightPanelStyle = computed<CSSProperties>(() => ({
  width: props.showRightPanel ? `${rightPanelWidth.value}px` : "0px",
  opacity: props.showRightPanel ? 1 : 0,
  pointerEvents: props.showRightPanel ? ("auto" as const) : ("none" as const)
}));
const resizing = ref<"left" | "right" | null>(null);
let resizeStartX = 0;
let resizeStartWidth = 0;

const MIN_LEFT_WIDTH = 200;
const MAX_LEFT_WIDTH = 600;
const MIN_RIGHT_WIDTH = 240;
const MAX_RIGHT_WIDTH = 1200;

const emit = defineEmits<{
  (e: "toggle-left-panel"): void;
  (e: "toggle-right-panel"): void;
  (e: "select-app", appId: string): void;
  (e: "logout"): void;
  (e: "refresh-opencode-process"): void;
  (e: "join-app", appId: string, callback: (success: boolean) => void): void;
}>();

const appMenuOpen = ref(false);
const userMenuOpen = ref(false);
const runtimeInventoryOpen = ref(false);

function toggleLeftPanel() {
  leftPanelOpen.value = !leftPanelOpen.value;
  emit("toggle-left-panel");
}

function toggleRightPanel() {
  emit("toggle-right-panel");
}

function toggleAppMenu() {
  appMenuOpen.value = !appMenuOpen.value;
  userMenuOpen.value = false;
  runtimeInventoryOpen.value = false;
}

function closeAppMenu() {
  appMenuOpen.value = false;
}

function toggleUserMenu() {
  const nextOpen = !userMenuOpen.value;
  userMenuOpen.value = nextOpen;
  appMenuOpen.value = false;
  runtimeInventoryOpen.value = false;
  if (nextOpen) {
    emit("refresh-opencode-process");
  }
}

function closeUserMenu() {
  userMenuOpen.value = false;
}

function closeHeaderMenus() {
  closeAppMenu();
  closeUserMenu();
  closeRuntimeInventory();
}

function logout() {
  closeUserMenu();
  emit("logout");
}

function selectApp(app: AppItem) {
  emit("select-app", app.id);
  closeAppMenu();
}

const selectedApp = computed(
  () => props.apps.find((a) => a.id === props.selectedAppId) ?? props.apps[0] ?? { id: "", name: "未选择应用" }
);
const runtimeInventory = computed<RuntimeInventorySummary>(() => props.runtimeInventory ?? {
  agents: [],
  skills: [],
  mcp: [],
  plugins: [],
  mcpTools: [],
  mcpResources: []
});
const runtimeInventoryCounts = computed(() => ({
  agents: runtimeInventory.value.agents.length,
  skills: runtimeInventory.value.skills.length,
  mcp: runtimeInventory.value.mcp.length,
  plugins: runtimeInventory.value.plugins.length
}));

function toggleRuntimeInventory(event: MouseEvent) {
  event.stopPropagation();
  runtimeInventoryOpen.value = !runtimeInventoryOpen.value;
  appMenuOpen.value = false;
  userMenuOpen.value = false;
}

function closeRuntimeInventory() {
  runtimeInventoryOpen.value = false;
}
const userName = computed(() => props.currentUserName?.trim() || "未登录");
// 右上角用户菜单顶部的「角色」灰显行：来自后端 /api/auth/me 的 roleLabels（dictionaries.dict_label）。
// 多个角色用「、」拼接；roleLabels 为空或缺失时整行不渲染，避免在未登录或字典缺失时出现 "角色：" 空文案。
const userRoleText = computed(() => {
  const labels = props.currentUserRoleLabels?.filter((label) => !!label && label.trim().length > 0) ?? [];
  if (labels.length === 0) {
    return "";
  }
  return labels.join("、");
});
const userInitial = computed(() => {
  const first = userName.value.trim().charAt(0);
  return first ? first.toUpperCase() : "?";
});

function serviceAddressFromBaseUrl(baseUrl?: string) {
  if (!baseUrl) return "";
  try {
    const url = new URL(baseUrl);
    return url.hostname && url.port ? `${url.hostname}:${url.port}` : "";
  } catch {
    return "";
  }
}

function opencodeServiceAddress(process?: UserOpencodeProcess | null) {
  if (!process) return "";
  if (process.serviceAddress?.trim()) return process.serviceAddress.trim();
  const addressFromBaseUrl = serviceAddressFromBaseUrl(process.baseUrl);
  if (addressFromBaseUrl) return addressFromBaseUrl;
  return "";
}

function opencodeServiceServerName(process?: UserOpencodeProcess | null) {
  return process?.linuxServerId?.trim() ?? "";
}

function opencodeServiceTarget(process?: UserOpencodeProcess | null) {
  const serverName = opencodeServiceServerName(process);
  const address = opencodeServiceAddress(process);
  if (serverName && address) return `${serverName} / ${address}`;
  return serverName || address;
}

const opencodeServiceDisplay = computed(() => {
  if (props.opencodeProcessLoading) {
    return { tone: "checking", text: "正在查询" };
  }
  const process = props.opencodeProcessStatus;
  if (!process) {
    return { tone: "checking", text: "状态未知" };
  }
  const target = opencodeServiceTarget(process);
  const hasAssignedServer = Boolean(opencodeServiceServerName(process) || opencodeServiceAddress(process));
  const serviceStatus = process?.serviceStatus ?? (process?.status === "READY" ? "RUNNING" : hasAssignedServer ? "NOT_RUNNING" : "UNASSIGNED");
  if (serviceStatus === "RUNNING") {
    return { tone: "running", text: target ? `运行中(${target})` : "运行中" };
  }
  if (serviceStatus === "NOT_RUNNING") {
    return { tone: "stopped", text: target ? `未运行(${target})` : "未运行" };
  }
  return { tone: "unassigned", text: "待分配专属进程" };
});

function onAppMenuBlur(event: FocusEvent) {
  const next = event.relatedTarget as Node | null;
  if (next && (event.currentTarget as Node).contains(next)) return;
  setTimeout(closeAppMenu, 120);
}

function onUserMenuBlur(event: FocusEvent) {
  const next = event.relatedTarget as Node | null;
  if (next && (event.currentTarget as Node).contains(next)) return;
  setTimeout(closeUserMenu, 120);
}

function onResizeStart(side: "left" | "right", event: MouseEvent) {
  resizing.value = side;
  resizeStartX = event.clientX;
  resizeStartWidth = side === "left" ? leftPanelWidth.value : rightPanelWidth.value;
  document.addEventListener("mousemove", onResizeMove);
  document.addEventListener("mouseup", onResizeEnd);
  document.body.style.cursor = "col-resize";
  document.body.style.userSelect = "none";
}

function onResizeMove(event: MouseEvent) {
  if (!resizing.value) return;
  const delta = event.clientX - resizeStartX;
  // 左侧把手：向右拖 → 左侧更宽；右侧把手：向左拖 → 右侧更宽。
  if (resizing.value === "left") {
    const nextWidth = Math.min(MAX_LEFT_WIDTH, Math.max(MIN_LEFT_WIDTH, resizeStartWidth + delta));
    leftPanelWidth.value = nextWidth;
  } else {
    const nextWidth = Math.min(MAX_RIGHT_WIDTH, Math.max(MIN_RIGHT_WIDTH, resizeStartWidth - delta));
    rightPanelWidth.value = nextWidth;
  }
}

function onResizeEnd() {
  resizing.value = null;
  document.removeEventListener("mousemove", onResizeMove);
  document.removeEventListener("mouseup", onResizeEnd);
  document.body.style.cursor = "";
  document.body.style.userSelect = "";
}

onUnmounted(() => {
  document.removeEventListener("mousemove", onResizeMove);
  document.removeEventListener("mouseup", onResizeEnd);
  document.body.style.cursor = "";
  document.body.style.userSelect = "";
});

// --- MIMO Robot Easter Egg Logic ---
type RobotState =
  | "sleeping"
  | "spawning"
  | "idle"
  | "charging"
  | "jumping-up"
  | "jumping-down"
  | "landing"
  | "waving"
  | "exiting-charge"
  | "exiting-fly"
  | "walking"
  | "sitting"
  | "flipping"
  | "hanging"
  | "shaking"
  | "celebrating";

const robotState = ref<RobotState>("sleeping");
const robotX = ref(0);
const robotY = ref(0);
const robotDirection = ref<"left" | "right" | "front">("front");
const robotTransition = ref("none");

let inactivityTimer: ReturnType<typeof setTimeout> | null = null;
let naturalExitTimer: ReturnType<typeof setTimeout> | null = null;
let behaviorTimer: ReturnType<typeof setTimeout> | null = null;
let movementTimeout: ReturnType<typeof setTimeout> | null = null;

const robotCurrentLevel = ref<"top" | "bottom">("top");
// 保存坐标只作为下一次出现的起点，不代表要冻结动作或离场逻辑。
const robotHasSavedPosition = ref(false);
// 手动唤起后保持可见；用户拖动或键盘定位后才恢复自然宠物的离场规则。
const robotKeepVisible = ref(false);
const robotDragging = ref(false);

const ROBOT_WIDTH = 24;
const ROBOT_HEIGHT = 32;
const ROBOT_VIEWPORT_MARGIN = 8;
const ROBOT_DRAG_THRESHOLD = 4;
const ROBOT_KEYBOARD_STEP = 8;
const ROBOT_POSITION_STORAGE_KEY = "figma-shell-robot-pos";

let robotDragPointerId: number | null = null;
let robotDragStartClientX = 0;
let robotDragStartClientY = 0;
let robotDragStartX = 0;
let robotDragStartY = 0;
let robotDragWasEffective = false;
let robotDragTarget: HTMLElement | null = null;
let robotDragPreviousCursor = "";
let robotDragPreviousUserSelect = "";

// Throttled activity timer
let lastActivityTime = 0;

type RobotPosition = { x: number; y: number };

// 坐标统一表示机器人根元素左上角相对 viewport 的位置，避免存储、渲染和拖动出现 32px 偏移。
function clampRobotPosition(position: RobotPosition): RobotPosition {
  const maxX = Math.max(ROBOT_VIEWPORT_MARGIN, window.innerWidth - ROBOT_WIDTH - ROBOT_VIEWPORT_MARGIN);
  const maxY = Math.max(ROBOT_VIEWPORT_MARGIN, window.innerHeight - ROBOT_HEIGHT - ROBOT_VIEWPORT_MARGIN);
  return {
    x: Math.min(maxX, Math.max(ROBOT_VIEWPORT_MARGIN, position.x)),
    y: Math.min(maxY, Math.max(ROBOT_VIEWPORT_MARGIN, position.y))
  };
}

function loadSavedRobotPosition(): RobotPosition | null {
  try {
    const raw = window.localStorage.getItem(ROBOT_POSITION_STORAGE_KEY);
    if (!raw) return null;
    const position = JSON.parse(raw) as Partial<RobotPosition>;
    if (!Number.isFinite(position.x) || !Number.isFinite(position.y)) return null;
    return clampRobotPosition({ x: position.x!, y: position.y! });
  } catch {
    return null;
  }
}

function saveRobotPosition() {
  try {
    window.localStorage.setItem(ROBOT_POSITION_STORAGE_KEY, JSON.stringify({ x: robotX.value, y: robotY.value }));
  } catch {
    // 隐私模式或存储配额异常时保留当前内存位置，不影响机器人交互。
  }
}

function pauseRobotForManualPlacement() {
  // 拖动期间暂停当前计时器，释放后立即恢复同一套自然动作和离场调度。
  clearAllRobotTimers();
  if (inactivityTimer) clearTimeout(inactivityTimer);
  inactivityTimer = null;
  robotState.value = "idle";
  robotDirection.value = "front";
  robotTransition.value = "none";
}

function scheduleNaturalExit() {
  if (naturalExitTimer) clearTimeout(naturalExitTimer);
  const stayDuration = (15 + Math.random() * 45) * 1000;
  naturalExitTimer = setTimeout(triggerExit, stayDuration);
}

function resumeNaturalRobotBehavior() {
  if (robotState.value === "sleeping") return;
  if (robotKeepVisible.value) {
    if (naturalExitTimer) clearTimeout(naturalExitTimer);
    naturalExitTimer = null;
  } else {
    scheduleNaturalExit();
  }
  scheduleNextAction();
}

function cleanupRobotDrag() {
  const target = robotDragTarget;
  const pointerId = robotDragPointerId;
  if (!robotDragging.value && !target && pointerId === null) return;

  robotDragging.value = false;
  robotDragPointerId = null;
  robotDragTarget = null;
  document.body.style.cursor = robotDragPreviousCursor;
  document.body.style.userSelect = robotDragPreviousUserSelect;
  if (target && pointerId !== null && target.hasPointerCapture?.(pointerId)) {
    target.releasePointerCapture?.(pointerId);
  }
}

function onRobotPointerDown(event: PointerEvent) {
  if (event.isPrimary === false || robotDragPointerId !== null) return;
  robotDragPointerId = event.pointerId;
  robotDragStartClientX = event.clientX;
  robotDragStartClientY = event.clientY;
  robotDragStartX = robotX.value;
  robotDragStartY = robotY.value;
  robotDragWasEffective = false;
  robotDragTarget = event.currentTarget as HTMLElement;
  robotDragTarget.setPointerCapture?.(event.pointerId);
  robotDragging.value = true;
  robotDragPreviousCursor = document.body.style.cursor;
  robotDragPreviousUserSelect = document.body.style.userSelect;
  document.body.style.cursor = "grabbing";
  document.body.style.userSelect = "none";
}

function onRobotPointerMove(event: PointerEvent) {
  if (event.pointerId !== robotDragPointerId) return;
  const deltaX = event.clientX - robotDragStartClientX;
  const deltaY = event.clientY - robotDragStartClientY;
  if (!robotDragWasEffective && Math.hypot(deltaX, deltaY) < ROBOT_DRAG_THRESHOLD) return;

  if (!robotDragWasEffective) {
    robotDragWasEffective = true;
    pauseRobotForManualPlacement();
  }
  event.preventDefault();
  const position = clampRobotPosition({ x: robotDragStartX + deltaX, y: robotDragStartY + deltaY });
  robotX.value = position.x;
  robotY.value = position.y;
}

function finishRobotDrag(pointerId?: number) {
  if (robotDragPointerId === null || (pointerId !== undefined && pointerId !== robotDragPointerId)) return;
  if (robotDragWasEffective) {
    const position = clampRobotPosition({ x: robotX.value, y: robotY.value });
    robotX.value = position.x;
    robotY.value = position.y;
    robotHasSavedPosition.value = true;
    saveRobotPosition();
    robotKeepVisible.value = false;
    resumeNaturalRobotBehavior();
  }
  cleanupRobotDrag();
}

function finishRobotPointerDrag(event: PointerEvent) {
  finishRobotDrag(event.pointerId);
}

function onRobotLostPointerCapture(event: PointerEvent) {
  finishRobotDrag(event.pointerId);
}

function onRobotKeydown(event: KeyboardEvent) {
  const movements: Record<string, RobotPosition> = {
    ArrowUp: { x: 0, y: -ROBOT_KEYBOARD_STEP },
    ArrowDown: { x: 0, y: ROBOT_KEYBOARD_STEP },
    ArrowLeft: { x: -ROBOT_KEYBOARD_STEP, y: 0 },
    ArrowRight: { x: ROBOT_KEYBOARD_STEP, y: 0 }
  };
  const movement = movements[event.key];
  if (!movement) return;

  // 键盘移动与拖动走同一手动定位路径，释放后恢复自然行为。
  event.preventDefault();
  pauseRobotForManualPlacement();
  const position = clampRobotPosition({ x: robotX.value + movement.x, y: robotY.value + movement.y });
  robotX.value = position.x;
  robotY.value = position.y;
  robotHasSavedPosition.value = true;
  saveRobotPosition();
  robotKeepVisible.value = false;
  resumeNaturalRobotBehavior();
}

// Safe X ranges logic
function getSafeXRange(level: "top" | "bottom") {
  if (level === "bottom") {
    return [50, window.innerWidth - 50];
  }
  // Level is 'top': avoid logo on left, avatar on right
  const leftEl = document.querySelector(".figma-header-left");
  const leftWidth = leftEl ? leftEl.getBoundingClientRect().right : 220;

  const rightEl = document.querySelector(".figma-header-right");
  const rightWidth = rightEl ? window.innerWidth - rightEl.getBoundingClientRect().left : 220;

  const minX = leftWidth + 20;
  const maxX = window.innerWidth - rightWidth - 20;
  if (minX >= maxX) {
    return [100, window.innerWidth - 100];
  }
  return [minX, maxX];
}

// Bounding box of the title for spawning
function getBirthPosition() {
  const el = document.querySelector(".figma-title");
  if (el) {
    const rect = el.getBoundingClientRect();
    return {
      x: rect.left + rect.width / 2,
      y: rect.bottom - ROBOT_HEIGHT
    };
  }
  return { x: 100, y: 36 - ROBOT_HEIGHT };
}

// Exit logic (interrupted or naturally)
function triggerExit() {
  if (robotState.value === "sleeping" || robotState.value === "exiting-charge" || robotState.value === "exiting-fly") {
    return;
  }

  // Clear running timers
  clearAllRobotTimers();

  robotState.value = "waving";
  robotDirection.value = "front";
  robotTransition.value = "none";

  // Wave for 1.2s
  movementTimeout = setTimeout(() => {
    robotState.value = "exiting-charge";

    // Charge for 300ms
    movementTimeout = setTimeout(() => {
      robotState.value = "exiting-fly";

      const flyLeft = Math.random() < 0.5;
      const targetX = flyLeft ? -100 : window.innerWidth + 100;
      const startX = robotX.value;
      const startY = robotY.value;
      const peakY = Math.max(-100, startY - 180);

      // Phase 1: Fly Up
      robotTransition.value = "left 0.4s linear, top 0.4s ease-out";
      robotDirection.value = flyLeft ? "left" : "right";
      robotX.value = (startX + targetX) / 2;
      robotY.value = peakY;

      movementTimeout = setTimeout(() => {
        // Phase 2: Fly Down/Out
        robotTransition.value = "left 0.4s linear, top 0.4s ease-in";
        robotX.value = targetX;
        robotY.value = -200; // Fly out upwards/sideways

        movementTimeout = setTimeout(() => {
          robotState.value = "sleeping";
          resetInactivityTimer();
        }, 400);
      }, 400);
    }, 300);
  }, 1200);
}

// Clean timers helper
function clearAllRobotTimers() {
  if (naturalExitTimer) clearTimeout(naturalExitTimer);
  if (behaviorTimer) clearTimeout(behaviorTimer);
  if (movementTimeout) clearTimeout(movementTimeout);
}

// Reset activity and 1-minute inactivity timer
function resetInactivityTimer() {
  if (inactivityTimer) clearTimeout(inactivityTimer);
  inactivityTimer = null;
  if (robotState.value !== "sleeping") {
    // 页面活动只负责重新计算“隐藏态的一分钟自动出现”计时，不能把已经唤起的宠物当成离场信号。
    // 宠物的自然离场由 scheduleNaturalExit 单独控制，避免用户点击/输入后宠物突然消失。
    return;
  }

  // Only trigger when page is active and in focus
  if (document.hidden || !document.hasFocus()) {
    return;
  }

  inactivityTimer = setTimeout(spawnRobot, 60000); // 1 minute (60 seconds)
}

// Spawn logic
function spawnRobot() {
  if (robotState.value !== "sleeping") return;
  if (document.hidden || !document.hasFocus()) {
    resetInactivityTimer();
    return;
  }

  clearAllRobotTimers();
  if (inactivityTimer) clearTimeout(inactivityTimer);

  const savedPosition = loadSavedRobotPosition();
  const birth = savedPosition ?? getBirthPosition();
  robotX.value = birth.x;
  robotY.value = birth.y;
  robotHasSavedPosition.value = Boolean(savedPosition);
  robotKeepVisible.value = false;
  robotCurrentLevel.value = savedPosition && savedPosition.y > window.innerHeight / 2 ? "bottom" : "top";

  // 有保存坐标时从该起点直接进入自然待机；坐标不是静止开关，动作和离场仍照常启动。
  if (savedPosition) {
    robotState.value = "idle";
    robotDirection.value = "front";
    robotTransition.value = "none";
    resumeNaturalRobotBehavior();
    return;
  }

  robotState.value = "spawning";
  robotTransition.value = "none";

  const [minX, maxX] = getSafeXRange("top");
  const targetX = minX + Math.random() * (maxX - minX);
  const targetY = 36 - ROBOT_HEIGHT; // navbar bottom floor

  robotDirection.value = targetX > birth.x ? "right" : "left";

  // Use nextTick and a slight timeout to let Vue render the birth position first
  nextTick(() => {
    setTimeout(() => {
      if (robotState.value !== "spawning") return;

      // Jump from text onto navbar (High parabola)
      const peakY = -40; // High in the sky

      // Phase 1: Up
      robotTransition.value = "left 0.4s linear, top 0.4s ease-out";
      robotX.value = (birth.x + targetX) / 2;
      robotY.value = peakY;

      movementTimeout = setTimeout(() => {
        if (robotState.value !== "spawning") return;

        // Phase 2: Down
        robotTransition.value = "left 0.4s linear, top 0.4s ease-in";
        robotX.value = targetX;
        robotY.value = targetY;

        movementTimeout = setTimeout(() => {
          if (robotState.value !== "spawning") return;

          // Land
          robotTransition.value = "none";
          robotState.value = "landing";

          movementTimeout = setTimeout(() => {
            // Start idle behavior
            robotState.value = "idle";

            resumeNaturalRobotBehavior();
          }, 250);
        }, 400);
      }, 400);
    }, 50);
  });
}

// Action selection
function scheduleNextAction() {
  if (robotState.value !== "idle" && robotState.value !== "sitting" && robotState.value !== "hanging") return;

  if (behaviorTimer) clearTimeout(behaviorTimer);
  behaviorTimer = setTimeout(() => {
    if (robotState.value !== "idle" && robotState.value !== "sitting" && robotState.value !== "hanging") return;

    if (robotCurrentLevel.value === "top") {
      // At top: restrict upward jumps to prevent going off-screen
      const rand = Math.random();
      if (rand < 0.30) {
        executeWalking();
      } else if (rand < 0.48) {
        executeHanging();
      } else if (rand < 0.64) {
        executeBigJump(); // Drop down to bottom
      } else if (rand < 0.75) {
        executeSitting();
      } else if (rand < 0.87) {
        executeShaking();
      } else if (rand < 0.97) {
        executeCelebrating();
      } else {
        if (Math.random() < 0.6) {
          executeSitting();
        } else {
          executeStayIdle();
        }
      }
    } else {
      // At bottom: normal full actions including upward jumps
      const rand = Math.random();
      if (rand < 0.25) {
        executeShortJump();
      } else if (rand < 0.50) {
        executeWalking();
      } else if (rand < 0.65) {
        executeBackflip();
      } else if (rand < 0.80) {
        executeBigJump(); // Jump up to top navbar
      } else if (rand < 0.92) {
        executeBounce();
      } else if (rand < 0.97) {
        executeShaking();
      } else if (rand < 0.995) {
        executeCelebrating();
      } else {
        if (Math.random() < 0.6) {
          executeSitting();
        } else {
          executeStayIdle();
        }
      }
    }
  }, 1000 + Math.random() * 1000); // 1-2s cycle for high activity
}

function executeStayIdle() {
  robotState.value = "idle";
  scheduleNextAction();
}

function executeSitting() {
  robotState.value = "sitting";
  robotDirection.value = "front";

  // Sit down for 1.5s to 3s
  const sitDuration = 1500 + Math.random() * 1500;
  movementTimeout = setTimeout(() => {
    if (robotState.value !== "sitting") return;
    robotState.value = "idle";
    robotDirection.value = Math.random() < 0.5 ? "left" : "right";
    scheduleNextAction();
  }, sitDuration);
}

function executeHanging() {
  robotState.value = "hanging";
  robotDirection.value = "front";

  // Hang upside down for 2s to 4s
  const hangDuration = 2000 + Math.random() * 2000;
  movementTimeout = setTimeout(() => {
    if (robotState.value !== "hanging") return;
    robotState.value = "idle";
    robotDirection.value = Math.random() < 0.5 ? "left" : "right";
    scheduleNextAction();
  }, hangDuration);
}

function executeShaking() {
  robotState.value = "shaking";
  robotDirection.value = "front";
  robotTransition.value = "none";
  const duration = 450 + Math.random() * 350;
  movementTimeout = setTimeout(() => {
    if (robotState.value !== "shaking") return;
    robotState.value = "idle";
    robotDirection.value = Math.random() < 0.5 ? "left" : "right";
    scheduleNextAction();
  }, duration);
}

function executeCelebrating() {
  robotState.value = "celebrating";
  robotDirection.value = "front";
  robotTransition.value = "none";
  const duration = 700 + Math.random() * 500;
  movementTimeout = setTimeout(() => {
    if (robotState.value !== "celebrating") return;
    robotState.value = "idle";
    robotDirection.value = Math.random() < 0.5 ? "left" : "right";
    scheduleNextAction();
  }, duration);
}

function executeWalking() {
  const startX = robotX.value;
  const [minX, maxX] = getSafeXRange(robotCurrentLevel.value);

  const direction = Math.random() < 0.5 ? -1 : 1;
  const dx = 50 + Math.random() * 50; // 50-100px
  let targetX = startX + direction * dx;

  if (targetX < minX) {
    targetX = Math.min(maxX, startX + dx);
  } else if (targetX > maxX) {
    targetX = Math.max(minX, startX - dx);
  }

  if (Math.abs(targetX - startX) < 15) {
    if (Math.random() < 0.5) {
      executeSitting();
    } else {
      executeBounce();
    }
    return;
  }

  const duration = Math.abs(targetX - startX) / 50; // speed 50px/s
  robotDirection.value = targetX > startX ? "right" : "left";
  robotState.value = "walking";
  robotTransition.value = `left ${duration}s linear`;
  robotX.value = targetX;

  movementTimeout = setTimeout(() => {
    if (robotState.value !== "walking") return;
    robotTransition.value = "none";
    robotState.value = "idle";
    scheduleNextAction();
  }, duration * 1000);
}

function executeBackflip() {
  const startY = robotY.value;
  robotState.value = "charging";

  movementTimeout = setTimeout(() => {
    if (robotState.value !== "charging") return;

    // Flip Jump Up
    robotState.value = "flipping";
    robotTransition.value = "top 0.4s ease-out";
    robotY.value = startY - 60;

    movementTimeout = setTimeout(() => {
      if (robotState.value !== "flipping") return;

      // Fall Down
      robotTransition.value = "top 0.4s ease-in";
      robotY.value = startY;

      movementTimeout = setTimeout(() => {
        if (robotState.value !== "flipping") return;

        // Land
        robotTransition.value = "none";
        robotState.value = "landing";

        movementTimeout = setTimeout(() => {
          robotState.value = "idle";
          scheduleNextAction();
        }, 250);
      }, 400);
    }, 400);
  }, 300);
}

function executeBounce() {
  const startY = robotY.value;
  robotState.value = "charging";

  movementTimeout = setTimeout(() => {
    if (robotState.value !== "charging") return;

    // Jump Up
    robotState.value = "jumping-up";
    robotTransition.value = "top 0.35s ease-out";
    robotY.value = startY - 50;

    movementTimeout = setTimeout(() => {
      if (robotState.value !== "jumping-up") return;

      // Fall Down
      robotState.value = "jumping-down";
      robotTransition.value = "top 0.35s ease-in";
      robotY.value = startY;

      movementTimeout = setTimeout(() => {
        if (robotState.value !== "jumping-down") return;

        // Land
        robotTransition.value = "none";
        robotState.value = "landing";

        movementTimeout = setTimeout(() => {
          robotState.value = "idle";
          scheduleNextAction();
        }, 250);
      }, 350);
    }, 350);
  }, 300);
}

function executeShortJump() {
  const startX = robotX.value;
  const startY = robotY.value;
  const [minX, maxX] = getSafeXRange(robotCurrentLevel.value);

  const direction = Math.random() < 0.5 ? -1 : 1;
  const dx = 60 + Math.random() * 90; // 60-150px
  let targetX = startX + direction * dx;

  // Clamp and correct direction if hitting boundary
  if (targetX < minX) {
    targetX = Math.min(maxX, startX + dx);
  } else if (targetX > maxX) {
    targetX = Math.max(minX, startX - dx);
  }

  // If space is too narrow to jump
  if (Math.abs(targetX - startX) < 15) {
    executeBounce();
    return;
  }

  robotDirection.value = targetX > startX ? "right" : "left";
  robotState.value = "charging";

  movementTimeout = setTimeout(() => {
    if (robotState.value !== "charging") return;

    // Phase 1: Jump Up
    robotState.value = "jumping-up";
    robotTransition.value = "left 0.4s linear, top 0.4s ease-out";
    robotX.value = (startX + targetX) / 2;
    robotY.value = startY - 40;

    movementTimeout = setTimeout(() => {
      if (robotState.value !== "jumping-up") return;

      // Phase 2: Fall Down
      robotState.value = "jumping-down";
      robotTransition.value = "left 0.4s linear, top 0.4s ease-in";
      robotX.value = targetX;
      robotY.value = startY;

      movementTimeout = setTimeout(() => {
        if (robotState.value !== "jumping-down") return;

        // Land
        robotTransition.value = "none";
        robotState.value = "landing";

        movementTimeout = setTimeout(() => {
          robotState.value = "idle";
          scheduleNextAction();
        }, 250);
      }, 400);
    }, 400);
  }, 300);
}

function executeBigJump() {
  const startX = robotX.value;
  const startY = robotY.value;
  const targetLevel = robotCurrentLevel.value === "top" ? "bottom" : "top";
  const targetY = targetLevel === "top" ? 36 - ROBOT_HEIGHT : window.innerHeight - ROBOT_HEIGHT - 4;
  const [minX, maxX] = getSafeXRange(targetLevel);
  const targetX = minX + Math.random() * (maxX - minX);

  robotDirection.value = targetX > startX ? "right" : "left";

  if (robotCurrentLevel.value === "top") {
    // Drop down directly from top to bottom (no upward jump, so no off-screen)
    robotState.value = "jumping-down";
    robotTransition.value = "left 0.6s linear, top 0.6s ease-in";
    robotX.value = targetX;
    robotY.value = targetY;

    movementTimeout = setTimeout(() => {
      if (robotState.value !== "jumping-down") return;

      robotCurrentLevel.value = "bottom";

      // Land
      robotTransition.value = "none";
      robotState.value = "landing";

      movementTimeout = setTimeout(() => {
        robotState.value = "idle";
        scheduleNextAction();
      }, 250);
    }, 600);
  } else {
    // Jump up from bottom to top (requires peakY)
    robotState.value = "charging";

    movementTimeout = setTimeout(() => {
      if (robotState.value !== "charging") return;

      const peakY = Math.max(10, Math.min(startY, targetY) - 100);

      // Phase 1: Jump Up
      robotState.value = "jumping-up";
      robotTransition.value = "left 0.6s linear, top 0.6s ease-out";
      robotX.value = (startX + targetX) / 2;
      robotY.value = peakY;

      movementTimeout = setTimeout(() => {
        if (robotState.value !== "jumping-up") return;

        // Phase 2: Fall Down
        robotState.value = "jumping-down";
        robotTransition.value = "left 0.6s linear, top 0.6s ease-in";
        robotX.value = targetX;
        robotY.value = targetY;

        movementTimeout = setTimeout(() => {
          if (robotState.value !== "jumping-down") return;

          robotCurrentLevel.value = "top";

          // Land
          robotTransition.value = "none";
          robotState.value = "landing";

          movementTimeout = setTimeout(() => {
            robotState.value = "idle";
            scheduleNextAction();
          }, 250);
        }, 600);
      }, 600);
    }, 300);
  }
}

// User activity listener
function handleUserActivity(event?: Event) {
  // 宠物自身的拖动和唤起/收起按钮都不应被全局空闲逻辑当成离场信号。
  const target = event?.target as Element | null;
  if (target?.closest?.(".figma-robot-agent, .figma-robot-visibility-toggle")) return;
  const now = Date.now();
  if (now - lastActivityTime < 100) return;
  lastActivityTime = now;
  resetInactivityTimer();
}

function handleWindowResize() {
  if (robotHasSavedPosition.value) {
    const position = clampRobotPosition({ x: robotX.value, y: robotY.value });
    robotX.value = position.x;
    robotY.value = position.y;
    // 隐藏态保存的是下一次起点，缩放时可同步夹紧；可见态坐标可能正在自然动画，不能覆盖起点偏好。
    if (robotState.value === "sleeping") {
      saveRobotPosition();
    }
    return;
  }
  if (robotState.value === "sleeping") return;
  const [minX, maxX] = getSafeXRange(robotCurrentLevel.value);
  if (robotX.value > maxX) robotX.value = maxX;
  if (robotX.value < minX) robotX.value = minX;
  if (robotCurrentLevel.value === "bottom") {
    robotY.value = window.innerHeight - ROBOT_HEIGHT - 4;
  }
}

const robotStyle = computed(() => ({
  position: "fixed" as const,
  left: `${robotX.value}px`,
  top: `${robotY.value}px`,
  width: `${ROBOT_WIDTH}px`,
  height: `${ROBOT_HEIGHT}px`,
  zIndex: 9999,
  pointerEvents: "auto" as const,
  transition: robotTransition.value,
  opacity: 0.85
}));

function handleFocusChange() {
  finishRobotDrag();
  resetInactivityTimer();
}

function toggleRobotVisibility() {
  if (robotState.value !== "sleeping") {
    // 收起后清理全部动作/离场计时，再重新开始完整的一分钟无操作等待。
    finishRobotDrag();
    clearAllRobotTimers();
    if (inactivityTimer) clearTimeout(inactivityTimer);
    inactivityTimer = null;
    robotState.value = "sleeping";
    robotKeepVisible.value = false;
    resetInactivityTimer();
    return;
  }

  // 手动唤起从保存坐标开始，共享随机动作循环，但保持可见直到用户再次收起或重新定位。
  const saved = loadSavedRobotPosition();
  const position = saved ?? clampRobotPosition(getBirthPosition());
  robotX.value = position.x;
  robotY.value = position.y;
  robotHasSavedPosition.value = Boolean(saved);
  robotKeepVisible.value = true;
  robotCurrentLevel.value = saved && saved.y > window.innerHeight / 2 ? "bottom" : "top";
  robotDirection.value = "front";
  robotTransition.value = "none";
  robotState.value = "idle";
  if (inactivityTimer) clearTimeout(inactivityTimer);
  inactivityTimer = null;
  resumeNaturalRobotBehavior();
}

onMounted(() => {
  window.addEventListener("resize", handleWindowResize);

  // Register inactivity detectors
  window.addEventListener("mousemove", handleUserActivity, { passive: true });
  window.addEventListener("mousedown", handleUserActivity, { passive: true });
  window.addEventListener("keydown", handleUserActivity, { passive: true });
  window.addEventListener("scroll", handleUserActivity, { passive: true });

  // Page active / focus change detectors
  window.addEventListener("focus", handleFocusChange);
  window.addEventListener("blur", handleFocusChange);
  document.addEventListener("visibilitychange", handleFocusChange);

  const savedPosition = loadSavedRobotPosition();
  if (savedPosition) {
    robotX.value = savedPosition.x;
    robotY.value = savedPosition.y;
    robotHasSavedPosition.value = true;
  }
  // 无论是否有保存位置，初次进入都保持隐藏，等连续一分钟无操作后再出现。
  resetInactivityTimer();
});

onUnmounted(() => {
  window.removeEventListener("resize", handleWindowResize);
  window.removeEventListener("mousemove", handleUserActivity);
  window.removeEventListener("mousedown", handleUserActivity);
  window.removeEventListener("keydown", handleUserActivity);
  window.removeEventListener("scroll", handleUserActivity);

  window.removeEventListener("focus", handleFocusChange);
  window.removeEventListener("blur", handleFocusChange);
  document.removeEventListener("visibilitychange", handleFocusChange);

  clearAllRobotTimers();
  if (inactivityTimer) clearTimeout(inactivityTimer);
  // 组件销毁也必须恢复文档样式与 pointer capture，避免拖动中路由切换留下不可选中文本。
  cleanupRobotDrag();
});

// --- Join Applications Logic ---
const addAppVisible = ref(false);
const selectedAppToJoin = ref("");
const joining = ref(false);

function openAddApp() {
  addAppVisible.value = true;
  selectedAppToJoin.value = "";
  appMenuOpen.value = false;
}

function closeAddApp() {
  addAppVisible.value = false;
  selectedAppToJoin.value = "";
}

function submitJoinApp() {
  if (!selectedAppToJoin.value || joining.value) return;
  joining.value = true;
  emit("join-app", selectedAppToJoin.value, (success) => {
    joining.value = false;
    if (success) {
      closeAddApp();
    }
  });
}
</script>

<template>
  <div class="figma-app" @click="closeHeaderMenus">
    <header class="figma-header">
      <div class="figma-header-left">
        <div class="figma-logo-group">
          <img :src="logoUrl" alt="logo" class="figma-logo" />
          <div class="figma-logo-margin" />
          <div class="figma-title-group">
            <span class="figma-title">MIMO测试智能体</span>
            <span class="figma-subtitle">MIMO Intelligent Test Agent</span>
          </div>
        </div>
      </div>



      <div class="figma-header-right">
        <div class="figma-runtime-inventory-wrapper" @click.stop>
          <button
            type="button"
            class="figma-runtime-inventory-summary"
            data-testid="runtime-inventory-summary"
            :aria-expanded="runtimeInventoryOpen"
            aria-label="查看运行态资源详情"
            @click="toggleRuntimeInventory"
          >
            <span>Agent {{ runtimeInventoryCounts.agents }}</span>
            <span>Skill {{ runtimeInventoryCounts.skills }}</span>
            <span>MCP {{ runtimeInventoryCounts.mcp }}</span>
            <span>Plugin {{ runtimeInventoryCounts.plugins }}</span>
          </button>
          <section
            v-if="runtimeInventoryOpen"
            class="figma-runtime-inventory-panel"
            data-testid="runtime-inventory-panel"
            role="dialog"
            aria-label="运行态资源详情"
            @click.stop
          >
            <header class="figma-runtime-inventory-header">
              <div>
                <div class="figma-runtime-inventory-title">运行态资源</div>
                <div class="figma-runtime-inventory-subtitle">当前已加载目录的只读盘点</div>
              </div>
              <button type="button" class="figma-runtime-inventory-close" aria-label="关闭运行态资源详情" @click="closeRuntimeInventory">
                <X :size="14" />
              </button>
            </header>
            <div class="figma-runtime-inventory-body">
              <section class="figma-runtime-inventory-section">
                <div class="figma-runtime-inventory-section-title">Agent <span>{{ runtimeInventoryCounts.agents }}</span></div>
                <ul v-if="runtimeInventory.agents.length" class="figma-runtime-inventory-list">
                  <li v-for="agent in runtimeInventory.agents" :key="agent.id" class="figma-runtime-inventory-row">
                    <span class="figma-runtime-inventory-name">{{ agent.name }}</span>
                    <span v-if="agent.status" class="figma-runtime-inventory-tag">{{ agent.status }}</span>
                    <span v-if="agent.description" class="figma-runtime-inventory-desc">{{ agent.description }}</span>
                  </li>
                </ul>
                <div v-else class="figma-runtime-inventory-empty">暂无已加载 Agent</div>
              </section>
              <section class="figma-runtime-inventory-section">
                <div class="figma-runtime-inventory-section-title">Skill <span>{{ runtimeInventoryCounts.skills }}</span></div>
                <ul v-if="runtimeInventory.skills.length" class="figma-runtime-inventory-list">
                  <li v-for="skill in runtimeInventory.skills" :key="skill.id" class="figma-runtime-inventory-row">
                    <span class="figma-runtime-inventory-name">{{ skill.name }}</span>
                    <span v-if="skill.description" class="figma-runtime-inventory-desc">{{ skill.description }}</span>
                  </li>
                </ul>
                <div v-else class="figma-runtime-inventory-empty">暂无已加载 Skill</div>
              </section>
              <section class="figma-runtime-inventory-section">
                <div class="figma-runtime-inventory-section-title">MCP <span>{{ runtimeInventoryCounts.mcp }}</span></div>
                <ul v-if="runtimeInventory.mcp.length" class="figma-runtime-inventory-list">
                  <li v-for="mcp in runtimeInventory.mcp" :key="mcp.id" class="figma-runtime-inventory-row">
                    <span class="figma-runtime-inventory-name">{{ mcp.name }}</span>
                    <span v-if="mcp.status" class="figma-runtime-inventory-tag">{{ mcp.status }}</span>
                    <span v-if="mcp.description" class="figma-runtime-inventory-desc">{{ mcp.description }}</span>
                  </li>
                </ul>
                <div v-else class="figma-runtime-inventory-empty">暂无 MCP 状态条目</div>
                <div class="figma-runtime-inventory-subsection">
                  <span>MCP tools {{ runtimeInventory.mcpTools?.length ?? 0 }}</span>
                  <span>MCP resources {{ runtimeInventory.mcpResources?.length ?? 0 }}</span>
                </div>
                <ul
                  v-if="(runtimeInventory.mcpTools?.length ?? 0) || (runtimeInventory.mcpResources?.length ?? 0)"
                  class="figma-runtime-inventory-list is-compact"
                >
                  <li v-for="tool in runtimeInventory.mcpTools" :key="`tool:${tool.id}`" class="figma-runtime-inventory-row">
                    <span class="figma-runtime-inventory-name">{{ tool.name }}</span>
                    <span class="figma-runtime-inventory-tag">tool</span>
                    <span v-if="tool.description" class="figma-runtime-inventory-desc">{{ tool.description }}</span>
                  </li>
                  <li v-for="resource in runtimeInventory.mcpResources" :key="`resource:${resource.id}`" class="figma-runtime-inventory-row">
                    <span class="figma-runtime-inventory-name">{{ resource.name }}</span>
                    <span class="figma-runtime-inventory-tag">resource</span>
                    <span v-if="resource.description" class="figma-runtime-inventory-desc">{{ resource.description }}</span>
                  </li>
                </ul>
              </section>
              <section class="figma-runtime-inventory-section">
                <div class="figma-runtime-inventory-section-title">Plugin <span>{{ runtimeInventoryCounts.plugins }}</span></div>
                <ul v-if="runtimeInventory.plugins.length" class="figma-runtime-inventory-list">
                  <li v-for="plugin in runtimeInventory.plugins" :key="plugin.id" class="figma-runtime-inventory-row">
                    <span class="figma-runtime-inventory-name">{{ plugin.name }}</span>
                    <span v-if="plugin.description" class="figma-runtime-inventory-desc">{{ plugin.description }}</span>
                  </li>
                </ul>
                <div v-else class="figma-runtime-inventory-empty">当前运行态未提供独立 Plugin 目录</div>
              </section>
            </div>
          </section>
        </div>
        <div class="figma-app-menu-wrapper" @click.stop>
          <button
            type="button"
            :class="['figma-app-menu-trigger', appMenuOpen && 'is-open']"
            aria-haspopup="listbox"
            :aria-expanded="appMenuOpen"
            @click="toggleAppMenu"
            @blur="onAppMenuBlur"
          >
            <span class="figma-app-menu-name">{{ selectedApp?.name || "F-GCMS-PSN" }}</span>
            <ChevronDown class="figma-app-menu-chevron" :class="{ 'is-open': appMenuOpen }" />
          </button>
          <ul v-if="appMenuOpen" class="figma-app-menu-dropdown" role="listbox">
            <li
              v-for="app in apps"
              :key="app.id"
              :class="['figma-app-menu-item', app.id === selectedApp?.id && 'is-active']"
              role="option"
              :aria-selected="app.id === selectedApp?.id"
              tabindex="0"
              @mousedown.prevent="selectApp(app)"
            >
              <div class="figma-app-menu-item-main">
                <span class="figma-app-menu-item-name">{{ app.name }}</span>
                <span v-if="app.description" class="figma-app-menu-item-desc">{{ app.description }}</span>
              </div>
              <span v-if="app.id === selectedApp?.id" class="figma-app-menu-item-check">✓</span>
            </li>
            <li class="figma-app-menu-divider" />
            <li class="figma-app-menu-item is-add-app" role="option" tabindex="0" @mousedown.prevent="openAddApp">
              <div class="figma-app-menu-item-main figma-app-menu-add-item">
                <span class="figma-app-menu-add-icon">+</span>
                <span class="figma-app-menu-add-text">加入其他应用</span>
              </div>
            </li>
          </ul>
        </div>

        <!-- 加入应用弹窗 (弹出div) -->
        <div v-if="addAppVisible" class="figma-add-app-overlay" @click="closeAddApp">
          <div class="figma-add-app-card" @click.stop>
            <div class="figma-add-app-header">
              <span class="figma-add-app-title">加入其他应用</span>
              <button type="button" class="figma-add-app-close" @click="closeAddApp">×</button>
            </div>
            <div class="figma-add-app-body">
              <div class="figma-add-app-section">
                <label class="figma-add-app-label">已加入的应用</label>
                <div class="figma-joined-apps-tags">
                  <span v-for="app in apps" :key="app.id" class="figma-joined-app-tag">
                    {{ app.name }}
                  </span>
                </div>
              </div>
              <div class="figma-add-app-section">
                <label class="figma-add-app-label">选择要加入的应用</label>
                <el-select v-model="selectedAppToJoin" placeholder="请选择应用" class="figma-add-app-select" size="default">
                  <el-option
                    v-for="app in joinableApps"
                    :key="app.appId"
                    :label="app.appName"
                    :value="app.appId"
                  />
                </el-select>
              </div>
            </div>
            <div class="figma-add-app-footer">
              <el-button size="small" @click="closeAddApp">取消</el-button>
              <el-button type="primary" size="small" :loading="joining" :disabled="!selectedAppToJoin" @click="submitJoinApp">
                保存
              </el-button>
            </div>
          </div>
        </div>
        <div class="figma-user-menu-wrapper" @click.stop @blur="onUserMenuBlur">
          <button
            type="button"
            class="figma-user-avatar-btn"
            :class="{ 'is-open': userMenuOpen }"
            :aria-label="`当前用户 ${userName}`"
            aria-haspopup="menu"
            :aria-expanded="userMenuOpen"
            @click="toggleUserMenu"
          >
            <span class="figma-user-avatar figma-user-avatar--compact">{{ userInitial }}</span>
          </button>
          <div v-if="userMenuOpen" class="figma-user-menu-dropdown" role="menu">
            <div v-if="userRoleText" class="figma-user-menu-role" role="presentation" aria-label="当前用户角色">
              <ShieldCheck class="figma-user-menu-icon" />
              <span class="figma-user-menu-role-text" :title="userRoleText">{{ userRoleText }}</span>
            </div>
            <div
              class="figma-user-menu-service"
              :class="`figma-user-menu-service--${opencodeServiceDisplay.tone}`"
              role="status"
              aria-label="TestAgent 服务状态"
            >
              <span class="figma-user-menu-service-dot" aria-hidden="true" />
              <span class="figma-user-menu-service-text" :title="opencodeServiceDisplay.text">{{ opencodeServiceDisplay.text }}</span>
            </div>
            <div class="figma-user-menu-summary">
              <UserRound class="figma-user-menu-icon" />
              <span class="figma-user-menu-name">{{ userName }}</span>
            </div>
            <button type="button" class="figma-user-menu-item" role="menuitem" @mousedown.prevent="logout">
              <LogOut class="figma-user-menu-icon" />
              <span>退出登录</span>
            </button>
          </div>
        </div>
      </div>
    </header>

    <div class="figma-body">
      <!-- Floating left sidebar toggle button -->
      <div
        class="figma-sidebar-toggle-floating"
        :class="{ 'is-resizing': resizing === 'left' }"
        :style="{ left: leftPanelOpen ? `${leftPanelWidth + 48 - 32}px` : '54px' }"
      >
        <button
          type="button"
          :class="[
            'figma-icon-btn',
            leftPanelOpen ? 'figma-icon-btn-floating-open' : 'figma-icon-btn-ghost figma-icon-btn-ghost--collapsed'
          ]"
          aria-label="切换侧边栏"
          @click.stop="toggleLeftPanel"
        >
          <img
            :src="panelCloseUrl"
            alt="toggle panel"
            class="figma-icon-16"
            :style="{ transform: leftPanelOpen ? 'scaleX(-1)' : 'none' }"
          />
        </button>
      </div>

      <!-- Floating right sidebar toggle button -->
      <div class="figma-sidebar-toggle-floating figma-sidebar-toggle-floating--right">
        <button
          type="button"
          :class="[
            'figma-icon-btn',
            showRightPanel ? 'figma-icon-btn-floating-open' : 'figma-icon-btn-ghost figma-icon-btn-ghost--collapsed'
          ]"
          aria-label="切换右侧栏"
          @click.stop="toggleRightPanel"
        >
          <img
            :src="panelCloseUrl"
            alt="toggle panel"
            class="figma-icon-16"
            :style="{ transform: showRightPanel ? 'none' : 'scaleX(-1)' }"
          />
        </button>
      </div>

      <aside class="figma-activity-bar">
        <slot name="activity" />
        <div class="figma-robot-toggle-activity">
          <button
            type="button"
            class="figma-robot-visibility-toggle figma-robot-visibility-toggle--activity"
            data-testid="robot-visibility-toggle"
            :aria-label="robotState === 'sleeping' ? '唤起小宠物' : '收起小宠物'"
            :aria-pressed="robotState !== 'sleeping'"
            title="唤起或收起小宠物"
            @click.stop="toggleRobotVisibility"
          >
            <svg viewBox="5 0 14 16" class="robot-toggle-svg" width="24" height="28" preserveAspectRatio="xMidYMid meet" aria-hidden="true">
              <!-- 开关复用宠物相同的天线、脑袋和眼睛几何，避免出现两个不同机器人图标。 -->
              <path class="robot-antenna-l" d="M8,4 L6,2" stroke="#42617a" stroke-width="1.2" stroke-linecap="round" />
              <circle class="robot-antenna-l-tip" cx="6" cy="1.5" r="0.8" fill="#7c6bb5" />
              <path class="robot-antenna-r" d="M16,4 L18,2" stroke="#42617a" stroke-width="1.2" stroke-linecap="round" />
              <circle class="robot-antenna-r-tip" cx="18" cy="1.5" r="0.8" fill="#5aa9a6" />
              <rect class="robot-head" x="6" y="4" width="12" height="10" rx="2.5" fill="#27384b" />
              <circle class="robot-eye" cx="12" cy="9" r="1.2" fill="#7cd5d0" />
            </svg>
          </button>
        </div>
      </aside>

      <div class="figma-panel-group">
        <div class="figma-panel-left" :class="{ 'is-resizing': resizing === 'left' }" :style="leftPanelStyle">
          <slot name="files" />
        </div>
        <div
          v-if="leftPanelOpen"
          class="figma-files-resize-handle"
          @mousedown="onResizeStart('left', $event)"
          aria-label="拖拽调整工作目录宽度"
          role="separator"
          aria-orientation="vertical"
        />
        <div class="figma-main-card-container">
          <div class="figma-main-card">
            <div class="figma-panel-center">
              <slot name="editor" />
            </div>
            <div class="figma-chat-panel-wrapper" :class="{ 'is-resizing': resizing === 'right' }" :style="rightPanelStyle">
              <div
                v-if="showRightPanel"
                class="figma-chat-resize-handle"
                @mousedown="onResizeStart('right', $event)"
                aria-label="拖拽调整对话窗口宽度"
                role="separator"
                aria-orientation="vertical"
              />
              <div class="figma-panel-right" :style="{ width: `${rightPanelWidth}px` }">
                <div class="figma-chat-body">
                  <slot name="chat" />
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div class="figma-bottom-drawer" :class="{ 'figma-bottom-drawer--open': bottomOpen }" role="region" aria-label="运行与终端">
        <slot name="bottom" />
      </div>
    </div>

    <!-- MIMO Test Agent Easter Egg Character -->
    <div
      v-if="robotState !== 'sleeping'"
      class="figma-robot-agent"
      :class="{ 'is-dragging': robotDragging }"
      :style="robotStyle"
      data-testid="figma-robot"
      role="group"
      tabindex="0"
      aria-label="可拖动的 MIMO 小宠物，可使用方向键移动"
      aria-describedby="figma-robot-instructions"
      aria-keyshortcuts="ArrowUp ArrowDown ArrowLeft ArrowRight"
      @pointerdown="onRobotPointerDown"
      @pointermove="onRobotPointerMove"
      @pointerup="finishRobotPointerDrag"
      @pointercancel="finishRobotPointerDrag"
      @lostpointercapture="onRobotLostPointerCapture"
      @keydown="onRobotKeydown"
    >
      <span id="figma-robot-instructions" class="figma-robot-instructions">可拖动小宠物；也可使用方向键每次移动 8 像素。</span>
      <div class="robot-dir-wrap" :class="[`facing-${robotDirection}`]">
        <div class="robot-squash-wrap" :class="[`state-${robotState}`]">
          <svg viewBox="0 0 24 32" class="robot-svg" width="24" height="32">
            <!-- Antennas -->
            <path class="robot-antenna-l" d="M8,4 L6,2" stroke="#42617a" stroke-width="1.2" stroke-linecap="round" />
            <circle class="robot-antenna-l-tip" cx="6" cy="1.5" r="0.8" fill="#7c6bb5" />

            <path class="robot-antenna-r" d="M16,4 L18,2" stroke="#42617a" stroke-width="1.2" stroke-linecap="round" />
            <circle class="robot-antenna-r-tip" cx="18" cy="1.5" r="0.8" fill="#5aa9a6" />

            <!-- Head -->
            <rect class="robot-head" x="6" y="4" width="12" height="10" rx="2.5" fill="#27384b" />
            <!-- Facial sensor (glowing/breath) -->
            <circle class="robot-eye" cx="12" cy="9" r="1.2" fill="#7cd5d0" />

            <!-- Body -->
            <rect class="robot-body" x="5" y="15.5" width="14" height="10" rx="4" fill="#354d66" />

            <!-- Left Arm -->
            <rect class="robot-arm-l" x="2.5" y="16" width="2" height="6.5" rx="1" fill="#42617a" />

            <!-- Right Arm -->
            <rect class="robot-arm-r" x="19.5" y="16" width="2" height="6.5" rx="1" fill="#42617a" />

            <!-- Left Leg -->
            <rect class="robot-leg-l" x="9" y="26.5" width="2.5" height="5" rx="1.25" fill="#42617a" />

            <!-- Right Leg -->
            <rect class="robot-leg-r" x="12.5" y="26.5" width="2.5" height="5" rx="1.25" fill="#42617a" />
          </svg>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.figma-app {
  display: grid;
  grid-template-rows: 36px 1fr;
  grid-template-columns: minmax(0, 1fr);
  width: 100%;
  height: 100vh;
  background: #f5f5f5;
  overflow: hidden;
}

/* ---- Header ---- */
.figma-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 36px;
  border-bottom: 1px solid var(--ta-border, #eaeaea);
  background: #fff;
  padding: 0 10px;
  flex-shrink: 0;
  z-index: 50;
  position: relative;
}

.figma-header-left {
  display: flex;
  align-items: center;
  gap: 0;
  height: 100%;
}

.figma-sidebar-toggle {
  display: flex;
  align-items: center;
  padding: 0 14px 0 5px;
}

.figma-logo-group {
  display: flex;
  align-items: center;
  padding-left: 0;
}

.figma-logo {
  height: 20px;
  width: auto;
  flex-shrink: 0;
}

.figma-logo-margin {
  width: 8px;
  flex-shrink: 0;
}

.figma-title-group {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 0px;
}

.figma-title {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  font-weight: 600;
  font-size: 12px;
  line-height: 14px;
  letter-spacing: 0.02em;
  color: #333;
  white-space: nowrap;
}

.figma-subtitle {
  font-family: "Geist", "Noto Sans SC", sans-serif;
  font-weight: 500;
  font-size: 7px;
  line-height: 8px;
  letter-spacing: -0.01em;
  color: #777;
  white-space: nowrap;
  transform: scale(0.9);
  transform-origin: center center;
}

/* ---- Header Right ---- */
.figma-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.figma-runtime-inventory-wrapper {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.figma-robot-visibility-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid #e5e7eb;
  border-radius: 7px;
  background: #fff;
  color: #475569;
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0;
}

.figma-robot-visibility-toggle:hover,
.figma-robot-visibility-toggle[aria-pressed='true'] {
  border-color: #cbd5e1;
  background: #f8fafc;
}

.figma-robot-visibility-toggle:focus-visible {
  outline: 2px solid #2563eb;
  outline-offset: 2px;
}

.figma-robot-toggle-activity {
  position: absolute;
  left: 5px;
  bottom: 52px;
  z-index: 2;
}

.figma-robot-visibility-toggle--activity {
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 12px;
  background: transparent;
}

.figma-robot-visibility-toggle--activity:hover,
.figma-robot-visibility-toggle--activity[aria-pressed='true'] {
  border-color: transparent;
  background: #e8e8e8;
}

.figma-runtime-inventory-summary {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  height: 24px;
  padding: 0 8px;
  border: 0.8px solid #e5e7eb;
  border-radius: 999px;
  background: #f8fafc;
  color: #4b5563;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 11px;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
  cursor: pointer;
}

.figma-runtime-inventory-summary:hover,
.figma-runtime-inventory-summary[aria-expanded='true'] {
  color: #111827;
  border-color: #c7d2fe;
  background: #eef2ff;
}

.figma-runtime-inventory-panel {
  position: absolute;
  top: calc(100% + 7px);
  right: 0;
  z-index: 100;
  width: min(520px, calc(100vw - 24px));
  max-height: min(520px, calc(100vh - 72px));
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border: 1px solid var(--ta-border, #e5e7eb);
  border-radius: 8px;
  background: #fff;
  box-shadow: 0 18px 42px rgba(15, 23, 42, 0.18);
}

.figma-runtime-inventory-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--ta-border, #e5e7eb);
  background: #f8fafc;
}

.figma-runtime-inventory-title {
  font-size: 13px;
  font-weight: 700;
  color: #111827;
}

.figma-runtime-inventory-subtitle {
  margin-top: 2px;
  font-size: 12px;
  color: #6b7280;
}

.figma-runtime-inventory-close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  border: 1px solid #e5e7eb;
  border-radius: 6px;
  background: #fff;
  color: #6b7280;
  cursor: pointer;
}

.figma-runtime-inventory-close:hover {
  color: #111827;
  background: #f3f4f6;
}

.figma-runtime-inventory-body {
  min-height: 0;
  overflow: auto;
  padding: 10px 12px 12px;
}

.figma-runtime-inventory-section + .figma-runtime-inventory-section {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px solid #e5e7eb;
}

.figma-runtime-inventory-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 7px;
  font-size: 12px;
  font-weight: 700;
  color: #111827;
}

.figma-runtime-inventory-section-title span {
  color: #6b7280;
  font-family: "JetBrains Mono", monospace;
  font-weight: 600;
}

.figma-runtime-inventory-list {
  display: grid;
  gap: 5px;
  margin: 0;
  padding: 0;
  list-style: none;
}

.figma-runtime-inventory-list.is-compact {
  margin-top: 6px;
}

.figma-runtime-inventory-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 4px 8px;
  align-items: center;
  min-height: 26px;
  padding: 5px 7px;
  border: 1px solid #edf0f3;
  border-radius: 6px;
  background: #fff;
}

.figma-runtime-inventory-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #111827;
  font-size: 12px;
  font-weight: 600;
}

.figma-runtime-inventory-tag {
  padding: 2px 5px;
  border-radius: 999px;
  background: #f3f4f6;
  color: #6b7280;
  font-size: 10px;
  font-weight: 600;
}

.figma-runtime-inventory-desc {
  grid-column: 1 / -1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: #6b7280;
  font-size: 11px;
}

.figma-runtime-inventory-empty {
  padding: 7px 8px;
  border: 1px dashed #e5e7eb;
  border-radius: 6px;
  color: #6b7280;
  font-size: 12px;
}

.figma-runtime-inventory-subsection {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
  color: #6b7280;
  font-size: 11px;
  font-weight: 600;
}

/* ---- App Dropdown ---- */
.figma-app-menu-wrapper {
  position: relative;
}

.figma-app-menu-trigger {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  height: 24px;
  padding: 0 6px;
  border: 0.8px solid transparent;
  border-radius: 6px;
  background: transparent;
  cursor: pointer;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  transition: background-color 0.12s ease, border-color 0.12s ease;
}

.figma-app-menu-trigger:hover,
.figma-app-menu-trigger.is-open {
  background: #f0f0f0;
  border-color: #dfdfdf;
}

.figma-app-menu-name {
  font-weight: 600;
  font-size: 12px;
  line-height: 16px;
  letter-spacing: 0.0154em;
  color: #18181b;
}

.figma-app-menu-chevron {
  width: 10px;
  height: 10px;
  color: #565656;
  transition: transform 0.16s ease;
}

.figma-app-menu-chevron.is-open {
  transform: rotate(180deg);
}

.figma-app-menu-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 240px;
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  padding: 4px;
  margin: 0;
  list-style: none;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 40;
}

.figma-app-menu-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border-radius: 6px;
  cursor: pointer;
  transition: background-color 0.1s ease;
  outline: none;
}

.figma-app-menu-item:hover,
.figma-app-menu-item:focus {
  background: #f4f4f5;
}

.figma-app-menu-item.is-active {
  background: #fafafa;
}

.figma-app-menu-divider {
  height: 1px;
  background: #e4e4e7;
  margin: 4px 0;
}

.figma-app-menu-add-item {
  display: flex;
  flex-direction: row !important;
  align-items: center;
  gap: 8px;
  color: #18a978;
}

.figma-app-menu-add-icon {
  font-size: 16px;
  font-weight: 600;
  line-height: 1;
}

.figma-app-menu-add-text {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 13px;
  font-weight: 500;
}

/* ---- Join App Pop-up Overlay ---- */
.figma-add-app-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.4);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: fadeIn 0.18s ease-out;
}

.figma-add-app-card {
  width: 380px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 12px 32px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
  animation: slideUp 0.18s ease-out;
}

.figma-add-app-header {
  padding: 16px 20px;
  border-bottom: 1px solid #f4f4f5;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.figma-add-app-title {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 15px;
  font-weight: 600;
  color: #18181b;
}

.figma-add-app-close {
  background: transparent;
  border: none;
  font-size: 20px;
  color: #999;
  cursor: pointer;
  padding: 0 4px;
  line-height: 1;
  transition: color 0.1s;
}

.figma-add-app-close:hover {
  color: #333;
}

.figma-add-app-body {
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.figma-add-app-section {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.figma-add-app-label {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 12px;
  font-weight: 500;
  color: #71717a;
}

.figma-joined-apps-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.figma-joined-app-tag {
  background: #f4f4f5;
  color: #3f3f46;
  font-size: 12px;
  padding: 4px 10px;
  border-radius: 999px;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
}

.figma-add-app-select {
  width: 100%;
}

.figma-add-app-footer {
  padding: 12px 20px 16px;
  background: #fafafa;
  border-top: 1px solid #f4f4f5;
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes slideUp {
  from { transform: translateY(8px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}

.figma-app-menu-item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.figma-app-menu-item-name {
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 13px;
  font-weight: 500;
  line-height: 18px;
  color: #18181b;
}

.figma-app-menu-item-desc {
  font-size: 11px;
  line-height: 14px;
  color: #999;
}

.figma-app-menu-item-check {
  color: #18a978;
  font-size: 14px;
  font-weight: 600;
}

/* ---- User Menu ---- */
.figma-user-menu-wrapper {
  position: relative;
}

.figma-user-avatar-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: 1px solid transparent;
  border-radius: 999px;
  background: transparent;
  cursor: pointer;
  transition: background-color 0.14s ease, border-color 0.14s ease;
}

.figma-user-avatar-btn:hover,
.figma-user-avatar-btn.is-open {
  background: #f0f0f0;
  border-color: #dfdfdf;
}

.figma-user-avatar {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 999px;
  background: #18181b;
  color: #fff;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 10px;
  font-weight: 600;
  line-height: 1;
}

.figma-user-avatar--compact {
  width: 16px;
  height: 16px;
  font-size: 8px;
}

.figma-user-menu-dropdown {
  position: absolute;
  top: calc(100% + 6px);
  right: 0;
  min-width: 168px;
  background: #fff;
  border: 1px solid #e4e4e7;
  border-radius: 8px;
  padding: 4px;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12);
  z-index: 40;
}

.figma-user-menu-summary,
.figma-user-menu-item,
.figma-user-menu-role,
.figma-user-menu-service {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-height: 34px;
  padding: 7px 9px;
  border: 0;
  border-radius: 6px;
  background: transparent;
  color: #18181b;
  font-family: "PingFang SC", "Microsoft YaHei", sans-serif;
  font-size: 13px;
  line-height: 18px;
  text-align: left;
}

/* 顶部「角色」灰显行：与 summary 共用一行的视觉重量，但用次要色 + 更小字号
   暗示它不是可点击项；roleLabels 为空时整行 v-if 不渲染。 */
.figma-user-menu-role {
  color: #9ca3af;
  font-size: 12px;
  cursor: default;
  border-bottom: 1px solid #f0f0f0;
  border-radius: 6px 6px 0 0;
}

.figma-user-menu-role .figma-user-menu-icon {
  color: #b8b8b8;
}

.figma-user-menu-role-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.figma-user-menu-service {
  color: #71717a;
  font-size: 12px;
  cursor: default;
  border-bottom: 1px solid #f0f0f0;
}

.figma-user-menu-service-dot {
  width: 8px;
  height: 8px;
  border-radius: 999px;
  background: #a1a1aa;
  flex-shrink: 0;
}

.figma-user-menu-service-text {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.figma-user-menu-service--running {
  color: #15803d;
}

.figma-user-menu-service--running .figma-user-menu-service-dot {
  background: #22c55e;
}

.figma-user-menu-service--stopped {
  color: #b91c1c;
}

.figma-user-menu-service--stopped .figma-user-menu-service-dot {
  background: #ef4444;
}

.figma-user-menu-service--checking,
.figma-user-menu-service--unassigned {
  color: #71717a;
}

.figma-user-menu-service--checking .figma-user-menu-service-dot,
.figma-user-menu-service--unassigned .figma-user-menu-service-dot {
  background: #a1a1aa;
}

.figma-user-menu-summary {
  color: #666;
  border-bottom: 1px solid #f0f0f0;
  border-radius: 6px 6px 0 0;
}

.figma-user-menu-item {
  margin-top: 4px;
  cursor: pointer;
}

.figma-user-menu-item:hover,
.figma-user-menu-item:focus-visible {
  background: #f4f4f5;
  outline: none;
}

.figma-user-menu-icon {
  width: 14px;
  height: 14px;
  color: #666;
  flex-shrink: 0;
}

.figma-user-menu-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ---- Icon Buttons ---- */
.figma-icon-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 6px;
  border: none !important;
  background: transparent !important;
  cursor: pointer;
  width: 24px;
  height: 24px;
  color: #666;
  box-shadow: none !important;
  transition: background-color 0.14s ease, color 0.14s ease;
}

.figma-icon-btn:hover {
  background: rgba(0, 0, 0, 0.06) !important;
  color: #111;
}

.figma-icon-btn-ghost {
  width: 24px;
  height: 24px;
}

.figma-icon-btn-ghost--collapsed {
  background: rgba(0, 0, 0, 0.04) !important;
}

.figma-icon-btn-secondary {
  width: 24px;
  height: 24px;
  background: #f4f4f5 !important;
}

.figma-icon-btn-secondary:hover {
  background: #e8e8e8 !important;
}

.figma-icon-16 {
  width: 16px;
  height: 16px;
}

/* ---- Body ---- */
.figma-body {
  display: flex;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  padding: 0px;
  position: relative;
}

.figma-activity-bar {
  position: relative;
  width: 48px;
  flex-shrink: 0;
  background: #fff;
  border-right: 1px solid #eaeaea;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.figma-panel-group {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  border: 0;
  border-radius: 0;
  background: #ffffff;
  box-shadow: none;
  overflow: hidden;
}

.figma-panel-left {
  /* 宽度由 :style="width: ${leftPanelWidth}px" 动态控制 */
  flex-shrink: 0;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  min-height: 0;
  overflow: hidden;
  transition: width 0.25s ease, opacity 0.25s ease;
}
.figma-panel-left.is-resizing {
  transition: none !important;
}

.figma-files-resize-handle {
  width: 1px;
  flex-shrink: 0;
  cursor: col-resize;
  position: relative;
  z-index: 5;
  background: var(--ta-border, #eaeaea);
  transition: background-color 0.14s ease;
}

.figma-files-resize-handle::before {
  content: "";
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 4px;
  height: 36px;
  border-radius: 2px;
  background: rgba(0, 0, 0, 0.1);
  transition: background-color 0.15s ease, height 0.15s ease;
}

.figma-files-resize-handle::after {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  left: -3px;
  width: 7px;
  background: transparent;
  cursor: col-resize;
}

.figma-files-resize-handle:hover {
  background: #d8d8d8;
}

.figma-files-resize-handle:hover::before {
  background: rgba(0, 0, 0, 0.3);
  height: 48px;
}

.figma-main-card-container {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  background: transparent;
  padding: 0;
}

.figma-main-card {
  flex: 1;
  min-width: 0;
  display: flex;
  background: #ffffff;
  overflow: hidden;
}

.figma-panel-center {
  flex: 1;
  min-width: 100px;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ---- Right Chat Panel ---- */
.figma-chat-panel-wrapper {
  flex-shrink: 0;
  display: flex;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
  transition: width 0.25s ease, opacity 0.25s ease;
}
.figma-chat-panel-wrapper.is-resizing {
  transition: none !important;
}

.figma-panel-right {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-height: 0;
  min-width: 0;
  overflow: hidden;
}

.figma-chat-resize-handle {
  width: 1px;
  flex-shrink: 0;
  cursor: col-resize;
  position: relative;
  z-index: 5;
  background: var(--ta-border, #eaeaea);
  transition: background-color 0.14s ease;
}

.figma-chat-resize-handle::before {
  content: "";
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  width: 4px;
  height: 36px;
  border-radius: 2px;
  background: rgba(0, 0, 0, 0.1);
  transition: background-color 0.15s ease, height 0.15s ease;
}

.figma-chat-resize-handle::after {
  content: "";
  position: absolute;
  top: 0;
  bottom: 0;
  left: -3px;
  width: 7px;
  background: transparent;
  cursor: col-resize;
}

.figma-chat-resize-handle:hover {
  background: #d8d8d8;
}

.figma-chat-resize-handle:hover::before {
  background: rgba(0, 0, 0, 0.3);
  height: 48px;
}

.figma-chat-body {
  flex: 1;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #ffffff;
}

/* ---- Bottom Drawer ---- */
.figma-bottom-drawer {
  position: absolute;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 20;
  height: 0;
  overflow: hidden;
  transition: height 0.2s ease;
  border-top: 1px solid #eaeaea;
  background: #f5f5f5;
  box-shadow: 0 -12px 28px rgba(17, 24, 39, 0.08);
}

.figma-bottom-drawer--open {
  height: 190px;
}

/* ---- Activity Bar (used by slot content) ---- */
:deep(.figma-activity-nav) {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: space-between;
  height: 100%;
  width: 100%;
  padding: 8px 0;
}

:deep(.figma-activity-top),
:deep(.figma-activity-bottom) {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
}

:deep(.figma-activity-btn) {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border: none;
  border-radius: 12px;
  background: transparent;
  color: #444;
  cursor: pointer;
  transition: background-color 0.14s ease, color 0.14s ease;
}

:deep(.figma-activity-btn:hover) {
  background: #e8e8e8;
  color: #333;
}

:deep(.figma-activity-btn--active) {
  color: #333;
}

:deep(.figma-activity-btn--active::before) {
  content: "";
  position: absolute;
  left: 0;
  top: 7px;
  width: 2px;
  height: 24px;
  border-radius: 0 999px 999px 0;
  background: #333;
}

:deep(.figma-activity-icon) {
  width: 20px;
  height: 20px;
}
.figma-sidebar-toggle-floating {
  position: absolute;
  top: 5px;
  z-index: 40;
  transition: left 0.25s ease;
}
.figma-sidebar-toggle-floating.is-resizing {
  transition: none !important;
}
.figma-sidebar-toggle-floating--right {
  right: 8px;
  top: 4px;
}
.figma-icon-btn-floating-open {
  width: 24px;
  height: 24px;
}

/* ---- MIMO Test Agent Idle Egg Character ---- */
.figma-robot-agent {
  pointer-events: auto;
  user-select: none;
  touch-action: none;
  cursor: grab;
  transform: translate3d(0, 0, 0);
  opacity: 0.85;
}

.figma-robot-agent.is-dragging {
  cursor: grabbing;
}

.figma-robot-instructions {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}

.robot-dir-wrap {
  width: 100%;
  height: 100%;
  transition: transform 0.2s ease;
}

.robot-dir-wrap.facing-left {
  transform: scaleX(-1);
}

.robot-dir-wrap.facing-right {
  transform: scaleX(1);
}

.robot-dir-wrap.facing-front {
  transform: scaleX(1);
}

.robot-squash-wrap {
  width: 100%;
  height: 100%;
  transform-origin: 50% 100%;
  transition: transform 0.15s ease-out;
}

/* Squash/Stretch shapes depending on state */
.robot-squash-wrap.state-charging,
.robot-squash-wrap.state-exiting-charge,
.robot-squash-wrap.state-landing {
  transform: scale(1.15, 0.7);
}

.robot-squash-wrap.state-jumping-up {
  transform: scale(0.9, 1.15);
}

.robot-squash-wrap.state-jumping-down {
  transform: scale(0.9, 1.15);
}

.robot-squash-wrap.state-exiting-fly {
  transform: scale(0.85, 1.25);
}

/* Idle/spawning/waving breathing animation */
.robot-squash-wrap.state-idle,
.robot-squash-wrap.state-spawning,
.robot-squash-wrap.state-waving {
  animation: robot-breath 3s infinite ease-in-out;
}

@keyframes robot-breath {
  0%, 100% {
    transform: scale(1, 1);
  }
  50% {
    transform: scale(1.02, 1.05); /* 5% vertical stretch */
  }
}

.robot-svg {
  display: block;
}

.robot-toggle-svg {
  display: block;
  width: 24px;
  height: 28px;
}

/* Eye glowing/breathing animation */
.robot-eye {
  animation: robot-eye-glow 2s infinite ease-in-out;
}

@keyframes robot-eye-glow {
  0%, 100% {
    opacity: 0.5;
  }
  50% {
    opacity: 1;
  }
}

/* Limbs styling & transitions */
.robot-arm-r {
  transform-origin: 20.5px 17px;
  transition: transform 0.2s ease;
}

.robot-arm-l {
  transform-origin: 3.5px 17px;
  transition: transform 0.2s ease;
}

.robot-leg-l {
  transform-origin: 10.25px 26.5px;
  transition: transform 0.2s ease;
}

.robot-leg-r {
  transform-origin: 13.75px 26.5px;
  transition: transform 0.2s ease;
}

.robot-head,
.robot-body {
  transition: transform 0.2s ease;
}

/* Waving state */
.state-waving .robot-arm-r {
  animation: robot-wave 0.15s infinite ease-in-out;
}

@keyframes robot-wave {
  0%, 100% {
    transform: rotate(-140deg);
  }
  50% {
    transform: rotate(-180deg);
  }
}

/* Walking state walk-cycle */
.state-walking .robot-leg-l {
  animation: robot-walk-leg 0.4s infinite ease-in-out;
}

.state-walking .robot-leg-r {
  animation: robot-walk-leg 0.4s infinite ease-in-out reverse;
}

.state-walking .robot-arm-l {
  animation: robot-walk-arm 0.4s infinite ease-in-out;
}

.state-walking .robot-arm-r {
  animation: robot-walk-arm 0.4s infinite ease-in-out reverse;
}

@keyframes robot-walk-leg {
  0%, 100% {
    transform: rotate(-25deg);
  }
  50% {
    transform: rotate(25deg);
  }
}

@keyframes robot-walk-arm {
  0%, 100% {
    transform: rotate(20deg);
  }
  50% {
    transform: rotate(-20deg);
  }
}

/* Sitting state */
.state-sitting .robot-head,
.state-sitting .robot-body {
  transform: translateY(3px);
}

.state-sitting .robot-leg-l {
  transform: translate(-1.5px, -2px) rotate(80deg);
}

.state-sitting .robot-leg-r {
  transform: translate(1.5px, -2px) rotate(-80deg);
}

.state-sitting .robot-arm-l {
  transform: rotate(20deg);
}

.state-sitting .robot-arm-r {
  transform: rotate(-20deg);
}

/* Flipping (Backflip) state */
.robot-squash-wrap.state-flipping {
  animation: robot-backflip 0.8s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 快速抖动：短促左右摆动，作为随机行为的一种轻量反馈。 */
.robot-squash-wrap.state-shaking {
  animation: robot-shake 0.42s ease-in-out;
}

@keyframes robot-shake {
  0%, 100% { transform: translateX(0) rotate(0deg); }
  25% { transform: translateX(-2px) rotate(-6deg); }
  75% { transform: translateX(2px) rotate(6deg); }
}

/* 旋转庆祝：完整旋转一圈后回到自然站姿。 */
.robot-squash-wrap.state-celebrating {
  animation: robot-celebrate 0.9s cubic-bezier(0.4, 0, 0.2, 1);
}

@keyframes robot-celebrate {
  0% { transform: rotate(0deg) scale(1); }
  45% { transform: rotate(180deg) scale(1.08); }
  100% { transform: rotate(360deg) scale(1); }
}

@keyframes robot-backflip {
  0% {
    transform: scale(0.9, 1.15) rotate(0deg);
  }
  50% {
    transform: scale(1.1, 0.85) rotate(-180deg);
  }
  100% {
    transform: scale(1, 1) rotate(-360deg);
  }
}

/* Hanging (upside down) state */
.robot-squash-wrap.state-hanging {
  animation: robot-hang-swing 4s infinite ease-in-out;
}

@keyframes robot-hang-swing {
  0%, 100% {
    transform: rotate(180deg);
  }
  50% {
    transform: rotate(175deg) translateX(-1px);
  }
}

.state-hanging .robot-arm-l,
.state-hanging .robot-arm-r {
  /* Let arms hang loosely straight down relative to the viewport floor */
  transform: rotate(160deg);
}
</style>
