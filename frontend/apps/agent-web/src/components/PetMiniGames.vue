<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from "vue";
import { Bomb, Gamepad2, RotateCcw, X } from "lucide-vue-next";

type GameKind = "tetris" | "minesweeper";
type TetrisCell = string | null;
type TetrisPiece = {
  matrix: number[][];
  color: string;
  x: number;
  y: number;
};
type MineCell = {
  mine: boolean;
  revealed: boolean;
  flagged: boolean;
  nearby: number;
};

const emit = defineEmits<{ (event: "close"): void }>();
const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

const activeGame = ref<GameKind | null>(null);
const panel = ref<HTMLElement | null>(null);

const TETRIS_ROWS = 16;
const TETRIS_COLUMNS = 10;
const TETRIS_TICK_MS = 460;
const TETROMINOES = [
  { color: "cyan", matrix: [[1, 1, 1, 1]] },
  { color: "yellow", matrix: [[1, 1], [1, 1]] },
  { color: "violet", matrix: [[0, 1, 0], [1, 1, 1]] },
  { color: "blue", matrix: [[1, 0, 0], [1, 1, 1]] },
  { color: "orange", matrix: [[0, 0, 1], [1, 1, 1]] },
  { color: "green", matrix: [[0, 1, 1], [1, 1, 0]] },
  { color: "rose", matrix: [[1, 1, 0], [0, 1, 1]] },
] as const;

function emptyTetrisBoard(): TetrisCell[][] {
  return Array.from({ length: TETRIS_ROWS }, () => Array<TetrisCell>(TETRIS_COLUMNS).fill(null));
}

const tetrisBoard = ref<TetrisCell[][]>(emptyTetrisBoard());
const tetrisPiece = ref<TetrisPiece | null>(null);
const tetrisScore = ref(0);
const tetrisLines = ref(0);
const tetrisRunning = ref(false);
const tetrisGameOver = ref(false);
let tetrisTimer: ReturnType<typeof setInterval> | null = null;

function clearTetrisTimer() {
  if (tetrisTimer) clearInterval(tetrisTimer);
  tetrisTimer = null;
}

function startTetrisTimer() {
  clearTetrisTimer();
  if (tetrisRunning.value) tetrisTimer = setInterval(stepTetris, TETRIS_TICK_MS);
}

function createTetrisPiece(): TetrisPiece {
  const source = TETROMINOES[Math.floor(Math.random() * TETROMINOES.length)]!;
  return {
    matrix: source.matrix.map((row) => [...row]),
    color: source.color,
    x: Math.floor((TETRIS_COLUMNS - source.matrix[0]!.length) / 2),
    y: 0,
  };
}

function tetrisCollides(piece: TetrisPiece, x = piece.x, y = piece.y, matrix = piece.matrix): boolean {
  return matrix.some((row, rowIndex) => row.some((filled, columnIndex) => {
    if (!filled) return false;
    const boardX = x + columnIndex;
    const boardY = y + rowIndex;
    return boardX < 0
      || boardX >= TETRIS_COLUMNS
      || boardY >= TETRIS_ROWS
      || (boardY >= 0 && Boolean(tetrisBoard.value[boardY]?.[boardX]));
  }));
}

function spawnTetrisPiece() {
  const piece = createTetrisPiece();
  if (tetrisCollides(piece)) {
    tetrisPiece.value = null;
    tetrisRunning.value = false;
    tetrisGameOver.value = true;
    clearTetrisTimer();
    return;
  }
  tetrisPiece.value = piece;
}

function startTetris() {
  tetrisBoard.value = emptyTetrisBoard();
  tetrisScore.value = 0;
  tetrisLines.value = 0;
  tetrisGameOver.value = false;
  tetrisRunning.value = true;
  spawnTetrisPiece();
  startTetrisTimer();
}

function toggleTetrisPause() {
  if (tetrisGameOver.value || !tetrisPiece.value) {
    startTetris();
    return;
  }
  tetrisRunning.value = !tetrisRunning.value;
  startTetrisTimer();
}

function moveTetris(deltaX: number, deltaY: number): boolean {
  const piece = tetrisPiece.value;
  if (!piece || !tetrisRunning.value || tetrisCollides(piece, piece.x + deltaX, piece.y + deltaY)) return false;
  piece.x += deltaX;
  piece.y += deltaY;
  return true;
}

