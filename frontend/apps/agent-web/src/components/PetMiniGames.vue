<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from "vue";
import { Bomb, Gamepad2, RotateCcw, X } from "lucide-vue-next";
import PetGoldMinerGame from "./PetGoldMinerGame.vue";

type GameKind = "tetris" | "minesweeper" | "sudoku" | "snake" | "pinball" | "gold-miner";
type GoldMinerExpose = {
  dropHook: () => void;
  togglePause: () => void;
  resetGame: () => void;
};
type TetrisCell = string | null;
type TetrisShape = {
  matrix: number[][];
  color: string;
};
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
type SudokuCell = {
  value: number;
  given: boolean;
  error: boolean;
};
type SnakePoint = { x: number; y: number };
type PinballPoint = { x: number; y: number };
type PinballBall = PinballPoint & {
  id: number;
  vx: number;
  vy: number;
  previousX: number;
  previousY: number;
  inLaunchLane: boolean;
  cooldowns: Record<string, number>;
};
type PinballBumper = PinballPoint & { radius: number; score: number; label: string; tone: "rose" | "teal" | "gold" };
type PinballImpact = PinballPoint & { id: number; tone: "signal" | "charge" | "jackpot" };

const emit = defineEmits<{ (event: "close"): void }>();
const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

const activeGame = ref<GameKind | null>(null);
const panel = ref<HTMLElement | null>(null);
const goldMinerGame = ref<GoldMinerExpose | null>(null);

const TETRIS_ROWS = 16;
const TETRIS_COLUMNS = 10;
const TETRIS_BASE_TICK_MS = 460;
const TETRIS_MIN_TICK_MS = 120;
const TETRIS_LINES_PER_LEVEL = 4;
const RANDOM_DIFFICULTY_LEVELS = 5;
const RANDOM_STARTING_DIFFICULTY_LEVELS = 3;

function randomDifficultyLevel(max = RANDOM_DIFFICULTY_LEVELS): number {
  return 1 + Math.floor(Math.random() * max);
}

function randomDifficultyIncrease(): number {
  return 1 + Math.floor(Math.random() * 2);
}

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
const tetrisNextPiece = ref<TetrisShape | null>(null);
const tetrisDifficultyLevel = ref(1);
let tetrisBag: number[] = [];
let tetrisTimer: ReturnType<typeof setInterval> | null = null;

const tetrisLevel = computed(() => Math.min(10, 1 + Math.floor(tetrisLines.value / TETRIS_LINES_PER_LEVEL)));
const tetrisTickMs = computed(() => Math.max(
  TETRIS_MIN_TICK_MS,
  TETRIS_BASE_TICK_MS - (tetrisLevel.value - 1) * 45 - (tetrisDifficultyLevel.value - 1) * 22,
));
const tetrisNextBoard = computed(() => tetrisNextPiece.value?.matrix ?? []);

function clearTetrisTimer() {
  if (tetrisTimer) clearInterval(tetrisTimer);
  tetrisTimer = null;
}

function startTetrisTimer() {
  clearTetrisTimer();
  if (tetrisRunning.value) tetrisTimer = setInterval(stepTetris, tetrisTickMs.value);
}

function createTetrisShape(): TetrisShape {
  if (tetrisBag.length === 0) {
    tetrisBag = Array.from({ length: TETROMINOES.length }, (_, index) => index);
    // 采用七袋洗牌：一袋内七种方块各出现一次，避免纯随机导致某种关键块长期缺席。
    for (let index = tetrisBag.length - 1; index > 0; index -= 1) {
      const swapIndex = Math.floor(Math.random() * (index + 1));
      [tetrisBag[index], tetrisBag[swapIndex]] = [tetrisBag[swapIndex]!, tetrisBag[index]!];
    }
  }
  const source = TETROMINOES[tetrisBag.pop()!]!;
  return {
    matrix: source.matrix.map((row) => [...row]),
    color: source.color,
  };
}

function createTetrisPiece(shape: TetrisShape): TetrisPiece {
  return {
    matrix: shape.matrix.map((row) => [...row]),
    color: shape.color,
    x: Math.floor((TETRIS_COLUMNS - shape.matrix[0]!.length) / 2),
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
  const shape = tetrisNextPiece.value ?? createTetrisShape();
  tetrisNextPiece.value = createTetrisShape();
  const piece = createTetrisPiece(shape);
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
  tetrisDifficultyLevel.value = randomDifficultyLevel(RANDOM_STARTING_DIFFICULTY_LEVELS);
  tetrisGameOver.value = false;
  tetrisBag = [];
  tetrisNextPiece.value = createTetrisShape();
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
    const previousLevel = tetrisLevel.value;
    tetrisLines.value += cleared;
    tetrisScore.value += [0, 100, 300, 500, 800][cleared] ?? cleared * 200;
    if (tetrisLevel.value > previousLevel) {
      tetrisDifficultyLevel.value = Math.min(
        RANDOM_DIFFICULTY_LEVELS,
        tetrisDifficultyLevel.value + randomDifficultyIncrease(),
      );
    }
  }
  spawnTetrisPiece();
  if (cleared > 0) startTetrisTimer();
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
const MINE_BASE_COUNT = 10;
const MINE_MAX_COUNT = 18;

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
const mineLevel = ref(randomDifficultyLevel());
const mineCount = computed(() => Math.min(MINE_MAX_COUNT, MINE_BASE_COUNT + (mineLevel.value - 1) * 2));
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
  candidates.slice(0, mineCount.value).forEach((index) => {
    mineBoard.value[index]!.mine = true;
  });
  mineBoard.value.forEach((cell, index) => {
    cell.nearby = mineNeighbors(index).filter((neighbor) => mineBoard.value[neighbor]!.mine).length;
  });
  minesInitialized.value = true;
  mineStatus.value = "playing";
}

function revealSafeMineRegion(index: number) {
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
}

function updateMineWinStatus() {
  const revealedSafeCells = mineBoard.value.filter((cell) => cell.revealed && !cell.mine).length;
  if (revealedSafeCells === MINE_ROWS * MINE_COLUMNS - mineCount.value) mineStatus.value = "won";
}

function revealAllMines() {
  mineBoard.value.forEach((cell) => {
    if (cell.mine) cell.revealed = true;
  });
  mineStatus.value = "lost";
}

function revealMineCell(index: number) {
  if (mineStatus.value === "won" || mineStatus.value === "lost") return;
  if (!minesInitialized.value) initializeMines(index);
  const target = mineBoard.value[index];
  if (!target || target.flagged || target.revealed) return;
  if (target.mine) {
    revealAllMines();
    return;
  }
  revealSafeMineRegion(index);
  updateMineWinStatus();
}

function chordMineCell(index: number) {
  if (mineStatus.value === "won" || mineStatus.value === "lost" || !minesInitialized.value) return;
  const target = mineBoard.value[index];
  if (!target?.revealed || target.mine || target.nearby === 0) return;
  const neighbors = mineNeighbors(index);
  const flaggedCount = neighbors.filter((neighbor) => mineBoard.value[neighbor]!.flagged).length;
  if (flaggedCount !== target.nearby) return;

  // 数字与旗子数匹配时展开其余邻格；若旗子标错，保留经典扫雷仍会踩雷的规则。
  const candidates = neighbors.filter((neighbor) => {
    const cell = mineBoard.value[neighbor]!;
    return !cell.flagged && !cell.revealed;
  });
  if (candidates.some((candidate) => mineBoard.value[candidate]!.mine)) {
    revealAllMines();
    return;
  }
  candidates.forEach(revealSafeMineRegion);
  updateMineWinStatus();
}

function toggleMineFlag(index: number) {
  if (mineStatus.value === "won" || mineStatus.value === "lost") return;
  const cell = mineBoard.value[index];
  if (!cell || cell.revealed) return;
  cell.flagged = !cell.flagged;
}

function resetMines() {
  // 每次重开随机抽取难度，避免连续失败后被固定在越来越高的难度。
  mineLevel.value = randomDifficultyLevel();
  mineBoard.value = emptyMineBoard();
  minesInitialized.value = false;
  mineStatus.value = "ready";
}

const mineStatusText = computed(() => ({
  ready: "先翻一格，第一步安全",
  playing: `找出全部 ${mineCount.value} 颗雷`,
  won: "清场成功",
  lost: "踩雷了，再来一局",
}[mineStatus.value]));

function mineCellLabel(cell: MineCell, index: number): string {
  const row = Math.floor(index / MINE_COLUMNS) + 1;
  const column = index % MINE_COLUMNS + 1;
  if (cell.flagged) return `第 ${row} 行第 ${column} 列，已插旗`;
  if (!cell.revealed) return `第 ${row} 行第 ${column} 列，未翻开`;
  if (cell.mine) return `第 ${row} 行第 ${column} 列，地雷`;
  const chordHint = cell.nearby > 0 ? "，双击可展开周围" : "";
  return `第 ${row} 行第 ${column} 列，周围 ${cell.nearby} 颗雷${chordHint}`;
}

const SUDOKU_SOLUTION = [
  5, 3, 4, 6, 7, 8, 9, 1, 2,
  6, 7, 2, 1, 9, 5, 3, 4, 8,
  1, 9, 8, 3, 4, 2, 5, 6, 7,
  8, 5, 9, 7, 6, 1, 4, 2, 3,
  4, 2, 6, 8, 5, 3, 7, 9, 1,
  7, 1, 3, 9, 2, 4, 8, 5, 6,
  9, 6, 1, 5, 3, 7, 2, 8, 4,
  2, 8, 7, 4, 1, 9, 6, 3, 5,
  3, 4, 5, 2, 8, 6, 1, 7, 9,
] as const;
const SUDOKU_PUZZLE = [
  5, 3, 0, 0, 7, 0, 0, 0, 0,
  6, 0, 0, 1, 9, 5, 0, 0, 0,
  0, 9, 8, 0, 0, 0, 0, 6, 0,
  8, 0, 0, 0, 6, 0, 0, 0, 3,
  4, 0, 0, 8, 0, 3, 0, 0, 1,
  7, 0, 0, 0, 2, 0, 0, 0, 6,
  0, 6, 0, 0, 0, 0, 2, 8, 0,
  0, 0, 0, 4, 1, 9, 0, 0, 5,
  0, 0, 0, 0, 8, 0, 0, 7, 9,
] as const;
const SUDOKU_EXTRA_GIVENS = [3, 5, 6, 7, 8, 10, 11, 15, 16, 17, 18, 21] as const;

const sudokuLevel = ref(randomDifficultyLevel());

function createSudokuBoard(level = sudokuLevel.value): SudokuCell[] {
  const extraGivenCount = Math.max(0, (RANDOM_DIFFICULTY_LEVELS - level) * 3);
  const extraGivenIndexes = new Set<number>(SUDOKU_EXTRA_GIVENS.slice(0, extraGivenCount));
  // 五档题面都建立在同一份唯一解题目上；低档只增加提示，避免删题后出现多解却只认固定答案。
  return SUDOKU_PUZZLE.map((value, index) => {
    const given = value > 0 || extraGivenIndexes.has(index);
    return { value: given ? SUDOKU_SOLUTION[index]! : 0, given, error: false };
  });
}

const sudokuBoard = ref<SudokuCell[]>(createSudokuBoard());
const sudokuSelectedIndex = ref<number | null>(null);
const sudokuStatus = ref<"ready" | "playing" | "won">("ready");
const sudokuRemaining = computed(() => sudokuBoard.value.filter((cell) => cell.value === 0).length);
const sudokuErrors = computed(() => sudokuBoard.value.filter((cell) => cell.error).length);
const sudokuStatusText = computed(() => {
  if (sudokuStatus.value === "won") return "九宫完成";
  if (sudokuErrors.value > 0) return `有 ${sudokuErrors.value} 格需要检查`;
  return sudokuStatus.value === "ready" ? "选一格开始填写" : "继续推理";
});

function selectSudokuCell(index: number) {
  const cell = sudokuBoard.value[index];
  if (!cell || cell.given || sudokuStatus.value === "won") return;
  sudokuSelectedIndex.value = index;
}