function rotatedMatrix(matrix: number[][]): number[][] {
  const height = matrix.length;
  const width = matrix[0]?.length ?? 0;
  return Array.from({ length: width }, (_, row) =>
    Array.from({ length: height }, (_, column) => matrix[height - 1 - column]![row] ?? 0)
  );
}

function rotateTetris() {
  const piece = tetrisPiece.value;
  if (!piece || !tetrisRunning.value) return;
  const nextMatrix = rotatedMatrix(piece.matrix);
  // 靠墙旋转时尝试一格 wall kick，保持迷你棋盘上的操作手感。
  for (const offset of [0, -1, 1, -2, 2]) {
    if (!tetrisCollides(piece, piece.x + offset, piece.y, nextMatrix)) {
      piece.x += offset;
      piece.matrix = nextMatrix;
      return;
    }
  }
}

function lockTetrisPiece() {
  const piece = tetrisPiece.value;
  if (!piece) return;
  const nextBoard = tetrisBoard.value.map((row) => [...row]);
  piece.matrix.forEach((row, rowIndex) => row.forEach((filled, columnIndex) => {
    if (!filled) return;
    const boardY = piece.y + rowIndex;
    const boardX = piece.x + columnIndex;
    if (boardY >= 0 && boardY < TETRIS_ROWS) nextBoard[boardY]![boardX] = piece.color;
  }));
  const remaining = nextBoard.filter((row) => row.some((cell) => !cell));
  const cleared = TETRIS_ROWS - remaining.length;
  tetrisBoard.value = [
    ...Array.from({ length: cleared }, () => Array<TetrisCell>(TETRIS_COLUMNS).fill(null)),
    ...remaining,
  ];
  if (cleared > 0) {
    tetrisLines.value += cleared;
    tetrisScore.value += [0, 100, 300, 500, 800][cleared] ?? cleared * 200;
  }
  spawnTetrisPiece();
}

function stepTetris() {
  if (!tetrisRunning.value || !tetrisPiece.value) return;
  if (!moveTetris(0, 1)) lockTetrisPiece();
}

function hardDropTetris() {
  if (!tetrisRunning.value || !tetrisPiece.value) return;
  let distance = 0;
  while (moveTetris(0, 1)) distance += 1;
  tetrisScore.value += distance * 2;
  lockTetrisPiece();
}

const visibleTetrisBoard = computed(() => {
  const board = tetrisBoard.value.map((row) => [...row]);
  const piece = tetrisPiece.value;
  if (!piece) return board;
  piece.matrix.forEach((row, rowIndex) => row.forEach((filled, columnIndex) => {
    const boardY = piece.y + rowIndex;
    const boardX = piece.x + columnIndex;
    if (filled && boardY >= 0 && boardY < TETRIS_ROWS && boardX >= 0 && boardX < TETRIS_COLUMNS) {
      board[boardY]![boardX] = piece.color;
    }
  }));
  return board;
});

const tetrisStatus = computed(() => {
  if (tetrisGameOver.value) return "碰到顶部了";
  return tetrisRunning.value ? "下落中" : "已暂停";
});

const MINE_ROWS = 8;
const MINE_COLUMNS = 8;
const MINE_COUNT = 10;

function emptyMineBoard(): MineCell[] {
  return Array.from({ length: MINE_ROWS * MINE_COLUMNS }, () => ({
    mine: false,
    revealed: false,
    flagged: false,
    nearby: 0,
  }));
}

const mineBoard = ref<MineCell[]>(emptyMineBoard());
const minesInitialized = ref(false);
const mineStatus = ref<"ready" | "playing" | "won" | "lost">("ready");
const mineFlags = computed(() => mineBoard.value.filter((cell) => cell.flagged).length);

function mineNeighbors(index: number): number[] {
  const row = Math.floor(index / MINE_COLUMNS);
  const column = index % MINE_COLUMNS;
  const neighbors: number[] = [];
  for (let rowOffset = -1; rowOffset <= 1; rowOffset += 1) {
    for (let columnOffset = -1; columnOffset <= 1; columnOffset += 1) {
      if (rowOffset === 0 && columnOffset === 0) continue;
      const nextRow = row + rowOffset;
      const nextColumn = column + columnOffset;
      if (nextRow >= 0 && nextRow < MINE_ROWS && nextColumn >= 0 && nextColumn < MINE_COLUMNS) {
        neighbors.push(nextRow * MINE_COLUMNS + nextColumn);
      }
    }
  }
  return neighbors;
}

function initializeMines(firstIndex: number) {
  // 首次点击连同周围八格保持安全，让一局小游戏不会在第一步就结束。
  const excluded = new Set([firstIndex, ...mineNeighbors(firstIndex)]);
  const candidates = mineBoard.value.map((_, index) => index).filter((index) => !excluded.has(index));
  for (let index = candidates.length - 1; index > 0; index -= 1) {
    const swapIndex = Math.floor(Math.random() * (index + 1));
    [candidates[index], candidates[swapIndex]] = [candidates[swapIndex]!, candidates[index]!];
  }
  candidates.slice(0, MINE_COUNT).forEach((index) => {
    mineBoard.value[index]!.mine = true;
  });
  mineBoard.value.forEach((cell, index) => {
    cell.nearby = mineNeighbors(index).filter((neighbor) => mineBoard.value[neighbor]!.mine).length;
  });
  minesInitialized.value = true;
  mineStatus.value = "playing";
}

function revealMineCell(index: number) {
  if (mineStatus.value === "won" || mineStatus.value === "lost") return;
  if (!minesInitialized.value) initializeMines(index);
  const target = mineBoard.value[index];
  if (!target || target.flagged || target.revealed) return;
  if (target.mine) {
    target.revealed = true;
    mineBoard.value.forEach((cell) => {
      if (cell.mine) cell.revealed = true;
    });
    mineStatus.value = "lost";
    return;
  }

  // 空白区使用队列展开，避免递归深度随棋盘布局变化。
  const queue = [index];
  const visited = new Set<number>();
  while (queue.length > 0) {
    const currentIndex = queue.shift()!;
    if (visited.has(currentIndex)) continue;
    visited.add(currentIndex);
    const cell = mineBoard.value[currentIndex]!;
    if (cell.flagged || cell.mine) continue;
    cell.revealed = true;
    if (cell.nearby === 0) {
      mineNeighbors(currentIndex).forEach((neighbor) => {
        if (!visited.has(neighbor)) queue.push(neighbor);
      });
    }
  }
  const revealedSafeCells = mineBoard.value.filter((cell) => cell.revealed && !cell.mine).length;
  if (revealedSafeCells === MINE_ROWS * MINE_COLUMNS - MINE_COUNT) mineStatus.value = "won";
}

function toggleMineFlag(index: number) {
  if (mineStatus.value === "won" || mineStatus.value === "lost") return;
  const cell = mineBoard.value[index];
  if (!cell || cell.revealed) return;
  cell.flagged = !cell.flagged;
}

function resetMines() {
  mineBoard.value = emptyMineBoard();
  minesInitialized.value = false;
  mineStatus.value = "ready";
}

const mineStatusText = computed(() => ({
  ready: "先翻一格，第一步安全",
  playing: "找出全部 10 颗雷",
  won: "清场成功",
  lost: "踩雷了，再来一局",
}[mineStatus.value]));

function mineCellLabel(cell: MineCell, index: number): string {
  const row = Math.floor(index / MINE_COLUMNS) + 1;
  const column = index % MINE_COLUMNS + 1;
  if (cell.flagged) return `第 ${row} 行第 ${column} 列，已插旗`;
  if (!cell.revealed) return `第 ${row} 行第 ${column} 列，未翻开`;
  if (cell.mine) return `第 ${row} 行第 ${column} 列，地雷`;
  return `第 ${row} 行第 ${column} 列，周围 ${cell.nearby} 颗雷`;
}

function selectGame(game: GameKind) {
  activeGame.value = game;
  if (game === "tetris" && !tetrisPiece.value) startTetris();
  void nextTick(() => panel.value?.focus());
}

function onPanelKeydown(event: KeyboardEvent) {
  if (activeGame.value !== "tetris") return;
  const handledKeys = ["ArrowLeft", "ArrowRight", "ArrowDown", "ArrowUp", " ", "p", "P"];
  if (!handledKeys.includes(event.key)) return;
  event.preventDefault();
  if (event.key === "ArrowLeft") moveTetris(-1, 0);
  if (event.key === "ArrowRight") moveTetris(1, 0);
  if (event.key === "ArrowDown") stepTetris();
  if (event.key === "ArrowUp") rotateTetris();
  if (event.key === " ") hardDropTetris();
  if (event.key.toLowerCase() === "p") toggleTetrisPause();
}