function setSudokuValue(value: number) {
  const index = sudokuSelectedIndex.value;
  if (index === null || sudokuStatus.value === "won") return;
  const cell = sudokuBoard.value[index];
  if (!cell || cell.given) return;
  cell.value = value;
  cell.error = value > 0 && value !== SUDOKU_SOLUTION[index];
  sudokuStatus.value = "playing";
  // 只有全部填写且每格都与解一致时才结束，错误数字不会被静默覆盖。
  if (sudokuBoard.value.every((candidate, cellIndex) => candidate.value === SUDOKU_SOLUTION[cellIndex])) {
    sudokuStatus.value = "won";
    sudokuSelectedIndex.value = null;
  }
}

function resetSudoku() {
  // 每次重开随机抽取题面提示数量，连续失败也不会线性升难。
  sudokuLevel.value = randomDifficultyLevel();
  sudokuBoard.value = createSudokuBoard();
  sudokuSelectedIndex.value = null;
  sudokuStatus.value = "ready";
}

function sudokuCellLabel(cell: SudokuCell, index: number): string {
  const row = Math.floor(index / 9) + 1;
  const column = index % 9 + 1;
  if (cell.given) return `第 ${row} 行第 ${column} 列，题目数字 ${cell.value}`;
  if (cell.value === 0) return `第 ${row} 行第 ${column} 列，待填写`;
  return `第 ${row} 行第 ${column} 列，填写数字 ${cell.value}${cell.error ? "，需要检查" : ""}`;
}

const SNAKE_SIZE = 12;
const SNAKE_BASE_TICK_MS = 190;
const SNAKE_MIN_TICK_MS = 75;
const SNAKE_SCORE_PER_LEVEL = 3;
const snakeBody = ref<SnakePoint[]>([]);
const snakeDirection = ref<SnakePoint>({ x: 1, y: 0 });
const snakeQueuedDirection = ref<SnakePoint>({ x: 1, y: 0 });
const snakeFood = ref<SnakePoint>({ x: 0, y: 0 });
const snakeScore = ref(0);
const snakeRunning = ref(false);
const snakeGameOver = ref(false);
const snakeDifficultyLevel = ref(1);
let snakeTimer: ReturnType<typeof setInterval> | null = null;

const snakeLevel = computed(() => Math.min(10, 1 + Math.floor(snakeScore.value / SNAKE_SCORE_PER_LEVEL)));
const snakeTickMs = computed(() => Math.max(
  SNAKE_MIN_TICK_MS,
  SNAKE_BASE_TICK_MS - (snakeLevel.value - 1) * 20 - (snakeDifficultyLevel.value - 1) * 12,
));

function clearSnakeTimer() {
  if (snakeTimer) clearInterval(snakeTimer);
  snakeTimer = null;
}

function nextSnakeFood(body: SnakePoint[]): SnakePoint {
  const candidates = Array.from({ length: SNAKE_SIZE * SNAKE_SIZE }, (_, index) => ({
    x: index % SNAKE_SIZE,
    y: Math.floor(index / SNAKE_SIZE),
  })).filter((candidate) => !body.some((part) => part.x === candidate.x && part.y === candidate.y));
  return candidates[Math.floor(Math.random() * candidates.length)] ?? { x: -1, y: -1 };
}

function startSnakeTimer() {
  clearSnakeTimer();
  if (snakeRunning.value) snakeTimer = setInterval(stepSnake, snakeTickMs.value);
}

function startSnake() {
  const initialBody = [{ x: 5, y: 6 }, { x: 4, y: 6 }, { x: 3, y: 6 }];
  snakeBody.value = initialBody;
  snakeDirection.value = { x: 1, y: 0 };
  snakeQueuedDirection.value = { x: 1, y: 0 };
  snakeFood.value = nextSnakeFood(initialBody);
  snakeScore.value = 0;
  snakeDifficultyLevel.value = randomDifficultyLevel(RANDOM_STARTING_DIFFICULTY_LEVELS);
  snakeGameOver.value = false;
  snakeRunning.value = true;
  startSnakeTimer();
}

function setSnakeDirection(x: number, y: number) {
  if (!snakeRunning.value) return;
  // 禁止直接反向，避免蛇头在同一 tick 内撞向第二节身体。
  if (snakeDirection.value.x + x === 0 && snakeDirection.value.y + y === 0) return;
  snakeQueuedDirection.value = { x, y };
}

function stepSnake() {
  if (!snakeRunning.value || snakeBody.value.length === 0) return;
  snakeDirection.value = snakeQueuedDirection.value;
  const head = snakeBody.value[0]!;
  const nextHead = { x: head.x + snakeDirection.value.x, y: head.y + snakeDirection.value.y };
  const eating = nextHead.x === snakeFood.value.x && nextHead.y === snakeFood.value.y;
  const collisionBody = eating ? snakeBody.value : snakeBody.value.slice(0, -1);
  const hitWall = nextHead.x < 0 || nextHead.x >= SNAKE_SIZE || nextHead.y < 0 || nextHead.y >= SNAKE_SIZE;
  const hitBody = collisionBody.some((part) => part.x === nextHead.x && part.y === nextHead.y);
  if (hitWall || hitBody) {
    snakeRunning.value = false;
    snakeGameOver.value = true;
    clearSnakeTimer();
    return;
  }
  const nextBody = [nextHead, ...snakeBody.value];
  if (eating) {
    const previousLevel = snakeLevel.value;
    snakeScore.value += 1;
    if (snakeLevel.value > previousLevel) {
      snakeDifficultyLevel.value = Math.min(
        RANDOM_DIFFICULTY_LEVELS,
        snakeDifficultyLevel.value + randomDifficultyIncrease(),
      );
    }
    snakeFood.value = nextSnakeFood(nextBody);
    startSnakeTimer();
  } else {
    nextBody.pop();
  }
  snakeBody.value = nextBody;
}

function toggleSnakePause() {
  if (snakeGameOver.value || snakeBody.value.length === 0) {
    startSnake();
    return;
  }
  snakeRunning.value = !snakeRunning.value;
  startSnakeTimer();
}

const visibleSnakeBoard = computed(() => {
  const board = Array<"head" | "body" | "food" | null>(SNAKE_SIZE * SNAKE_SIZE).fill(null);
  snakeBody.value.forEach((part, index) => {
    board[part.y * SNAKE_SIZE + part.x] = index === 0 ? "head" : "body";
  });
  if (snakeFood.value.x >= 0) board[snakeFood.value.y * SNAKE_SIZE + snakeFood.value.x] = "food";
  return board;
});

const snakeStatusText = computed(() => {
  if (snakeGameOver.value) return "撞到了，再来一局";
  return snakeRunning.value ? "正在觅食" : "已暂停";
});

const PINBALL_WIDTH = 316;
const PINBALL_HEIGHT = 420;
const PINBALL_BALL_RADIUS = 6;
const PINBALL_FRAME_MS = 16;
// 420px 高球台需要明显的向下坡度：静止球约 0.8 秒穿过半场，甜区发射仍可越过顶部判定线。
const PINBALL_GRAVITY = 680;
const PINBALL_MAX_SPEED = 950;
const PINBALL_LAUNCH_BASE_SPEED = 720;
const PINBALL_LAUNCH_CHARGE_SPEED = 2.2;
const PINBALL_AUTO_SERVE_SPEED = 820;
const PINBALL_LAUNCH_RAIL_X = 270;
const PINBALL_LAUNCH_GATE_X = PINBALL_LAUNCH_RAIL_X - PINBALL_BALL_RADIUS - 2;
const PINBALL_FLIPPER_LENGTH = 62;
const PINBALL_SKILL_SHOT_MIN = 52;
const PINBALL_SKILL_SHOT_MAX = 82;
const PINBALL_EXTRA_BALL_SCORE = 15_000;
const pinballBumpers: PinballBumper[] = [
  { x: 90, y: 150, radius: 19, score: 120, label: "SYNC", tone: "rose" },
  { x: 164, y: 132, radius: 21, score: 180, label: "GAIN", tone: "gold" },
  { x: 225, y: 169, radius: 18, score: 240, label: "BOOST", tone: "teal" },
];
const pinballRolloverLanes = [
  { id: "mimo-m1", x: 52, label: "M" },
  { id: "mimo-i", x: 104, label: "I" },
  { id: "mimo-m2", x: 158, label: "M" },
  { id: "mimo-o", x: 218, label: "O" },
] as const;
const pinballDropTargetPositions = [112, 158, 204] as const;
const pinballLeftPivot: PinballPoint = { x: 96, y: 360 };
const pinballRightPivot: PinballPoint = { x: 220, y: 360 };
let pinballBallIdSequence = 0;

function createPinballBall(x = 289, y = 385, vx = 0, vy = 0): PinballBall {
  return {
    id: ++pinballBallIdSequence,
    x,
    y,
    vx,
    vy,
    previousX: x,
    previousY: y,
    inLaunchLane: x > PINBALL_LAUNCH_RAIL_X,
    cooldowns: {},
  };
}

const pinballBalls = ref<PinballBall[]>([createPinballBall()]);
const pinballScore = ref(0);
const pinballHighScore = ref(0);
const pinballLives = ref(3);
const pinballCharge = ref(0);
const pinballMultiplier = ref(1);
const pinballCombo = ref(0);
const pinballComboUntil = ref(0);
const pinballNow = ref(Date.now());
const pinballBallSaveUntil = ref(0);
const pinballRolloverLights = ref(pinballRolloverLanes.map(() => false));
const pinballDropTargets = ref([false, false, false]);
const pinballSpinnerTurns = ref(0);
const pinballJackpot = ref(3_000);
const pinballImpact = ref<PinballImpact | null>(null);
const pinballSkillShotPending = ref(false);
const pinballLastLaunchCharge = ref(0);
const pinballMultiballActive = ref(false);
const pinballExtraBallAwarded = ref(false);
const pinballTiltWarnings = ref(0);
const pinballLastNudgeAt = ref(0);
const pinballDifficultyLevel = ref(1);
const pinballCallout = ref("装球完成 · 对准技能发射区");
const pinballStatus = ref<"ready" | "running" | "paused" | "tilted" | "gameover">("ready");
const pinballCharging = ref(false);
const pinballLeftPressed = ref(false);
const pinballRightPressed = ref(false);
let pinballFrameTimer: ReturnType<typeof setInterval> | null = null;
let pinballChargeTimer: ReturnType<typeof setInterval> | null = null;
let pinballImpactSequence = 0;
let pinballRoundInitialized = false;

const pinballMissionReady = computed(() =>
  pinballRolloverLights.value.every(Boolean) && pinballDropTargets.value.every(Boolean)
);
const pinballMissionText = computed(() => {
  if (pinballMultiballActive.value) return `信号风暴 · ${pinballBalls.value.length} 球在线`;
  if (!pinballRolloverLights.value.every(Boolean)) return "连接 M / I / M / O 四座中继";
  if (!pinballDropTargets.value.every(Boolean)) return "中继上线 · 击落三枚放大器";
  return "JACKPOT 就绪 · 命中红色核心";
});
const pinballMissionProgress = computed(() => (
  pinballRolloverLights.value.filter(Boolean).length + pinballDropTargets.value.filter(Boolean).length
));
const pinballStatusText = computed(() => ({
  ready: "按住发射杆蓄力",
  running: pinballCallout.value,
  paused: "已暂停",
  tilted: "TILT · 挡板失效",
  gameover: "本局结束",
}[pinballStatus.value]));
const pinballBonus = computed(() => (
  pinballRolloverLights.value.filter(Boolean).length * 350
  + pinballDropTargets.value.filter(Boolean).length * 500
  + pinballSpinnerTurns.value * 20
) * pinballMultiplier.value);
const pinballBallSaveSeconds = computed(() => Math.max(0, Math.ceil(
  (pinballBallSaveUntil.value - pinballNow.value) / 1000
)));
const pinballGravity = computed(() => PINBALL_GRAVITY + (pinballDifficultyLevel.value - 1) * 35);
const pinballBallSaveDurationSeconds = computed(() => Math.max(5, 10 - pinballDifficultyLevel.value));
const pinballSkillShotMin = computed(() => PINBALL_SKILL_SHOT_MIN + (pinballDifficultyLevel.value - 1) * 3);
const pinballSkillShotMax = computed(() => PINBALL_SKILL_SHOT_MAX - (pinballDifficultyLevel.value - 1) * 2);
const pinballSkillZoneStyle = computed(() => ({
  top: `${50 - pinballSkillShotMax.value * 0.5}px`,
  height: `${(pinballSkillShotMax.value - pinballSkillShotMin.value) * 0.5}px`,
}));
const pinballDifficultyText = computed(() => (
  `随机档 ${pinballDifficultyLevel.value} · 重力 ${pinballGravity.value} · 救球 ${pinballBallSaveDurationSeconds.value}s`
));
const pinballComboVisible = computed(() => pinballCombo.value > 1 && pinballComboUntil.value > pinballNow.value);
const pinballLeftAngle = computed(() => pinballLeftPressed.value ? -26 : 18);
const pinballRightAngle = computed(() => pinballRightPressed.value ? -26 : 18);
const pinballLeftFlipperStyle = computed(() => ({ transform: `rotate(${pinballLeftAngle.value}deg)` }));
const pinballRightFlipperStyle = computed(() => ({ transform: `rotate(${-pinballRightAngle.value}deg)` }));
const pinballLauncherStyle = computed(() => ({ height: `${Math.max(8, pinballCharge.value * 0.58)}px` }));