function closePanel() {
  clearTetrisTimer();
  emit("close");
}

onBeforeUnmount(clearTetrisTimer);
</script>

<template>
  <section
    ref="panel"
    class="pet-game-panel"
    :class="{ 'is-embedded': props.embedded }"
    data-testid="pet-mini-games"
    :role="props.embedded ? 'group' : 'dialog'"
    :aria-labelledby="props.embedded ? undefined : 'pet-game-title'"
    tabindex="-1"
    @keydown="onPanelKeydown"
    @pointerdown.stop
    @click.stop
  >
    <header v-if="!props.embedded" class="pet-game-header">
      <div class="pet-game-heading">
        <span class="pet-game-mark" aria-hidden="true"><Gamepad2 :size="15" /></span>
        <div>
          <strong id="pet-game-title">MIMO 游乐舱</strong>
          <span>{{ activeGame ? "休息两分钟，再继续工作" : "选一个小游戏" }}</span>
        </div>
      </div>
      <button type="button" class="pet-game-icon-button" aria-label="关闭小宠物游戏" @click="closePanel">
        <X :size="15" />
      </button>
    </header>

    <div v-if="!activeGame" class="pet-game-picker">
      <button type="button" class="pet-game-choice is-tetris" data-testid="pet-game-open-tetris" @click="selectGame('tetris')">
        <span class="pet-game-choice-art tetris-choice-art" aria-hidden="true">
          <i v-for="index in 8" :key="index" />
        </span>
        <span><strong>俄罗斯方块</strong><small>方向键移动 · 空格直落</small></span>
      </button>
      <button type="button" class="pet-game-choice is-mines" data-testid="pet-game-open-minesweeper" @click="selectGame('minesweeper')">
        <span class="pet-game-choice-art mine-choice-art" aria-hidden="true"><Bomb :size="23" /></span>
        <span><strong>扫雷</strong><small>左键翻开 · 右键插旗</small></span>
      </button>
    </div>

    <template v-else>
      <nav class="pet-game-tabs" aria-label="小游戏切换">
        <button type="button" :class="{ 'is-active': activeGame === 'tetris' }" @click="selectGame('tetris')">俄罗斯方块</button>
        <button type="button" :class="{ 'is-active': activeGame === 'minesweeper' }" @click="selectGame('minesweeper')">扫雷</button>
      </nav>

      <div v-if="activeGame === 'tetris'" class="pet-tetris" data-testid="pet-tetris">
        <div class="pet-game-status-row">
          <span>{{ tetrisStatus }}</span>
          <span>分数 {{ tetrisScore }} · 消行 {{ tetrisLines }}</span>
        </div>
        <div class="pet-tetris-board" role="grid" aria-label="俄罗斯方块棋盘">
          <template v-for="(row, rowIndex) in visibleTetrisBoard" :key="rowIndex">
            <span
              v-for="(cell, columnIndex) in row"
              :key="`${rowIndex}-${columnIndex}`"
              class="pet-tetris-cell"
              :class="cell && `is-${cell}`"
              role="gridcell"
            />
          </template>
        </div>
        <div class="pet-tetris-controls" aria-label="俄罗斯方块操作">
          <button type="button" aria-label="左移" @click="moveTetris(-1, 0)">←</button>
          <button type="button" aria-label="旋转" @click="rotateTetris">↻</button>
          <button type="button" aria-label="右移" @click="moveTetris(1, 0)">→</button>
          <button type="button" aria-label="加速下落" @click="stepTetris">↓</button>
          <button type="button" class="is-wide" aria-label="直接落下" @click="hardDropTetris">直落</button>
          <button type="button" class="is-wide" @click="toggleTetrisPause">{{ tetrisRunning ? "暂停" : tetrisGameOver ? "重开" : "继续" }}</button>
        </div>
      </div>

      <div v-else class="pet-mines" data-testid="pet-minesweeper">
        <div class="pet-game-status-row">
          <span>{{ mineStatusText }}</span>
          <span>旗 {{ mineFlags }}/{{ MINE_COUNT }}</span>
          <button type="button" aria-label="重开扫雷" @click="resetMines"><RotateCcw :size="13" /></button>
        </div>
        <div class="pet-mine-board" role="grid" aria-label="扫雷棋盘">
          <button
            v-for="(cell, index) in mineBoard"
            :key="index"
            type="button"
            class="pet-mine-cell"
            :class="{
              'is-revealed': cell.revealed,
              'is-mine': cell.revealed && cell.mine,
              'is-flagged': cell.flagged,
            }"
            :data-nearby="cell.revealed && !cell.mine ? cell.nearby : undefined"
            :aria-label="mineCellLabel(cell, index)"
            role="gridcell"
            @click="revealMineCell(index)"
            @contextmenu.prevent="toggleMineFlag(index)"
          >
            <span v-if="cell.flagged">⚑</span>
            <span v-else-if="cell.revealed && cell.mine">✹</span>
            <span v-else-if="cell.revealed && cell.nearby">{{ cell.nearby }}</span>
          </button>
        </div>
      </div>
    </template>
  </section>