function pinballBallStyle(ball: PinballBall) {
  return { left: `${ball.x}px`, top: `${ball.y}px` };
}

function clearPinballFrameTimer() {
  if (pinballFrameTimer) clearInterval(pinballFrameTimer);
  pinballFrameTimer = null;
}

function clearPinballChargeTimer() {
  if (pinballChargeTimer) clearInterval(pinballChargeTimer);
  pinballChargeTimer = null;
  pinballCharging.value = false;
}

function clearPinballTimers() {
  clearPinballFrameTimer();
  clearPinballChargeTimer();
  pinballLeftPressed.value = false;
  pinballRightPressed.value = false;
}

function resetPinballMission() {
  pinballRolloverLights.value = pinballRolloverLanes.map(() => false);
  pinballDropTargets.value = [false, false, false];
  pinballSpinnerTurns.value = 0;
}

function servePinballBall(callout?: string) {
  clearPinballTimers();
  pinballBalls.value = [createPinballBall()];
  pinballCharge.value = 0;
  pinballCombo.value = 0;
  pinballTiltWarnings.value = 0;
  pinballBallSaveUntil.value = 0;
  pinballSkillShotPending.value = false;
  pinballCallout.value = callout ?? `新球就位 · ${pinballBallSaveDurationSeconds.value} 秒救球`;
  pinballStatus.value = "ready";
}

function resetPinball() {
  pinballDifficultyLevel.value = randomDifficultyLevel(RANDOM_STARTING_DIFFICULTY_LEVELS);
  pinballRoundInitialized = true;
  pinballScore.value = 0;
  pinballLives.value = 3;
  pinballMultiplier.value = 1;
  pinballExtraBallAwarded.value = false;
  pinballMultiballActive.value = false;
  pinballJackpot.value = 3_000;
  pinballImpact.value = null;
  resetPinballMission();
  servePinballBall("装球完成 · 对准技能发射区");
}

function startPinballFrameTimer() {
  clearPinballFrameTimer();
  if (pinballStatus.value === "running" || pinballStatus.value === "tilted") {
    pinballFrameTimer = setInterval(stepPinball, PINBALL_FRAME_MS);
  }
}

function beginPinballCharge(event?: PointerEvent) {
  if (pinballStatus.value !== "ready" || pinballCharging.value) return;
  const target = event?.currentTarget as (HTMLElement & { setPointerCapture?: (pointerId: number) => void }) | null;
  if (target && typeof event?.pointerId === "number") target.setPointerCapture?.(event.pointerId);
  clearPinballChargeTimer();
  pinballCharge.value = Math.max(18, pinballCharge.value);
  pinballCharging.value = true;
  pinballChargeTimer = setInterval(() => {
    pinballCharge.value = Math.min(100, pinballCharge.value + 2);
  }, PINBALL_FRAME_MS);
}

function releasePinballCharge() {
  if (pinballStatus.value !== "ready" || !pinballCharging.value) return;
  const charge = Math.max(18, pinballCharge.value);
  clearPinballChargeTimer();
  const ball = pinballBalls.value[0] ?? createPinballBall();
  ball.vx = -8;
  ball.vy = -PINBALL_LAUNCH_BASE_SPEED - charge * PINBALL_LAUNCH_CHARGE_SPEED;
  pinballBalls.value = [ball];
  pinballLastLaunchCharge.value = charge;
  pinballSkillShotPending.value = true;
  pinballBallSaveUntil.value = Date.now() + pinballBallSaveDurationSeconds.value * 1000;
  pinballNow.value = Date.now();
  pinballCharge.value = 0;
  pinballCallout.value = "技能发射判定中";
  pinballStatus.value = "running";
  startPinballFrameTimer();
}

function cyclePinballRolloverLights(direction: "left" | "right") {
  const lights = [...pinballRolloverLights.value];
  pinballRolloverLights.value = direction === "left"
    ? [...lights.slice(1), lights[0]!]
    : [lights.at(-1)!, ...lights.slice(0, -1)];
}

function setPinballFlipper(side: "left" | "right", pressed: boolean) {
  if (pinballStatus.value === "tilted") return;
  const current = side === "left" ? pinballLeftPressed.value : pinballRightPressed.value;
  if (side === "left") pinballLeftPressed.value = pressed;
  else pinballRightPressed.value = pressed;
  // 真实弹球机允许用挡板键平移顶部已亮灯，给 MIMO 中继补灯留出主动操作空间。
  if (pressed && !current && pinballStatus.value === "running") cyclePinballRolloverLights(side);
}

function pinballFlipperTip(side: "left" | "right"): PinballPoint {
  const pivot = side === "left" ? pinballLeftPivot : pinballRightPivot;
  const angle = (side === "left" ? pinballLeftAngle.value : pinballRightAngle.value) * Math.PI / 180;
  return side === "left"
    ? { x: pivot.x + Math.cos(angle) * PINBALL_FLIPPER_LENGTH, y: pivot.y + Math.sin(angle) * PINBALL_FLIPPER_LENGTH }
    : { x: pivot.x - Math.cos(angle) * PINBALL_FLIPPER_LENGTH, y: pivot.y + Math.sin(angle) * PINBALL_FLIPPER_LENGTH };
}

function pinballHitAvailable(ball: PinballBall, key: string, cooldownMs = 300): boolean {
  const availableAt = ball.cooldowns[key] ?? 0;
  if (availableAt > pinballNow.value) return false;
  ball.cooldowns[key] = pinballNow.value + cooldownMs;
  return true;
}

function awardPinballScore(baseScore: number, callout?: string, useCombo = false) {
  if (pinballStatus.value !== "running") return;
  if (useCombo) {
    pinballCombo.value = pinballComboUntil.value > pinballNow.value
      ? Math.min(5, pinballCombo.value + 1)
      : 1;
    pinballComboUntil.value = pinballNow.value + 2600;
  }
  const comboMultiplier = useCombo ? Math.max(1, pinballCombo.value) : 1;
  const awarded = baseScore * pinballMultiplier.value * comboMultiplier;
  pinballScore.value += awarded;
  pinballHighScore.value = Math.max(pinballHighScore.value, pinballScore.value);
  if (callout) pinballCallout.value = `${callout} +${awarded}`;
  if (!pinballExtraBallAwarded.value && pinballScore.value >= PINBALL_EXTRA_BALL_SCORE) {
    pinballExtraBallAwarded.value = true;
    pinballLives.value += 1;
    pinballCallout.value = "达到 15000 分 · 奖励一球";
  }
}

function pulsePinballImpact(x: number, y: number, tone: PinballImpact["tone"] = "signal") {
  pinballImpact.value = { id: ++pinballImpactSequence, x, y, tone };
}

function chargePinballJackpot(amount: number) {
  pinballJackpot.value += amount;
}

function resolvePinballSegment(
  ball: PinballBall,
  start: PinballPoint,
  end: PinballPoint,
  padding: number,
  restitution: number,
  kick: PinballPoint = { x: 0, y: 0 },
): boolean {
  const segmentX = end.x - start.x;
  const segmentY = end.y - start.y;
  const segmentLengthSquared = segmentX * segmentX + segmentY * segmentY;
  const projection = segmentLengthSquared === 0 ? 0 : Math.max(0, Math.min(1,
    ((ball.x - start.x) * segmentX + (ball.y - start.y) * segmentY) / segmentLengthSquared,
  ));
  const closestX = start.x + projection * segmentX;
  const closestY = start.y + projection * segmentY;
  const distanceX = ball.x - closestX;
  const distanceY = ball.y - closestY;
  const distance = Math.hypot(distanceX, distanceY);
  const minimumDistance = PINBALL_BALL_RADIUS + padding;
  if (distance >= minimumDistance) return false;
  const normalX = distance > 0.001 ? distanceX / distance : 0;
  const normalY = distance > 0.001 ? distanceY / distance : -1;
  ball.x = closestX + normalX * minimumDistance;
  ball.y = closestY + normalY * minimumDistance;
  const approachSpeed = ball.vx * normalX + ball.vy * normalY;
  if (approachSpeed < 0) {
    ball.vx -= (1 + restitution) * approachSpeed * normalX;
    ball.vy -= (1 + restitution) * approachSpeed * normalY;
  }
  ball.vx += kick.x;
  ball.vy += kick.y;
  return true;
}

function resolvePinballCircle(ball: PinballBall, center: PinballPoint, radius: number, kick = 0): boolean {
  const deltaX = ball.x - center.x;
  const deltaY = ball.y - center.y;
  const distance = Math.hypot(deltaX, deltaY);
  const minimumDistance = PINBALL_BALL_RADIUS + radius;
  if (distance >= minimumDistance) return false;
  const normalX = distance > 0.001 ? deltaX / distance : 0;
  const normalY = distance > 0.001 ? deltaY / distance : -1;
  const approachSpeed = ball.vx * normalX + ball.vy * normalY;
  ball.x = center.x + normalX * minimumDistance;
  ball.y = center.y + normalY * minimumDistance;
  if (approachSpeed < 0) {
    ball.vx -= 1.82 * approachSpeed * normalX;
    ball.vy -= 1.82 * approachSpeed * normalY;
  }
  ball.vx += normalX * kick;
  ball.vy += normalY * kick;
  return true;
}

function resolvePinballBumper(ball: PinballBall, bumper: PinballBumper, index: number) {
  if (!resolvePinballCircle(ball, bumper, bumper.radius, 150)) return;
  if (pinballHitAvailable(ball, `bumper-${index}`, 220)) {
    awardPinballScore(bumper.score, `碰撞器 ${index + 1}`, true);
    chargePinballJackpot(80 + index * 20);
    pulsePinballImpact(bumper.x, bumper.y, bumper.tone === "rose" ? "jackpot" : "charge");
  }
}

function detectPinballFeatures(ball: PinballBall) {
  if (pinballSkillShotPending.value && ball.y < 68) {
    pinballSkillShotPending.value = false;
    if (pinballLastLaunchCharge.value >= pinballSkillShotMin.value && pinballLastLaunchCharge.value <= pinballSkillShotMax.value) {
      awardPinballScore(2500, "SKILL SHOT");
      pinballMultiplier.value = Math.min(5, pinballMultiplier.value + 1);
      chargePinballJackpot(500);
      pulsePinballImpact(ball.x, ball.y, "jackpot");
    } else {
      awardPinballScore(250, "发射入台");
      pulsePinballImpact(ball.x, ball.y, "signal");
    }
  }

  if (ball.previousY < 103 && ball.y >= 103) {
    const laneIndex = pinballRolloverLanes.findIndex((lane) => Math.abs(ball.x - lane.x) < 25);
    if (laneIndex >= 0 && !pinballRolloverLights.value[laneIndex]) {
      const nextLights = [...pinballRolloverLights.value];
      nextLights[laneIndex] = true;
      pinballRolloverLights.value = nextLights;
      awardPinballScore(400, `${pinballRolloverLanes[laneIndex]!.label} 中继接通`);
      chargePinballJackpot(150);
      pulsePinballImpact(pinballRolloverLanes[laneIndex]!.x, 102, "signal");
      if (nextLights.every(Boolean)) {
        pinballMultiplier.value = Math.min(5, pinballMultiplier.value + 1);
        pinballCallout.value = `MIMO 中继上线 · 倍率升至 x${pinballMultiplier.value}`;
      }
    }
  }

  if (ball.previousY < 337 && ball.y >= 337 && (ball.x < 78 || ball.x > 238)) {
    awardPinballScore(300, "内道回球");
    chargePinballJackpot(80);
  }
}