</template>

<style scoped>
.pet-game-panel {
  position: fixed;
  z-index: 10004;
  width: min(320px, calc(100vw - 16px));
  max-height: calc(100vh - 16px);
  box-sizing: border-box;
  overflow: auto;
  padding: 12px;
  border: 1px solid #d8e0e8;
  border-radius: 15px;
  outline: none;
  background: rgba(249, 251, 252, 0.98);
  box-shadow: 0 18px 42px rgba(39, 56, 75, 0.2), 0 3px 10px rgba(39, 56, 75, 0.08);
  color: #27384b;
  font-family: var(--ta-font-sans, "Noto Sans SC", "PingFang SC", sans-serif);
}

.pet-game-panel.is-embedded {
  position: static;
  width: 100%;
  max-height: none;
  overflow: visible;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: transparent;
  box-shadow: none;
}

.pet-game-header,
.pet-game-heading,
.pet-game-status-row,
.pet-game-tabs,
.pet-tetris-controls {
  display: flex;
  align-items: center;
}

.pet-game-header {
  justify-content: space-between;
  gap: 10px;
}

.pet-game-heading {
  gap: 8px;
}

.pet-game-heading > div {
  display: flex;
  flex-direction: column;
  gap: 1px;
}

.pet-game-heading strong {
  font-size: 13px;
  line-height: 18px;
}

.pet-game-heading span:not(.pet-game-mark) {
  color: #84919f;
  font-size: 10px;
  line-height: 14px;
}

.pet-game-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 27px;
  height: 27px;
  border-radius: 10px;
  background: linear-gradient(145deg, #e7f7f5, #eeeafd);
  color: #536e91;
}

.pet-game-icon-button,
.pet-game-status-row button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 0;
  background: transparent;
  color: #8793a0;
  cursor: pointer;
}

.pet-game-icon-button {
  width: 26px;
  height: 26px;
  border-radius: 7px;
}

.pet-game-icon-button:hover,
.pet-game-icon-button:focus-visible,
.pet-game-status-row button:hover,
.pet-game-status-row button:focus-visible {
  outline: none;
  background: #edf1f4;
  color: #354d66;
}

.pet-game-picker {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 9px;
  margin-top: 12px;
}

.pet-game-choice {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 9px;
  padding: 10px;
  border: 1px solid #e0e6eb;
  border-radius: 12px;
  background: #fff;
  color: #34495d;
  cursor: pointer;
  text-align: left;
}

.pet-game-choice:hover,
.pet-game-choice:focus-visible {
  border-color: #aebdcc;
  outline: none;
  transform: translateY(-1px);
}

.pet-game-choice > span:last-child {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 2px;
}

.pet-game-choice strong {
  font-size: 12px;
}

.pet-game-choice small {
  color: #8995a1;
  font-size: 9px;
  line-height: 13px;
}

.pet-game-choice-art {
  height: 48px;
  border-radius: 9px;
}

.tetris-choice-art {
  display: grid;
  grid-template-columns: repeat(4, 11px);
  grid-template-rows: repeat(3, 11px);
  align-content: center;
  justify-content: center;
  gap: 2px;
  background: #eef7fa;
}

.tetris-choice-art i {
  border-radius: 2px;
  background: #5aa9a6;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.45);
}

.tetris-choice-art i:nth-child(1),
.tetris-choice-art i:nth-child(8) {
  visibility: hidden;
}

.tetris-choice-art i:nth-child(n+5) {
  background: #7c6bb5;
}

.mine-choice-art {
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f3eff9;
  color: #7c6bb5;
}

.pet-game-tabs {
  gap: 4px;
  margin: 11px 0 9px;
  padding: 3px;
  border-radius: 9px;
  background: #edf1f4;
}

.pet-game-tabs button {
  flex: 1;
  height: 25px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  color: #73808d;
  cursor: pointer;
  font-size: 10px;
}

.pet-game-tabs button.is-active {
  background: #fff;
  box-shadow: 0 1px 3px rgba(39, 56, 75, 0.1);
  color: #33495d;
  font-weight: 650;
}

.pet-game-status-row {
  min-height: 25px;
  justify-content: space-between;
  gap: 7px;
  margin-bottom: 7px;
  color: #73808d;
  font-size: 10px;
}

.pet-game-status-row span:first-child {
  color: #3b5369;
  font-weight: 600;
}

.pet-game-status-row button {
  width: 24px;
  height: 24px;
  margin-left: auto;
  border-radius: 6px;
}

.pet-tetris-board {
  display: grid;
  width: fit-content;
  margin: 0 auto;
  grid-template-columns: repeat(10, 14px);
  grid-template-rows: repeat(16, 14px);
  gap: 1px;
  padding: 6px;
  border: 1px solid #27384b;
  border-radius: 9px;
  background: #1d2a38;
  box-shadow: inset 0 0 18px rgba(5, 15, 24, 0.45);
}

.pet-tetris-cell {
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.045);
}

.pet-tetris-cell[class*="is-"] { box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.35); }
.pet-tetris-cell.is-cyan { background: #61c8c2; }
.pet-tetris-cell.is-yellow { background: #e2c66d; }
.pet-tetris-cell.is-violet { background: #8a76bd; }
.pet-tetris-cell.is-blue { background: #5f86b4; }
.pet-tetris-cell.is-orange { background: #d99361; }
.pet-tetris-cell.is-green { background: #72ad83; }
.pet-tetris-cell.is-rose { background: #cf7684; }

.pet-tetris-controls {
  width: 238px;
  flex-wrap: wrap;
  justify-content: center;
  gap: 5px;
  margin: 9px auto 0;
}

.pet-tetris-controls button {
  width: 34px;
  height: 27px;
  border: 1px solid #d9e0e6;
  border-radius: 7px;
  background: #fff;
  color: #3d556b;
  cursor: pointer;
  font-size: 12px;
}

.pet-tetris-controls button.is-wide {
  width: 50px;
  font-size: 10px;
}

.pet-tetris-controls button:hover,
.pet-tetris-controls button:focus-visible {
  border-color: #91a7ba;
  outline: none;
  background: #f3f7f9;
}

.pet-mine-board {
  display: grid;
  width: fit-content;
  margin: 0 auto 3px;
  grid-template-columns: repeat(8, 26px);
  grid-template-rows: repeat(8, 26px);
  gap: 2px;
  padding: 6px;
  border: 1px solid #d6dee5;
  border-radius: 10px;
  background: #e8edf1;
}

.pet-mine-cell {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 26px;
  height: 26px;
  padding: 0;
  border: 1px solid #cbd5dd;
  border-radius: 5px;
  background: linear-gradient(145deg, #fff, #edf2f5);
  color: #415b72;
  cursor: pointer;
  font-size: 12px;
  font-weight: 700;
}

.pet-mine-cell:hover:not(.is-revealed),
.pet-mine-cell:focus-visible:not(.is-revealed) {
  border-color: #7897ae;
  outline: none;
}

.pet-mine-cell.is-revealed {
  border-color: transparent;
  background: #f9fbfc;
  cursor: default;
}

.pet-mine-cell.is-flagged { color: #7c6bb5; }
.pet-mine-cell.is-mine { background: #f6dedc; color: #c45d57; }
.pet-mine-cell[data-nearby="1"] { color: #3974a8; }
.pet-mine-cell[data-nearby="2"] { color: #4c8b62; }
.pet-mine-cell[data-nearby="3"] { color: #b75c55; }
.pet-mine-cell[data-nearby="4"],
.pet-mine-cell[data-nearby="5"],
.pet-mine-cell[data-nearby="6"],
.pet-mine-cell[data-nearby="7"],
.pet-mine-cell[data-nearby="8"] { color: #725f99; }

@media (prefers-reduced-motion: reduce) {
  .pet-game-choice { transition: none; }
  .pet-game-choice:hover,
  .pet-game-choice:focus-visible { transform: none; }
}
</style>