function startPinballMultiball(ball: PinballBall) {
  pinballMultiballActive.value = true;
  pinballBallSaveUntil.value = pinballNow.value + Math.max(4, pinballBallSaveDurationSeconds.value - 2) * 1000;
  // 多球弹出速度与坡度重力配套，保证三球先散入上半场，而不是在球门旁原地回落。
  pinballBalls.value.push(
    createPinballBall(ball.x - 8, ball.y - 10, -230, -560),
    createPinballBall(ball.x + 8, ball.y - 8, 225, -610),
  );
  ball.vx = -150;
  ball.vy = -640;
  const payout = pinballJackpot.value;
  awardPinballScore(payout, "JACKPOT · 信号风暴");
  pinballJackpot.value += 1_500;
  pulsePinballImpact(ball.x, ball.y, "jackpot");
}

function resolvePinballFlipper(ball: PinballBall, side: "left" | "right") {
  const pressed = side === "left" ? pinballLeftPressed.value : pinballRightPressed.value;
  const pivot = side === "left" ? pinballLeftPivot : pinballRightPivot;
  const hit = resolvePinballSegment(ball, pivot, pinballFlipperTip(side), 7, 0.94);
  if (!hit || !pressed || !pinballHitAvailable(ball, `flipper-${side}`, 150)) return;
  // 挡板命中时给出明确的向上抽射，避免仅靠几何反弹产生“按了却没感觉”的手感。
  ball.vx += side === "left" ? 135 : -135;
  ball.vy = Math.min(ball.vy, -590);
  chargePinballJackpot(40);
  awardPinballScore(90, side === "left" ? "左翼反击" : "右翼反击", true);
  pulsePinballImpact(ball.x, ball.y, "signal");
}

function resolvePinballPlayfield(ball: PinballBall) {
  // 右侧发射导轨、两侧回球导轨和底部弹射三角共同形成主要物理边界。
  resolvePinballSegment(ball, { x: PINBALL_LAUNCH_RAIL_X, y: 76 }, { x: PINBALL_LAUNCH_RAIL_X, y: 342 }, 2, 0.8);
  resolvePinballSegment(ball, { x: 30, y: 286 }, { x: 76, y: 348 }, 3, 0.78);
  resolvePinballSegment(ball, { x: 258, y: 286 }, { x: 240, y: 348 }, 3, 0.78);
  pinballBumpers.forEach((bumper, index) => resolvePinballBumper(ball, bumper, index));

  if (resolvePinballSegment(ball, { x: 54, y: 198 }, { x: 54, y: 235 }, 2, 0.88, { x: 42, y: -18 })
    && pinballHitAvailable(ball, "spinner", 120)) {
    pinballSpinnerTurns.value += 1;
    awardPinballScore(90, `旋转门 ${pinballSpinnerTurns.value} 转`);
    chargePinballJackpot(25);
    pulsePinballImpact(54, 216, "charge");
  }

  pinballDropTargetPositions.forEach((x, index) => {
    if (pinballDropTargets.value[index]) return;
    if (resolvePinballSegment(ball, { x: x - 12, y: 245 }, { x: x + 12, y: 245 }, 4, 0.72, { x: 0, y: -82 })
      && pinballHitAvailable(ball, `target-${index}`, 500)) {
      const nextTargets = [...pinballDropTargets.value];
      nextTargets[index] = true;
      pinballDropTargets.value = nextTargets;
      awardPinballScore(650, `放大器 ${index + 1} 击落`);
      chargePinballJackpot(300);
      pulsePinballImpact(x, 245, "charge");
      if (nextTargets.every(Boolean)) pinballCallout.value = "放大器全清 · 红色核心已充能";
    }
  });

  if (resolvePinballSegment(ball, { x: 45, y: 286 }, { x: 85, y: 321 }, 5, 0.9, { x: 95, y: -145 })
    && pinballHitAvailable(ball, "left-sling", 260)) {
    awardPinballScore(180, "左弹射器", true);
    chargePinballJackpot(60);
    pulsePinballImpact(66, 305, "charge");
  }
  if (resolvePinballSegment(ball, { x: 271, y: 286 }, { x: 231, y: 321 }, 5, 0.9, { x: -95, y: -145 })
    && pinballHitAvailable(ball, "right-sling", 260)) {
    awardPinballScore(180, "右弹射器", true);
    chargePinballJackpot(60);
    pulsePinballImpact(250, 305, "charge");
  }

  if (resolvePinballCircle(ball, { x: 244, y: 236 }, 14, 110) && pinballHitAvailable(ball, "scoop", 800)) {
    if (pinballMissionReady.value && !pinballMultiballActive.value) {
      startPinballMultiball(ball);
    } else if (pinballMultiballActive.value) {
      const payout = pinballJackpot.value;
      awardPinballScore(payout, "SUPER JACKPOT");
      pinballJackpot.value += 2_000;
      pulsePinballImpact(244, 236, "jackpot");
    } else {
      awardPinballScore(350, "核心尚未充能");
      pulsePinballImpact(244, 236, "signal");
    }
  }

  resolvePinballFlipper(ball, "left");
  resolvePinballFlipper(ball, "right");
}

function finishPinballMultiballIfNeeded() {
  if (!pinballMultiballActive.value || pinballBalls.value.length > 1) return;
  pinballMultiballActive.value = false;
  resetPinballMission();
  pinballDifficultyLevel.value = Math.min(
    RANDOM_DIFFICULTY_LEVELS,
    pinballDifficultyLevel.value + randomDifficultyIncrease(),
  );
  pinballCallout.value = `多球结束 · 随机干扰升至 ${pinballDifficultyLevel.value} 档`;
}

function drainPinballBall(ballId: number) {
  pinballBalls.value = pinballBalls.value.filter((ball) => ball.id !== ballId);
  if (pinballBalls.value.length > 0) {
    pinballCallout.value = `一球出局 · 场上 ${pinballBalls.value.length} 球`;
    finishPinballMultiballIfNeeded();
    return;
  }

  if (pinballStatus.value !== "tilted" && pinballBallSaveUntil.value > pinballNow.value) {
    // 自动救球要重新越过发射导轨顶部，不能受增强后的重力影响而在导轨内反复落回。
    const savedBall = createPinballBall(289, 385, -10, -PINBALL_AUTO_SERVE_SPEED);
    pinballBalls.value = [savedBall];
    pinballBallSaveUntil.value = 0;
    pinballCallout.value = "BALL SAVE · 自动补球";
    return;
  }

  const earnedBonus = pinballStatus.value === "tilted" ? 0 : pinballBonus.value;
  if (earnedBonus > 0) {
    pinballScore.value += earnedBonus;
    pinballHighScore.value = Math.max(pinballHighScore.value, pinballScore.value);
  }
  pinballLives.value -= 1;
  pinballMultiballActive.value = false;
  if (pinballLives.value <= 0) {
    clearPinballTimers();
    pinballStatus.value = "gameover";
    pinballCallout.value = `最终得分 ${pinballScore.value}`;
    return;
  }
  // 中继与放大器进度跨弹珠保留；失误会失去一球，但不会把整条任务链打回起点。
  pinballSpinnerTurns.value = 0;
  servePinballBall(pinballStatus.value === "tilted" ? "TILT · 下一球" : `奖励分 ${earnedBonus} · 下一球`);
}

function stepPinball() {
  if (pinballStatus.value !== "running" && pinballStatus.value !== "tilted") return;
  pinballNow.value = Date.now();
  if (pinballComboUntil.value <= pinballNow.value) pinballCombo.value = 0;
  const deltaSeconds = PINBALL_FRAME_MS / 1000;
  const drainedBallIds: number[] = [];
  pinballBalls.value.forEach((ball) => {
    ball.previousX = ball.x;
    ball.previousY = ball.y;
    ball.vy += pinballGravity.value * deltaSeconds;
    ball.vx *= 0.999;
    ball.vy *= 0.999;
    const speed = Math.hypot(ball.vx, ball.vy);
    if (speed > PINBALL_MAX_SPEED) {
      ball.vx = ball.vx / speed * PINBALL_MAX_SPEED;
      ball.vy = ball.vy / speed * PINBALL_MAX_SPEED;
    }
    ball.x += ball.vx * deltaSeconds;
    ball.y += ball.vy * deltaSeconds;

    if (ball.x < PINBALL_BALL_RADIUS + 8) {
      ball.x = PINBALL_BALL_RADIUS + 8;
      ball.vx = Math.abs(ball.vx) * 0.86;
    } else if (ball.x > PINBALL_WIDTH - PINBALL_BALL_RADIUS - 8) {
      ball.x = PINBALL_WIDTH - PINBALL_BALL_RADIUS - 8;
      ball.vx = -Math.abs(ball.vx) * 0.86;
    }
    if (ball.y < PINBALL_BALL_RADIUS + 8) {
      ball.y = PINBALL_BALL_RADIUS + 8;
      if (ball.inLaunchLane && ball.x > PINBALL_LAUNCH_RAIL_X) {
        // 发射槽顶部是弧形回转导轨：首次到顶后把弹珠送过单向门，而不是垂直弹回起点。
        ball.inLaunchLane = false;
        ball.x = PINBALL_LAUNCH_GATE_X - 4;
        ball.vx = -240;
        ball.vy = Math.max(120, Math.abs(ball.vy) * 0.32);
      } else {
        ball.vy = Math.abs(ball.vy) * 0.86;
      }
    }
    if (!ball.inLaunchLane && ball.y < 76 && ball.x > PINBALL_LAUNCH_GATE_X) {
      // 单向门只允许发射出球；主球台内的球从顶部回弹时不能重新掉进右侧发射槽。
      ball.x = PINBALL_LAUNCH_GATE_X;
      ball.vx = -Math.max(140, Math.abs(ball.vx) * 0.86);
    }

    if (pinballStatus.value === "running") {
      resolvePinballPlayfield(ball);
      detectPinballFeatures(ball);
    }
    if (ball.y > PINBALL_HEIGHT + PINBALL_BALL_RADIUS
      || (ball.y > 386 && (ball.x < 54 || ball.x > 262))) {
      drainedBallIds.push(ball.id);
    }
  });
  drainedBallIds.forEach(drainPinballBall);
}

function nudgePinball(direction = 1) {
  if (pinballStatus.value !== "running") return;
  const now = Date.now();
  pinballTiltWarnings.value = now - pinballLastNudgeAt.value < 1600
    ? pinballTiltWarnings.value + 1
    : 1;
  pinballLastNudgeAt.value = now;
  pinballBalls.value.forEach((ball) => {
    ball.vx += direction * 88;
    ball.vy -= 24;
  });
  if (pinballTiltWarnings.value >= 3) {
    pinballStatus.value = "tilted";
    pinballCallout.value = "TILT · 本球奖励分清零";
    pinballBallSaveUntil.value = 0;
    pinballLeftPressed.value = false;
    pinballRightPressed.value = false;
    pinballBalls.value.forEach((ball) => {
      ball.vx *= 0.25;
      ball.vy = Math.max(190, ball.vy);
    });
    startPinballFrameTimer();
    return;
  }
  pinballCallout.value = `晃台警告 ${pinballTiltWarnings.value}/3`;
}

function togglePinballPause() {
  if (pinballStatus.value === "gameover") {
    resetPinball();
    return;
  }
  if (pinballStatus.value === "tilted") return;
  if (pinballStatus.value === "ready") {
    beginPinballCharge();
    releasePinballCharge();
    return;
  }
  pinballStatus.value = pinballStatus.value === "running" ? "paused" : "running";
  startPinballFrameTimer();
}

function selectGame(game: GameKind) {
  if (activeGame.value === "tetris" && game !== "tetris" && tetrisRunning.value) {
    tetrisRunning.value = false;
    clearTetrisTimer();
  }
  if (activeGame.value === "snake" && game !== "snake" && snakeRunning.value) {
    snakeRunning.value = false;
    clearSnakeTimer();
  }
  if (activeGame.value === "pinball" && game !== "pinball") {
    if (pinballStatus.value === "running" || pinballStatus.value === "tilted") pinballStatus.value = "paused";
    clearPinballTimers();
  }
  activeGame.value = game;
  if (game === "tetris" && !tetrisPiece.value) startTetris();
  if (game === "snake" && snakeBody.value.length === 0) startSnake();
  if (game === "pinball" && (!pinballRoundInitialized || pinballLives.value <= 0)) resetPinball();
  void nextTick(() => panel.value?.focus());
}

function onPanelKeydown(event: KeyboardEvent) {
  if (activeGame.value === "gold-miner") {
    const key = event.key.toLowerCase();
    if ((event.key === " " || event.key === "ArrowDown") && !event.repeat) {
      event.preventDefault();
      goldMinerGame.value?.dropHook();
    } else if (key === "p") {
      event.preventDefault();
      goldMinerGame.value?.togglePause();
    } else if (key === "r") {
      event.preventDefault();
      goldMinerGame.value?.resetGame();
    }
    return;
  }
  if (activeGame.value === "pinball") {
    const key = event.key.toLowerCase();
    if (event.key === "ArrowLeft" || key === "z") {
      event.preventDefault();
      setPinballFlipper("left", true);
    } else if (event.key === "ArrowRight" || key === "/") {
      event.preventDefault();
      setPinballFlipper("right", true);
    } else if (event.key === " " && !event.repeat) {
      event.preventDefault();
      beginPinballCharge();
    } else if (key === "n") {
      event.preventDefault();
      nudgePinball(event.shiftKey ? -1 : 1);
    } else if (key === "p") {
      event.preventDefault();
      togglePinballPause();
    }
    return;
  }
  if (activeGame.value === "snake") {
    const directions: Record<string, SnakePoint> = {
      ArrowLeft: { x: -1, y: 0 },
      ArrowRight: { x: 1, y: 0 },
      ArrowUp: { x: 0, y: -1 },
      ArrowDown: { x: 0, y: 1 },
    };
    const direction = directions[event.key];
    if (direction) {
      event.preventDefault();
      setSnakeDirection(direction.x, direction.y);
    } else if (event.key.toLowerCase() === "p") {
      event.preventDefault();
      toggleSnakePause();
    }
    return;
  }
  if (activeGame.value === "sudoku") {
    if (/^[1-9]$/.test(event.key)) {
      event.preventDefault();
      setSudokuValue(Number(event.key));
    } else if (event.key === "Backspace" || event.key === "Delete" || event.key === "0") {
      event.preventDefault();
      setSudokuValue(0);
    }
    return;
  }
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

function onPanelKeyup(event: KeyboardEvent) {
  if (activeGame.value !== "pinball") return;
  const key = event.key.toLowerCase();
  if (event.key === "ArrowLeft" || key === "z") {
    event.preventDefault();
    setPinballFlipper("left", false);
  } else if (event.key === "ArrowRight" || key === "/") {
    event.preventDefault();
    setPinballFlipper("right", false);
  } else if (event.key === " ") {
    event.preventDefault();
    releasePinballCharge();
  }
}

function closePanel() {
  clearTetrisTimer();
  clearSnakeTimer();
  clearPinballTimers();
  emit("close");
}

onBeforeUnmount(() => {
  clearTetrisTimer();
  clearSnakeTimer();
  clearPinballTimers();
});
</script>

<template>
  <section
    ref="panel"
    class="pet-game-panel"
    :class="{ 'is-embedded': props.embedded, 'is-pinball': activeGame === 'pinball', 'is-miner': activeGame === 'gold-miner' }"
    data-testid="pet-mini-games"
    :role="props.embedded ? 'group' : 'dialog'"
    :aria-labelledby="props.embedded ? undefined : 'pet-game-title'"
    tabindex="-1"
    @keydown="onPanelKeydown"
    @keyup="onPanelKeyup"
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
        <span class="pet-game-choice-art mine-choice-art" aria-hidden="true"><Bomb :size="15" /></span>
        <span><strong>扫雷</strong><small>左键翻开 · 右键插旗</small></span>
      </button>
      <button type="button" class="pet-game-choice is-sudoku" data-testid="pet-game-open-sudoku" @click="selectGame('sudoku')">
        <span class="pet-game-choice-art sudoku-choice-art" aria-hidden="true">
          <i v-for="index in 9" :key="index">{{ index === 2 || index === 5 || index === 7 ? index : "" }}</i>
        </span>
        <span><strong>数独</strong><small>选格填写 · 即时检查</small></span>
      </button>
      <button type="button" class="pet-game-choice is-snake" data-testid="pet-game-open-snake" @click="selectGame('snake')">
        <span class="pet-game-choice-art snake-choice-art" aria-hidden="true">
          <i v-for="index in 8" :key="index" :class="{ 'is-food': index === 8 }" />
        </span>
        <span><strong>贪吃蛇</strong><small>方向键移动 · 吃点得分</small></span>
      </button>
      <button type="button" class="pet-game-choice is-pinball" data-testid="pet-game-open-pinball" @click="selectGame('pinball')">
        <span class="pet-game-choice-art pinball-choice-art" aria-hidden="true">
          <i class="pinball-choice-bumper" />
          <i class="pinball-choice-ball" />
          <i class="pinball-choice-flipper is-left" />
          <i class="pinball-choice-flipper is-right" />
        </span>
        <span><strong>桌面弹球</strong><small>按住蓄力 · 左右挡板救球</small></span>
      </button>
      <button type="button" class="pet-game-choice is-miner" data-testid="pet-game-open-gold-miner" @click="selectGame('gold-miner')">
        <span class="pet-game-choice-art miner-choice-art" aria-hidden="true">
          <i class="miner-choice-rope" />
          <i class="miner-choice-hook" />
          <i class="miner-choice-gold" />
          <i class="miner-choice-rock" />
        </span>
        <span><strong>黄金矿工</strong><small>摆钩抓金 · 重物可爆破</small></span>
      </button>
    </div>

    <template v-else>
      <nav class="pet-game-tabs" aria-label="小游戏切换">
        <button type="button" :class="{ 'is-active': activeGame === 'tetris' }" @click="selectGame('tetris')">俄罗斯方块</button>
        <button type="button" :class="{ 'is-active': activeGame === 'minesweeper' }" @click="selectGame('minesweeper')">扫雷</button>
        <button type="button" :class="{ 'is-active': activeGame === 'sudoku' }" @click="selectGame('sudoku')">数独</button>
        <button type="button" :class="{ 'is-active': activeGame === 'snake' }" @click="selectGame('snake')">贪吃蛇</button>
        <button type="button" :class="{ 'is-active': activeGame === 'pinball' }" @click="selectGame('pinball')">弹球</button>
        <button type="button" :class="{ 'is-active': activeGame === 'gold-miner' }" @click="selectGame('gold-miner')">矿工</button>
      </nav>

      <div v-if="activeGame === 'tetris'" class="pet-tetris" data-testid="pet-tetris">
        <div class="pet-game-status-row">
          <span>{{ tetrisStatus }}</span>
          <span data-testid="pet-tetris-level">随机档 {{ tetrisDifficultyLevel }} · 等级 {{ tetrisLevel }} · {{ tetrisTickMs }}ms</span>
          <span>分数 {{ tetrisScore }} · 消行 {{ tetrisLines }}</span>
        </div>
        <div class="pet-tetris-next" data-testid="pet-tetris-next" aria-label="下一个俄罗斯方块">
          <span>下一个</span>
          <div class="pet-tetris-next-board" aria-hidden="true">
            <div v-for="(row, rowIndex) in tetrisNextBoard" :key="rowIndex" class="pet-tetris-preview-row">
              <span
                v-for="(cell, columnIndex) in row"
                :key="`${rowIndex}-${columnIndex}`"
                class="pet-tetris-preview-cell"
                :class="cell ? `is-${tetrisNextPiece?.color}` : undefined"
              />
            </div>
          </div>
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

      <div v-else-if="activeGame === 'minesweeper'" class="pet-mines" data-testid="pet-minesweeper">
        <div class="pet-game-status-row">
          <span>{{ mineStatusText }}</span>
          <span>随机档 {{ mineLevel }} · 雷 {{ mineCount }} · 旗 {{ mineFlags }}</span>
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
            @dblclick="chordMineCell(index)"
            @contextmenu.prevent="toggleMineFlag(index)"
          >
            <span v-if="cell.flagged">⚑</span>
            <span v-else-if="cell.revealed && cell.mine">✹</span>
            <span v-else-if="cell.revealed && cell.nearby">{{ cell.nearby }}</span>
          </button>
        </div>
      </div>

      <div v-else-if="activeGame === 'sudoku'" class="pet-sudoku" data-testid="pet-sudoku">
        <div class="pet-game-status-row">
          <span>{{ sudokuStatusText }}</span>
          <span>随机档 {{ sudokuLevel }} · 剩余 {{ sudokuRemaining }} 格</span>
          <button type="button" aria-label="重开数独" @click="resetSudoku"><RotateCcw :size="13" /></button>
        </div>
        <div class="pet-sudoku-board" role="grid" aria-label="数独棋盘">
          <button
            v-for="(cell, index) in sudokuBoard"
            :key="index"
            type="button"
            class="pet-sudoku-cell"
            :class="{
              'is-given': cell.given,
              'is-selected': sudokuSelectedIndex === index,
              'is-error': cell.error,
              'is-box-right': (index + 1) % 3 === 0 && (index + 1) % 9 !== 0,
              'is-box-bottom': Math.floor(index / 9) === 2 || Math.floor(index / 9) === 5,
            }"
            :aria-label="sudokuCellLabel(cell, index)"
            :disabled="cell.given || sudokuStatus === 'won'"
            role="gridcell"
            @click="selectSudokuCell(index)"
          >
            {{ cell.value || "" }}
          </button>
        </div>
        <div class="pet-sudoku-numpad" aria-label="数独数字键盘">
          <button v-for="number in 9" :key="number" type="button" :aria-label="`填写数字 ${number}`" @click="setSudokuValue(number)">
            {{ number }}
          </button>
          <button type="button" class="is-clear" aria-label="清除数独格" @click="setSudokuValue(0)">清除</button>
        </div>
      </div>

      <div v-else-if="activeGame === 'snake'" class="pet-snake" data-testid="pet-snake">
        <div class="pet-game-status-row">
          <span>{{ snakeStatusText }}</span>
          <span>随机档 {{ snakeDifficultyLevel }} · 等级 {{ snakeLevel }} · {{ snakeTickMs }}ms</span>
          <span>得分 {{ snakeScore }}</span>
          <button type="button" aria-label="重开贪吃蛇" @click="startSnake"><RotateCcw :size="13" /></button>
        </div>
        <div class="pet-snake-board" role="grid" aria-label="贪吃蛇棋盘">
          <span
            v-for="(cell, index) in visibleSnakeBoard"
            :key="index"
            class="pet-snake-cell"
            :class="cell && `is-${cell}`"
            role="gridcell"
          />
        </div>
        <div class="pet-snake-controls" aria-label="贪吃蛇操作">
          <button type="button" aria-label="贪吃蛇向上" @click="setSnakeDirection(0, -1)">↑</button>
          <button type="button" aria-label="贪吃蛇向左" @click="setSnakeDirection(-1, 0)">←</button>
          <button type="button" aria-label="贪吃蛇向下" @click="setSnakeDirection(0, 1)">↓</button>
          <button type="button" aria-label="贪吃蛇向右" @click="setSnakeDirection(1, 0)">→</button>
          <button type="button" class="is-wide" @click="toggleSnakePause">{{ snakeRunning ? "暂停" : snakeGameOver ? "重开" : "继续" }}</button>
        </div>
      </div>

      <div v-else-if="activeGame === 'pinball'" class="pet-pinball" data-testid="pet-pinball">
        <div class="pet-game-status-row">
          <span>{{ pinballStatusText }}</span>
          <span data-testid="pet-pinball-score">分数 {{ pinballScore.toString().padStart(5, "0") }}</span>
          <button type="button" aria-label="重开桌面弹球" @click="resetPinball"><RotateCcw :size="13" /></button>
        </div>
        <div class="pet-pinball-feature-strip" aria-label="桌面弹球局内状态">
          <span data-testid="pet-pinball-difficulty"><small>随机档</small><strong>Lv{{ pinballDifficultyLevel }}</strong></span>
          <span class="is-jackpot"><small>奖池</small><strong>{{ pinballJackpot }}</strong></span>
          <span><small>倍率</small><strong>×{{ pinballMultiplier }}</strong></span>
          <span :class="{ 'is-hot': pinballComboVisible }"><small>连击</small><strong>×{{ pinballComboVisible ? pinballCombo : 1 }}</strong></span>
          <span :class="{ 'is-hot': pinballBallSaveSeconds > 0 }"><small>救球</small><strong>{{ pinballBallSaveSeconds ? `${pinballBallSaveSeconds}s` : "—" }}</strong></span>
          <span :class="{ 'is-hot': pinballMultiballActive }"><small>场上</small><strong>{{ pinballBalls.length }} 球</strong></span>
        </div>
        <div
          class="pet-pinball-board"
          data-testid="pet-pinball-board"
          role="application"
          aria-label="桌面弹球台，空格蓄力发射，左右方向键控制挡板，N 键晃台，P 键暂停"
        >
          <div class="pet-pinball-paper-score" aria-hidden="true">
            <span>MIMO SIGNAL LAB · HI {{ pinballHighScore.toString().padStart(5, "0") }}</span>
            <strong>{{ pinballScore.toString().padStart(5, "0") }}</strong>
          </div>
          <div class="pet-pinball-lives" aria-label="剩余弹珠">
            <span
              v-for="life in Math.max(3, pinballLives)"
              :key="life"
              :class="{ 'is-spent': life > pinballLives }"
              aria-hidden="true"
            />
            <small>× {{ pinballLives }}</small>
          </div>
          <div class="pet-pinball-mission-display" :class="{ 'is-ready': pinballMissionReady, 'is-multiball': pinballMultiballActive }">
            <small>RELAY // {{ pinballMissionProgress }}/7</small>
            <strong>{{ pinballMissionText }}</strong>
          </div>
          <span class="pet-pinball-circuit is-left" aria-hidden="true"><i /><i /><i /></span>
          <span class="pet-pinball-circuit is-right" aria-hidden="true"><i /><i /><i /></span>
          <div class="pet-pinball-rollovers" aria-label="顶部翻滚灯">
            <span
              v-for="(lane, index) in pinballRolloverLanes"
              :key="lane.id"
              :class="{ 'is-lit': pinballRolloverLights[index] }"
              :style="{ left: `${lane.x}px` }"
            >{{ lane.label }}</span>
          </div>
          <span class="pet-pinball-rail" aria-hidden="true" />
          <span class="pet-pinball-arch" aria-hidden="true" />
          <span class="pet-pinball-post is-left" aria-hidden="true" />
          <span class="pet-pinball-post is-right" aria-hidden="true" />
          <span class="pet-pinball-spinner" aria-hidden="true"><i /><small>SPIN</small></span>
          <span
            v-for="(bumper, index) in pinballBumpers"
            :key="index"
            class="pet-pinball-bumper"
            :class="`is-${bumper.tone}`"
            :style="{ left: `${bumper.x}px`, top: `${bumper.y}px`, width: `${bumper.radius * 2}px`, height: `${bumper.radius * 2}px` }"
            aria-hidden="true"
          >
            <i />
            <small>{{ bumper.label }}</small>
          </span>
          <span
            v-for="(x, index) in pinballDropTargetPositions"
            :key="x"
            class="pet-pinball-drop-target"
            :class="{ 'is-dropped': pinballDropTargets[index] }"
            :style="{ left: `${x}px` }"
            aria-hidden="true"
          >{{ ["A", "M", "P"][index] }}</span>
          <span class="pet-pinball-sling is-left" aria-hidden="true"><i>+180</i></span>
          <span class="pet-pinball-sling is-right" aria-hidden="true"><i>+180</i></span>
          <span class="pet-pinball-scoop" :class="{ 'is-ready': pinballMissionReady }" aria-hidden="true">
            <i />
            <small>{{ pinballMissionReady ? "JACKPOT" : "CORE" }}</small>
          </span>
          <span class="pet-pinball-inlane is-left" aria-hidden="true">IN</span>
          <span class="pet-pinball-inlane is-right" aria-hidden="true">IN</span>
          <span class="pet-pinball-flipper is-left" :class="{ 'is-pressed': pinballLeftPressed }" :style="pinballLeftFlipperStyle" aria-hidden="true" />
          <span class="pet-pinball-flipper is-right" :class="{ 'is-pressed': pinballRightPressed }" :style="pinballRightFlipperStyle" aria-hidden="true" />
          <span class="pet-pinball-drain" aria-hidden="true" />
          <span
            v-if="pinballImpact"
            :key="pinballImpact.id"
            class="pet-pinball-impact"
            :class="`is-${pinballImpact.tone}`"
            :style="{ left: `${pinballImpact.x}px`, top: `${pinballImpact.y}px` }"
            aria-hidden="true"
          />
          <span
            v-for="ball in pinballBalls"
            :key="ball.id"
            class="pet-pinball-ball"
            :style="pinballBallStyle(ball)"
            aria-hidden="true"
          />
          <button
            type="button"
            class="pet-pinball-plunger"
            aria-label="按住蓄力，松开发射弹珠"
            :aria-pressed="pinballCharging"
            :disabled="pinballStatus !== 'ready'"
            @pointerdown.prevent="beginPinballCharge"
            @pointerup.prevent="releasePinballCharge"
            @pointercancel.prevent="releasePinballCharge"
          >
            <span class="pet-pinball-plunger-track" aria-hidden="true">
              <em class="pet-pinball-skill-zone" :style="pinballSkillZoneStyle" />
              <i :style="pinballLauncherStyle" />
            </span>
            <small>{{ pinballCharging ? `${pinballCharge}%` : "发射" }}</small>
          </button>
          <div v-if="pinballStatus === 'paused' || pinballStatus === 'tilted' || pinballStatus === 'gameover'" class="pet-pinball-overlay" role="status">
            <strong>{{ pinballStatus === "gameover" ? "收工" : pinballStatus === "tilted" ? "TILT" : "暂停" }}</strong>
            <span v-if="pinballStatus === 'gameover'">本局 {{ pinballScore }} 分 · 最高 {{ pinballHighScore }}</span>
            <span v-else-if="pinballStatus === 'tilted'">挡板与本球奖励分已锁定</span>
            <span v-else>按 P 或继续返回球台</span>
          </div>
        </div>
        <div class="pet-pinball-ledger" aria-live="polite">
          <span>{{ pinballMissionText }} · 奖励 {{ pinballBonus }}</span>
          <span>{{ pinballDifficultyText }}</span>
        </div>
        <div class="pet-pinball-controls" aria-label="桌面弹球操作">
          <button
            type="button"
            aria-label="抬起左挡板"
            @pointerdown.prevent="setPinballFlipper('left', true)"
            @pointerup.prevent="setPinballFlipper('left', false)"
            @pointercancel.prevent="setPinballFlipper('left', false)"
            @pointerleave="setPinballFlipper('left', false)"
          >Z / ← 左挡板</button>
          <button type="button" class="is-nudge" aria-label="向左晃台" @click="nudgePinball(-1)">晃左</button>
          <button type="button" class="is-pause" @click="togglePinballPause">
            {{ pinballStatus === "running" ? "暂停" : pinballStatus === "gameover" ? "重开" : pinballStatus === "ready" ? "快速发射" : pinballStatus === "tilted" ? "TILT" : "继续" }}
          </button>
          <button type="button" class="is-nudge" aria-label="向右晃台" @click="nudgePinball(1)">晃右</button>
          <button
            type="button"
            aria-label="抬起右挡板"
            @pointerdown.prevent="setPinballFlipper('right', true)"
            @pointerup.prevent="setPinballFlipper('right', false)"
            @pointercancel.prevent="setPinballFlipper('right', false)"
            @pointerleave="setPinballFlipper('right', false)"
          >右挡板 → /</button>
        </div>
        <p class="pet-pinball-help">空格蓄力 · Z/← 与 /→ 挡板 · N 晃台 · P 暂停</p>
      </div>

      <PetGoldMinerGame
        v-show="activeGame === 'gold-miner'"
        ref="goldMinerGame"
        :active="activeGame === 'gold-miner'"
      />
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
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 6px;
  margin-top: 12px;
}

.pet-game-choice {
  display: flex;
  min-width: 0;
  flex-direction: row;
  align-items: center;
  gap: 7px;
  padding: 7px;
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
  width: 34px;
  height: 34px;
  flex: 0 0 34px;
  border-radius: 8px;
}

.tetris-choice-art {
  display: grid;
  grid-template-columns: repeat(4, 6px);
  grid-template-rows: repeat(3, 6px);
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

.sudoku-choice-art {
  display: grid;
  grid-template-columns: repeat(3, 8px);
  grid-template-rows: repeat(3, 8px);
  align-content: center;
  justify-content: center;
  gap: 1px;
  background: #f2f5f7;
}

.sudoku-choice-art i {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: 1px solid #c9d4dc;
  background: #fff;
  color: #536e91;
  font-size: 6px;
  font-style: normal;
  font-weight: 700;
}

.snake-choice-art {
  position: relative;
  background: #edf7f4;
}

.snake-choice-art i {
  position: absolute;
  width: 5px;
  height: 5px;
  border-radius: 2px;
  background: #5aa9a6;
}

.snake-choice-art i:nth-child(1) { left: 7px; top: 9px; }
.snake-choice-art i:nth-child(2) { left: 12px; top: 9px; }
.snake-choice-art i:nth-child(3) { left: 17px; top: 9px; }
.snake-choice-art i:nth-child(4) { left: 17px; top: 14px; }
.snake-choice-art i:nth-child(5) { left: 17px; top: 19px; }
.snake-choice-art i:nth-child(6) { left: 22px; top: 19px; }
.snake-choice-art i:nth-child(7) { left: 27px; top: 19px; }
.snake-choice-art i.is-food {
  left: 7px;
  top: 23px;
  border-radius: 50%;
  background: #cf7684;
}

.pinball-choice-art {
  position: relative;
  overflow: hidden;
  border: 3px solid #76543a;
  background: #315451;
  box-shadow: inset 0 0 0 1px #c59a53;
}

.pinball-choice-bumper,
.pinball-choice-ball,
.pinball-choice-flipper {
  position: absolute;
  display: block;
}

.pinball-choice-bumper {
  left: 9px;
  top: 5px;
  width: 9px;
  height: 9px;
  border: 2px solid #f2e0af;
  border-radius: 50%;
  background: #c8665a;
  box-shadow: 9px 5px 0 -1px #caa15a;
}

.pinball-choice-ball {
  right: 3px;
  top: 6px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #fff8e7;
  box-shadow: 0 0 0 1px #b99352;
}

.pinball-choice-flipper {
  bottom: 5px;
  width: 11px;
  height: 3px;
  border-radius: 999px;
  background: #e9d18f;
}

.pinball-choice-flipper.is-left { left: 5px; transform: rotate(16deg); }
.pinball-choice-flipper.is-right { right: 5px; transform: rotate(-16deg); }

.miner-choice-art {
  position: relative;
  overflow: hidden;
  border: 3px solid #214f58;
  background: linear-gradient(#214f58 0 28%, #b87541 29% 64%, #70483b 65%);
}

.miner-choice-art i {
  position: absolute;
  display: block;
}

.miner-choice-rope {
  left: 15px;
  top: 1px;
  width: 1px;
  height: 18px;
  background: #f1dfb2;
  transform: rotate(24deg);
  transform-origin: top;
}

.miner-choice-hook {
  left: 20px;
  top: 16px;
  width: 7px;
  height: 7px;
  border-right: 2px solid #f1cf70;
  border-bottom: 2px solid #f1cf70;
  border-radius: 0 0 6px;
  transform: rotate(18deg);
}

.miner-choice-gold {
  left: 4px;
  bottom: 3px;
  width: 9px;
  height: 7px;
  border: 1px solid #70410d;
  border-radius: 55% 45%;
  background: #e6a92b;
}

.miner-choice-rock {
  right: 3px;
  bottom: 2px;
  width: 10px;
  height: 8px;
  border-radius: 48% 60% 45% 55%;
  background: #59514e;
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

.pet-tetris-next {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 7px;
  min-height: 29px;
  margin-bottom: 7px;
  color: #73808d;
  font-size: 10px;
}

.pet-tetris-next-board {
  display: flex;
  flex-direction: column;
  gap: 1px;
  min-width: 34px;
  min-height: 26px;
  padding: 3px;
  border: 1px solid #d9e2e8;
  border-radius: 5px;
  background: #f1f5f7;
}

.pet-tetris-preview-row {
  display: grid;
  grid-template-columns: repeat(4, 8px);
  grid-auto-rows: 8px;
  gap: 1px;
}

.pet-tetris-preview-cell {
  border-radius: 1px;
  background: transparent;
}

.pet-tetris-preview-cell[class*="is-"] { box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.45); }
.pet-tetris-preview-cell.is-cyan { background: #61c8c2; }
.pet-tetris-preview-cell.is-yellow { background: #e2c66d; }
.pet-tetris-preview-cell.is-violet { background: #8a76bd; }
.pet-tetris-preview-cell.is-blue { background: #5f86b4; }
.pet-tetris-preview-cell.is-orange { background: #d99361; }
.pet-tetris-preview-cell.is-green { background: #72ad83; }
.pet-tetris-preview-cell.is-rose { background: #cf7684; }

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

.pet-sudoku-board {
  display: grid;
  width: fit-content;
  margin: 0 auto;
  grid-template-columns: repeat(9, 25px);
  grid-template-rows: repeat(9, 25px);
  overflow: hidden;
  border: 2px solid #536e91;
  border-radius: 8px;
  background: #536e91;
}

.pet-sudoku-cell {
  width: 25px;
  height: 25px;
  padding: 0;
  border: 0;
  border-right: 1px solid #d7dfe5;
  border-bottom: 1px solid #d7dfe5;
  background: #fff;
  color: #7461aa;
  cursor: pointer;
  font-size: 12px;
  font-weight: 650;
}

.pet-sudoku-cell.is-box-right { border-right: 2px solid #7f91a3; }
.pet-sudoku-cell.is-box-bottom { border-bottom: 2px solid #7f91a3; }
.pet-sudoku-cell.is-given {
  background: #edf1f4;
  color: #354d66;
  cursor: default;
  font-weight: 750;
}
.pet-sudoku-cell.is-selected {
  outline: 2px solid #7c6bb5;
  outline-offset: -2px;
  background: #f3effb;
}
.pet-sudoku-cell.is-error {
  background: #fff0ee;
  color: #c45d57;
}
.pet-sudoku-cell:focus-visible {
  position: relative;
  z-index: 1;
  outline: 2px solid #5aa9a6;
  outline-offset: -2px;
}

.pet-sudoku-numpad {
  display: grid;
  width: 225px;
  grid-template-columns: repeat(5, 1fr);
  gap: 4px;
  margin: 8px auto 0;
}

.pet-sudoku-numpad button {
  height: 26px;
  padding: 0;
  border: 1px solid #d8e0e6;
  border-radius: 6px;
  background: #fff;
  color: #3d556b;
  cursor: pointer;
  font-size: 11px;
  font-weight: 650;
}

.pet-sudoku-numpad button:hover,
.pet-sudoku-numpad button:focus-visible {
  border-color: #8ea5b7;
  outline: none;
  background: #f3f7f9;
}

.pet-sudoku-numpad button.is-clear {
  color: #7c6bb5;
  font-size: 9px;
}

.pet-snake-board {
  display: grid;
  width: fit-content;
  grid-template-columns: repeat(12, 16px);
  grid-template-rows: repeat(12, 16px);
  gap: 1px;
  margin: 0 auto;
  padding: 6px;
  border: 1px solid #b9c8d1;
  border-radius: 9px;
  background: #e8eef1;
}

.pet-snake-cell {
  border-radius: 3px;
  background: rgba(255, 255, 255, 0.72);
}

.pet-snake-cell.is-body,
.pet-snake-cell.is-head {
  background: #5aa9a6;
  box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.32);
}

.pet-snake-cell.is-head {
  background: #426f78;
}

.pet-snake-cell.is-food {
  margin: 3px;
  border-radius: 50%;
  background: #cf7684;
}

.pet-snake-controls {
  display: grid;
  width: 190px;
  grid-template-columns: repeat(5, 1fr);
  gap: 5px;
  margin: 8px auto 0;
}

.pet-snake-controls button {
  height: 27px;
  border: 1px solid #d8e0e6;
  border-radius: 7px;
  background: #fff;
  color: #3d556b;
  cursor: pointer;
}

.pet-snake-controls button.is-wide {
  color: #7c6bb5;
  font-size: 10px;
}

.pet-snake-controls button:hover,
.pet-snake-controls button:focus-visible {
  border-color: #8ea5b7;
  outline: none;
  background: #f3f7f9;
}

.pet-game-panel:is(.is-pinball, .is-miner):not(.is-embedded) {
  width: min(348px, calc(100vw - 16px));
}

.pet-pinball-feature-strip {
  display: grid;
  width: 316px;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  overflow: hidden;
  margin: 0 auto 6px;
  border: 1px solid #526b80;
  border-radius: 8px;
  background: #10283c;
  box-shadow: 0 3px 8px rgba(19, 42, 60, .16);
}

.pet-pinball-feature-strip > span {
  display: flex;
  min-width: 0;
  flex-direction: column;
  align-items: center;
  padding: 4px 2px;
  border-right: 1px solid rgba(158, 184, 201, .24);
  color: #d4e0e6;
}

.pet-pinball-feature-strip > span:last-child { border-right: 0; }
.pet-pinball-feature-strip > span.is-hot { background: #e5a63b; color: #172d3e; }
.pet-pinball-feature-strip > span.is-jackpot { background: #7d3130; color: #ffe5b4; }
.pet-pinball-feature-strip small { font-size: 7px; line-height: 9px; }
.pet-pinball-feature-strip strong { font: 700 10px/12px var(--ta-font-mono, "Geist Mono", monospace); }

.pet-pinball-board {
  position: relative;
  width: 316px;
  height: 420px;
  box-sizing: border-box;
  overflow: hidden;
  margin: 0 auto;
  border: 8px solid #243d53;
  border-radius: 28px 28px 16px 16px;
  background:
    linear-gradient(rgba(117, 164, 189, .06) 1px, transparent 1px),
    linear-gradient(90deg, rgba(117, 164, 189, .05) 1px, transparent 1px),
    radial-gradient(circle at 49% 39%, rgba(83, 170, 185, .17), transparent 47%),
    linear-gradient(155deg, #183a57 0%, #102e49 58%, #0d253b 100%);
  background-size: 23px 23px, 23px 23px, auto, auto;
  box-shadow:
    inset 0 0 0 2px #aabcc7,
    inset 0 0 35px rgba(3, 16, 29, .6),
    0 8px 16px rgba(24, 47, 65, .23);
  font-family: var(--ta-font-mono, "Geist Mono", monospace);
  touch-action: none;
}

.pet-pinball-board::before,
.pet-pinball-board::after {
  position: absolute;
  z-index: 0;
  content: "";
  pointer-events: none;
}

.pet-pinball-board::before {
  inset: 8px;
  border: 1px solid rgba(159, 195, 211, .26);
  border-radius: 20px 20px 10px 10px;
}

.pet-pinball-board::after {
  left: 48px;
  right: 48px;
  bottom: -52px;
  height: 114px;
  border: 2px solid rgba(119, 184, 199, .42);
  border-radius: 50%;
  background: rgba(5, 24, 39, .68);
}

.pet-pinball-paper-score {
  position: absolute;
  z-index: 2;
  left: 26px;
  top: 14px;
  display: flex;
  min-width: 126px;
  flex-direction: column;
  gap: 1px;
  padding: 4px 7px 3px;
  border: 1px solid #567f95;
  border-radius: 4px;
  background: #091e30;
  box-shadow: inset 0 0 8px rgba(64, 157, 174, .12), 0 2px 0 rgba(5, 17, 27, .32);
  color: #7dabb9;
}

.pet-pinball-paper-score span { font-size: 6px; letter-spacing: .07em; }
.pet-pinball-paper-score strong { color: #f0b348; font-size: 13px; letter-spacing: .14em; line-height: 14px; text-shadow: 0 0 8px rgba(240, 179, 72, .28); }

.pet-pinball-lives {
  position: absolute;
  z-index: 2;
  right: 29px;
  top: 18px;
  display: flex;
  align-items: center;
  gap: 3px;
  color: #bdd3dc;
}

.pet-pinball-lives > span {
  width: 7px;
  height: 7px;
  border: 1px solid #b7cad2;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #fff 0 18%, #c7d4d9 42%, #58778a 100%);
  box-shadow: 0 1px 3px rgba(4, 17, 28, .65);
}

.pet-pinball-lives > span.is-spent { background: transparent; box-shadow: none; opacity: .35; }
.pet-pinball-lives small { margin-left: 2px; font-size: 7px; }

.pet-pinball-mission-display {
  position: absolute;
  z-index: 2;
  left: 63px;
  right: 55px;
  top: 51px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
  min-height: 17px;
  border: 1px solid rgba(104, 177, 194, .48);
  border-radius: 4px;
  background: rgba(7, 29, 46, .86);
  color: #d8e8ed;
  text-align: center;
}

.pet-pinball-mission-display small { color: #69bcc7; font-size: 5px; letter-spacing: .08em; }
.pet-pinball-mission-display strong { overflow: hidden; font-size: 6px; text-overflow: ellipsis; white-space: nowrap; }
.pet-pinball-mission-display.is-ready { border-color: #f0b348; box-shadow: 0 0 11px rgba(240, 179, 72, .35); }
.pet-pinball-mission-display.is-multiball { border-color: #e07b5d; background: #743236; color: #ffe3b2; }

.pet-pinball-circuit {
  position: absolute;
  z-index: 1;
  top: 110px;
  width: 45px;
  height: 135px;
  border-top: 1px solid rgba(87, 184, 198, .32);
  border-bottom: 1px solid rgba(87, 184, 198, .26);
  pointer-events: none;
}

.pet-pinball-circuit.is-left { left: 19px; border-left: 1px solid rgba(87, 184, 198, .3); border-radius: 10px 0 0 10px; }
.pet-pinball-circuit.is-right { right: 35px; border-right: 1px solid rgba(87, 184, 198, .3); border-radius: 0 10px 10px 0; }
.pet-pinball-circuit i { position: absolute; width: 4px; height: 4px; border: 1px solid #5ba8b6; border-radius: 50%; background: #102e49; box-shadow: 0 0 5px rgba(87, 184, 198, .45); }
.pet-pinball-circuit i:nth-child(1) { top: -3px; }.pet-pinball-circuit i:nth-child(2) { top: 49%; }.pet-pinball-circuit i:nth-child(3) { bottom: -3px; }
.pet-pinball-circuit.is-left i { left: -3px; }.pet-pinball-circuit.is-right i { right: -3px; }

.pet-pinball-rollovers {
  position: absolute;
  z-index: 3;
  inset: 0;
  pointer-events: none;
}

.pet-pinball-rollovers span {
  position: absolute;
  top: 82px;
  display: inline-flex;
  width: 24px;
  height: 14px;
  align-items: center;
  justify-content: center;
  border: 1px solid #587b91;
  border-radius: 3px 3px 10px 10px;
  background: #0c263d;
  color: #718fa0;
  font-size: 8px;
  transform: translateX(-50%);
}

.pet-pinball-rollovers span::after {
  position: absolute;
  top: 15px;
  width: 1px;
  height: 17px;
  background: rgba(136, 180, 197, .42);
  content: "";
}

.pet-pinball-rollovers span.is-lit {
  border-color: #f2bd58;
  background: #df9f32;
  color: #172f42;
  box-shadow: 0 0 11px rgba(240, 179, 72, .7);
}

.pet-pinball-rail {
  position: absolute;
  z-index: 1;
  left: 269px;
  top: 70px;
  width: 12px;
  height: 276px;
  border-left: 3px solid #b7c7ce;
  border-radius: 14px 0 0 14px;
  box-shadow: -2px 0 0 rgba(74, 143, 162, .25);
}

.pet-pinball-arch {
  position: absolute;
  z-index: 1;
  left: 18px;
  top: 67px;
  width: 253px;
  height: 91px;
  border-top: 3px solid rgba(187, 204, 211, .72);
  border-right: 3px solid rgba(187, 204, 211, .72);
  border-radius: 50% 46% 0 0;
  transform: rotate(-1.5deg);
}

.pet-pinball-post {
  position: absolute;
  z-index: 2;
  top: 291px;
  width: 7px;
  height: 66px;
  border-radius: 999px;
  background: linear-gradient(90deg, #4d6b7e, #d7e0e3 48%, #4d6b7e);
  box-shadow: 0 0 0 2px rgba(6, 26, 42, .58);
}

.pet-pinball-post.is-left { left: 50px; transform: rotate(-34deg); }
.pet-pinball-post.is-right { right: 52px; transform: rotate(34deg); }

.pet-pinball-bumper {
  position: absolute;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  border: 4px solid #dce5e7;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  box-shadow: 0 0 0 2px #526f82, 0 4px 8px rgba(3, 18, 31, .5), inset 0 0 0 2px rgba(255, 255, 255, .28);
}

.pet-pinball-bumper.is-rose { background: #c95750; }
.pet-pinball-bumper.is-teal { background: #48a8b2; }
.pet-pinball-bumper.is-gold { background: #d39a35; }
.pet-pinball-bumper i { width: 30%; height: 30%; border-radius: 50%; background: rgba(255, 255, 255, .88); box-shadow: 0 0 8px rgba(225, 247, 249, .75); }
.pet-pinball-bumper small { position: absolute; top: calc(100% + 6px); color: #c8e1e7; font-size: 5px; letter-spacing: .08em; text-shadow: 0 1px 2px #071d30; }

.pet-pinball-spinner {
  position: absolute;
  z-index: 3;
  left: 46px;
  top: 190px;
  width: 17px;
  height: 51px;
  border: 1px solid rgba(97, 173, 188, .48);
  border-radius: 9px;
}

.pet-pinball-spinner i { position: absolute; left: 7px; top: 7px; width: 3px; height: 31px; border-radius: 99px; background: repeating-linear-gradient(#f0b348 0 5px, #5f7380 5px 8px); }
.pet-pinball-spinner small { position: absolute; left: -2px; bottom: -10px; color: #75b7c3; font-size: 5px; }

.pet-pinball-drop-target {
  position: absolute;
  z-index: 4;
  top: 235px;
  display: inline-flex;
  width: 24px;
  height: 13px;
  align-items: center;
  justify-content: center;
  border: 2px solid #ffd6b2;
  border-radius: 3px 3px 1px 1px;
  background: #c64f48;
  box-shadow: 0 3px 0 #692d31, 0 0 0 1px rgba(7, 29, 46, .72);
  color: #fff1d2;
  font-size: 6px;
  transform: translateX(-50%);
  transition: transform 120ms ease, opacity 120ms ease;
}

.pet-pinball-drop-target.is-dropped { opacity: .35; transform: translate(-50%, 8px) scaleY(.45); }

.pet-pinball-sling {
  position: absolute;
  z-index: 3;
  top: 283px;
  width: 0;
  height: 0;
  border-top: 36px solid transparent;
  border-bottom: 8px solid transparent;
  filter: drop-shadow(0 2px 1px rgba(3, 19, 31, .55));
}

.pet-pinball-sling.is-left { left: 45px; border-left: 42px solid #58b3be; transform: rotate(-4deg); }
.pet-pinball-sling.is-right { right: 45px; border-right: 42px solid #58b3be; transform: rotate(4deg); }
.pet-pinball-sling i { position: absolute; top: -19px; color: #17384f; font-size: 5px; font-style: normal; }
.pet-pinball-sling.is-left i { left: -36px; }
.pet-pinball-sling.is-right i { right: -36px; }

.pet-pinball-scoop {
  position: absolute;
  z-index: 3;
  left: 230px;
  top: 222px;
  display: flex;
  width: 30px;
  height: 30px;
  align-items: center;
  justify-content: center;
  border: 2px solid #9c5550;
  border-radius: 50%;
  background: #391f2a;
  box-shadow: inset 0 4px 7px #170f18, 0 0 0 3px rgba(203, 87, 77, .16);
}

.pet-pinball-scoop i { width: 12px; height: 12px; border-radius: 50%; background: #160e17; }
.pet-pinball-scoop small { position: absolute; bottom: -12px; color: #d28579; font-size: 5px; }
.pet-pinball-scoop.is-ready { border-color: #f0b348; box-shadow: 0 0 15px rgba(240, 179, 72, .76), inset 0 4px 6px #160e17; animation: pinball-core-ready 900ms ease-in-out infinite alternate; }
.pet-pinball-scoop.is-ready small { color: #ffd993; }

.pet-pinball-inlane {
  position: absolute;
  z-index: 2;
  top: 334px;
  color: rgba(125, 190, 202, .72);
  font-size: 5px;
  writing-mode: vertical-rl;
}

.pet-pinball-inlane.is-left { left: 55px; }
.pet-pinball-inlane.is-right { right: 55px; }

.pet-pinball-flipper {
  position: absolute;
  z-index: 5;
  top: 354px;
  width: 68px;
  height: 13px;
  box-sizing: border-box;
  border: 2px solid #49697d;
  border-radius: 999px;
  background: linear-gradient(180deg, #e9f0f1, #90b3be);
  box-shadow: 0 3px 5px rgba(3, 18, 30, .5), inset 0 2px 1px rgba(255, 255, 255, .62);
}

.pet-pinball-flipper::before { position: absolute; top: 2px; width: 6px; height: 6px; border-radius: 50%; background: #315267; content: ""; }
.pet-pinball-flipper.is-left { left: 90px; transform-origin: 6px 6px; }
.pet-pinball-flipper.is-left::before { left: 2px; }
.pet-pinball-flipper.is-right { left: 158px; transform-origin: 62px 6px; }
.pet-pinball-flipper.is-right::before { right: 2px; }
.pet-pinball-flipper.is-pressed { background: linear-gradient(180deg, #ffe6a5, #e7a83b); box-shadow: 0 0 11px rgba(240, 179, 72, .58), 0 3px 4px rgba(3, 18, 30, .5); }

.pet-pinball-ball {
  position: absolute;
  z-index: 7;
  width: 12px;
  height: 12px;
  border: 1px solid #355163;
  border-radius: 50%;
  background: radial-gradient(circle at 33% 28%, #fff 0 14%, #dce5e8 26%, #829aa6 65%, #2f495b 100%);
  box-shadow: 0 0 7px rgba(116, 200, 211, .32), 1px 3px 4px rgba(3, 18, 30, .58);
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.pet-pinball-drain { position: absolute; z-index: 2; left: 119px; bottom: -2px; width: 78px; height: 17px; border-radius: 50% 50% 0 0; background: #071b2c; box-shadow: 0 -2px 0 #5c8798; }

.pet-pinball-impact { position: absolute; z-index: 6; width: 17px; height: 17px; border: 2px solid #65c1cc; border-radius: 50%; transform: translate(-50%, -50%); animation: pinball-impact 360ms ease-out forwards; pointer-events: none; }
.pet-pinball-impact.is-charge { border-color: #f0b348; }.pet-pinball-impact.is-jackpot { border-color: #f07e66; box-shadow: 0 0 9px rgba(240, 126, 102, .7); }

.pet-pinball-plunger {
  position: absolute;
  z-index: 8;
  right: 5px;
  bottom: 9px;
  display: flex;
  width: 29px;
  height: 75px;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 3px;
  padding: 3px 2px;
  border: 1px solid rgba(129, 180, 196, .46);
  border-radius: 9px;
  background: rgba(7, 28, 44, .82);
  color: #bcd9e0;
  cursor: ns-resize;
}

.pet-pinball-plunger:disabled { cursor: default; opacity: .45; }
.pet-pinball-plunger:not(:disabled):focus-visible { outline: 2px solid #f0b348; outline-offset: 2px; }

.pet-pinball-plunger-track {
  position: relative;
  display: flex;
  width: 9px;
  height: 52px;
  align-items: flex-end;
  justify-content: center;
  overflow: hidden;
  border: 1px solid #638596;
  border-radius: 999px;
  background: #0a2235;
}

.pet-pinball-skill-zone { position: absolute; z-index: 2; inset: 9px 0 auto; height: 15px; border-top: 1px solid #67c1c8; border-bottom: 1px solid #67c1c8; background: rgba(87, 184, 198, .24); }
.pet-pinball-plunger-track i { display: block; width: 7px; max-height: 50px; border-radius: 999px; background: repeating-linear-gradient(0deg, #f0b348 0 3px, #586f7c 3px 5px); }
.pet-pinball-plunger small { font-size: 6px; line-height: 8px; white-space: nowrap; }

.pet-pinball-overlay { position: absolute; z-index: 10; inset: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px; background: rgba(7, 25, 40, .78); color: #e2edf0; backdrop-filter: blur(2px); }
.pet-pinball-overlay strong { font-family: var(--ta-font-sans, "Noto Sans SC", sans-serif); font-size: 22px; letter-spacing: .18em; }
.pet-pinball-overlay span { font-size: 8px; }

.pet-pinball-ledger {
  display: flex;
  width: 316px;
  box-sizing: border-box;
  justify-content: space-between;
  gap: 8px;
  margin: 6px auto 0;
  padding: 5px 7px;
  border: 1px solid #718a9a;
  border-radius: 5px;
  background: #edf3f4;
  color: #3e5a6d;
  font-size: 7px;
}

.pet-pinball-ledger span:first-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.pet-pinball-ledger span:last-child { flex: 0 0 auto; font-family: var(--ta-font-mono, "Geist Mono", monospace); }

.pet-pinball-controls {
  display: grid;
  width: 316px;
  grid-template-columns: 1fr 42px 52px 42px 1fr;
  gap: 4px;
  margin: 6px auto 0;
}

.pet-pinball-controls button { min-width: 0; height: 29px; padding: 0 4px; border: 1px solid #91a7b4; border-radius: 7px; background: #f4f8f8; color: #35566a; cursor: pointer; font-size: 7px; font-weight: 650; }
.pet-pinball-controls button:first-child,
.pet-pinball-controls button:last-child { border-color: #5e9baa; background: #e2f0f1; color: #1f5b68; }
.pet-pinball-controls button.is-pause { border-color: #d1a24b; background: #f6e8c7; color: #785319; }
.pet-pinball-controls button.is-nudge { border-color: #aebcc4; background: #edf1f3; color: #647782; }
.pet-pinball-controls button:hover,
.pet-pinball-controls button:focus-visible { border-color: #3c7f90; outline: none; background: #dcecee; }

.pet-pinball-help { margin: 5px 0 0; color: #71818b; font-size: 7px; line-height: 10px; text-align: center; }

@keyframes pinball-impact {
  from { opacity: 1; transform: translate(-50%, -50%) scale(.35); }
  to { opacity: 0; transform: translate(-50%, -50%) scale(2.15); }
}

@keyframes pinball-core-ready {
  from { transform: scale(.96); }
  to { transform: scale(1.04); }
}

@media (prefers-reduced-motion: reduce) {
  .pet-game-choice,
  .pet-pinball-drop-target { transition: none; }
  .pet-pinball-impact,
  .pet-pinball-scoop.is-ready { animation: none; }
  .pet-game-choice:hover,
  .pet-game-choice:focus-visible { transform: none; }
}
</style>
