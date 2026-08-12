<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref } from "vue";
import { Bomb, Gamepad2, RotateCcw, X } from "lucide-vue-next";

type GameKind = "tetris" | "minesweeper" | "sudoku" | "snake" | "pinball";
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
type PinballBall = PinballPoint & { vx: number; vy: number };
type PinballBumper = PinballPoint & { radius: number; score: number; tone: "rose" | "teal" | "gold" };

const emit = defineEmits<{ (event: "close"): void }>();
const props = withDefaults(defineProps<{ embedded?: boolean }>(), { embedded: false });

const activeGame = ref<GameKind | null>(null);
const panel = ref<HTMLElement | null>(null);

const TETRIS_ROWS = 16;
const TETRIS_COLUMNS = 10;
const TETRIS_BASE_TICK_MS = 460;
const TETRIS_MIN_TICK_MS = 120;
const TETRIS_LINES_PER_LEVEL = 4;
const RANDOM_DIFFICULTY_LEVELS = 5;
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
let tetrisTimer: ReturnType<typeof setInterval> | null = null;

const tetrisLevel = computed(() => Math.min(10, 1 + Math.floor(tetrisLines.value / TETRIS_LINES_PER_LEVEL)));
const tetrisTickMs = computed(() => Math.max(TETRIS_MIN_TICK_MS, TETRIS_BASE_TICK_MS - (tetrisLevel.value - 1) * 45));
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
  const source = TETROMINOES[Math.floor(Math.random() * TETROMINOES.length)]!;
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
  tetrisGameOver.value = false;
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
    tetrisLines.value += cleared;
    tetrisScore.value += [0, 100, 300, 500, 800][cleared] ?? cleared * 200;
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
const randomDifficultyLevel = () => 1 + Math.floor(Math.random() * RANDOM_DIFFICULTY_LEVELS);
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

const sudokuLevel = ref(randomDifficultyLevel());

function createSudokuBoard(level = sudokuLevel.value): SudokuCell[] {
  const givenIndexes = SUDOKU_PUZZLE.flatMap((value, index) => value > 0 ? [index] : []);
  const hiddenIndexes = new Set(givenIndexes.slice(0, Math.min(12, (level - 1) * 3)));
  return SUDOKU_PUZZLE.map((value, index) => {
    const given = value > 0 && !hiddenIndexes.has(index);
    return { value: given ? value : 0, given, error: false };
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
let snakeTimer: ReturnType<typeof setInterval> | null = null;

const snakeLevel = computed(() => Math.min(10, 1 + Math.floor(snakeScore.value / SNAKE_SCORE_PER_LEVEL)));
const snakeTickMs = computed(() => Math.max(SNAKE_MIN_TICK_MS, SNAKE_BASE_TICK_MS - (snakeLevel.value - 1) * 20));

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
    snakeScore.value += 1;
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

const PINBALL_WIDTH = 274;
const PINBALL_HEIGHT = 326;
const PINBALL_BALL_RADIUS = 6;
const PINBALL_FRAME_MS = 16;
const PINBALL_GRAVITY = 185;
const PINBALL_MAX_SPEED = 560;
const PINBALL_FLIPPER_LENGTH = 52;
const pinballBumpers: PinballBumper[] = [
  { x: 82, y: 92, radius: 18, score: 120, tone: "rose" },
  { x: 169, y: 77, radius: 20, score: 180, tone: "gold" },
  { x: 133, y: 151, radius: 17, score: 240, tone: "teal" },
];
const pinballLeftPivot: PinballPoint = { x: 84, y: 274 };
const pinballRightPivot: PinballPoint = { x: 190, y: 274 };
const pinballBall = ref<PinballBall>({ x: 249, y: 294, vx: 0, vy: 0 });
const pinballScore = ref(0);
const pinballLives = ref(3);
const pinballCharge = ref(0);
const pinballStatus = ref<"ready" | "running" | "paused" | "gameover">("ready");
const pinballCharging = ref(false);
const pinballLeftPressed = ref(false);
const pinballRightPressed = ref(false);
let pinballFrameTimer: ReturnType<typeof setInterval> | null = null;
let pinballChargeTimer: ReturnType<typeof setInterval> | null = null;

const pinballStatusText = computed(() => ({
  ready: "按住发射杆蓄力",
  running: "弹珠上桌",
  paused: "已暂停",
  gameover: "三枚弹珠用完了",
}[pinballStatus.value]));
const pinballBallStyle = computed(() => ({
  left: `${pinballBall.value.x}px`,
  top: `${pinballBall.value.y}px`,
}));
const pinballLeftAngle = computed(() => pinballLeftPressed.value ? -24 : 18);
const pinballRightAngle = computed(() => pinballRightPressed.value ? -24 : 18);
const pinballLeftFlipperStyle = computed(() => ({ transform: `rotate(${pinballLeftAngle.value}deg)` }));
const pinballRightFlipperStyle = computed(() => ({ transform: `rotate(${-pinballRightAngle.value}deg)` }));
const pinballLauncherStyle = computed(() => ({ height: `${Math.max(8, pinballCharge.value * 0.62)}px` }));

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

function servePinballBall() {
  clearPinballTimers();
  pinballBall.value = { x: 249, y: 294, vx: 0, vy: 0 };
  pinballCharge.value = 0;
  pinballStatus.value = "ready";
}

function resetPinball() {
  pinballScore.value = 0;
  pinballLives.value = 3;
  servePinballBall();
}

function startPinballFrameTimer() {
  clearPinballFrameTimer();
  if (pinballStatus.value === "running") {
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
    pinballCharge.value = Math.min(100, pinballCharge.value + 3);
  }, PINBALL_FRAME_MS);
}

function releasePinballCharge() {
  if (pinballStatus.value !== "ready" || !pinballCharging.value) return;
  const charge = Math.max(18, pinballCharge.value);
  clearPinballChargeTimer();
  // 发射力度同时影响向上速度与轻微左偏，让弹珠越过右侧导轨后自然进入桌面。
  pinballBall.value = {
    ...pinballBall.value,
    vx: -24 - charge * 0.42,
    vy: -245 - charge * 2.25,
  };
  pinballCharge.value = 0;
  pinballStatus.value = "running";
  startPinballFrameTimer();
}

function setPinballFlipper(side: "left" | "right", pressed: boolean) {
  if (side === "left") pinballLeftPressed.value = pressed;
  else pinballRightPressed.value = pressed;
}

function pinballFlipperTip(side: "left" | "right"): PinballPoint {
  const pivot = side === "left" ? pinballLeftPivot : pinballRightPivot;
  const angle = (side === "left" ? pinballLeftAngle.value : pinballRightAngle.value) * Math.PI / 180;
  return side === "left"
    ? { x: pivot.x + Math.cos(angle) * PINBALL_FLIPPER_LENGTH, y: pivot.y + Math.sin(angle) * PINBALL_FLIPPER_LENGTH }
    : { x: pivot.x - Math.cos(angle) * PINBALL_FLIPPER_LENGTH, y: pivot.y + Math.sin(angle) * PINBALL_FLIPPER_LENGTH };
}

function resolvePinballSegment(
  start: PinballPoint,
  end: PinballPoint,
  padding: number,
  restitution: number,
  kick: PinballPoint = { x: 0, y: 0 },
): boolean {
  const segmentX = end.x - start.x;
  const segmentY = end.y - start.y;
  const segmentLengthSquared = segmentX * segmentX + segmentY * segmentY;
  const ball = pinballBall.value;
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

function resolvePinballBumper(bumper: PinballBumper) {
  const ball = pinballBall.value;
  const deltaX = ball.x - bumper.x;
  const deltaY = ball.y - bumper.y;
  const distance = Math.hypot(deltaX, deltaY);
  const minimumDistance = PINBALL_BALL_RADIUS + bumper.radius;
  if (distance >= minimumDistance) return;
  const normalX = distance > 0.001 ? deltaX / distance : 0;
  const normalY = distance > 0.001 ? deltaY / distance : -1;
  const approachSpeed = ball.vx * normalX + ball.vy * normalY;
  ball.x = bumper.x + normalX * minimumDistance;
  ball.y = bumper.y + normalY * minimumDistance;
  if (approachSpeed < 0) {
    ball.vx -= 1.85 * approachSpeed * normalX;
    ball.vy -= 1.85 * approachSpeed * normalY;
  }
  ball.vx += normalX * 145;
  ball.vy += normalY * 145;
  pinballScore.value += bumper.score;
}

function losePinball() {
  pinballLives.value -= 1;
  if (pinballLives.value <= 0) {
    clearPinballTimers();
    pinballStatus.value = "gameover";
    return;
  }
  servePinballBall();
}

function stepPinball() {
  if (pinballStatus.value !== "running") return;
  const deltaSeconds = PINBALL_FRAME_MS / 1000;
  const ball = pinballBall.value;
  ball.vy += PINBALL_GRAVITY * deltaSeconds;
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
    ball.vy = Math.abs(ball.vy) * 0.86;
  }

  // 右侧黄铜导轨保留真实发射通道，只允许弹珠从导轨顶部切入主桌面。
  resolvePinballSegment({ x: 231, y: 66 }, { x: 231, y: 252 }, 2, 0.78);
  pinballBumpers.forEach(resolvePinballBumper);
  resolvePinballSegment(
    pinballLeftPivot,
    pinballFlipperTip("left"),
    5,
    0.9,
    pinballLeftPressed.value ? { x: 44, y: -150 } : undefined,
  );
  resolvePinballSegment(
    pinballRightPivot,
    pinballFlipperTip("right"),
    5,
    0.9,
    pinballRightPressed.value ? { x: -44, y: -150 } : undefined,
  );

  if (ball.y > PINBALL_HEIGHT + PINBALL_BALL_RADIUS) losePinball();
}

function togglePinballPause() {
  if (pinballStatus.value === "gameover") {
    resetPinball();
    return;
  }
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
    if (pinballStatus.value === "running") pinballStatus.value = "paused";
    clearPinballTimers();
  }
  activeGame.value = game;
  if (game === "tetris" && !tetrisPiece.value) startTetris();
  if (game === "snake" && snakeBody.value.length === 0) startSnake();
  if (game === "pinball" && pinballLives.value <= 0) resetPinball();
  void nextTick(() => panel.value?.focus());
}

function onPanelKeydown(event: KeyboardEvent) {
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
    :class="{ 'is-embedded': props.embedded }"
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
    </div>

    <template v-else>
      <nav class="pet-game-tabs" aria-label="小游戏切换">
        <button type="button" :class="{ 'is-active': activeGame === 'tetris' }" @click="selectGame('tetris')">俄罗斯方块</button>
        <button type="button" :class="{ 'is-active': activeGame === 'minesweeper' }" @click="selectGame('minesweeper')">扫雷</button>
        <button type="button" :class="{ 'is-active': activeGame === 'sudoku' }" @click="selectGame('sudoku')">数独</button>
        <button type="button" :class="{ 'is-active': activeGame === 'snake' }" @click="selectGame('snake')">贪吃蛇</button>
        <button type="button" :class="{ 'is-active': activeGame === 'pinball' }" @click="selectGame('pinball')">弹球</button>
      </nav>

      <div v-if="activeGame === 'tetris'" class="pet-tetris" data-testid="pet-tetris">
        <div class="pet-game-status-row">
          <span>{{ tetrisStatus }}</span>
          <span data-testid="pet-tetris-level">等级 {{ tetrisLevel }} · 速度 {{ tetrisTickMs }}ms</span>
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
          <span>难度 {{ mineLevel }} · 旗 {{ mineFlags }}/{{ mineCount }}</span>
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
          <span>难度 {{ sudokuLevel }} · 剩余 {{ sudokuRemaining }} 格</span>
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
          <span>等级 {{ snakeLevel }} · 得分 {{ snakeScore }}</span>
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

      <div v-else class="pet-pinball" data-testid="pet-pinball">
        <div class="pet-game-status-row">
          <span>{{ pinballStatusText }}</span>
          <span data-testid="pet-pinball-score">分数 {{ pinballScore.toString().padStart(5, "0") }}</span>
          <button type="button" aria-label="重开桌面弹球" @click="resetPinball"><RotateCcw :size="13" /></button>
        </div>
        <div
          class="pet-pinball-board"
          data-testid="pet-pinball-board"
          role="application"
          aria-label="桌面弹球台，空格蓄力发射，左右方向键控制挡板，P 键暂停"
        >
          <div class="pet-pinball-paper-score" aria-hidden="true">
            <span>DESK No. 05</span>
            <strong>{{ pinballScore.toString().padStart(5, "0") }}</strong>
          </div>
          <div class="pet-pinball-lives" aria-label="剩余弹珠">
            <span
              v-for="life in 3"
              :key="life"
              :class="{ 'is-spent': life > pinballLives }"
              aria-hidden="true"
            />
            <small>× {{ pinballLives }}</small>
          </div>
          <span class="pet-pinball-rail" aria-hidden="true" />
          <span class="pet-pinball-arch" aria-hidden="true" />
          <span class="pet-pinball-post is-left" aria-hidden="true" />
          <span class="pet-pinball-post is-right" aria-hidden="true" />
          <span
            v-for="(bumper, index) in pinballBumpers"
            :key="index"
            class="pet-pinball-bumper"
            :class="`is-${bumper.tone}`"
            :style="{ left: `${bumper.x}px`, top: `${bumper.y}px`, width: `${bumper.radius * 2}px`, height: `${bumper.radius * 2}px` }"
            aria-hidden="true"
          >
            <i />
            <small>+{{ bumper.score }}</small>
          </span>
          <span class="pet-pinball-flipper is-left" :class="{ 'is-pressed': pinballLeftPressed }" :style="pinballLeftFlipperStyle" aria-hidden="true" />
          <span class="pet-pinball-flipper is-right" :class="{ 'is-pressed': pinballRightPressed }" :style="pinballRightFlipperStyle" aria-hidden="true" />
          <span class="pet-pinball-drain" aria-hidden="true" />
          <span class="pet-pinball-ball" :style="pinballBallStyle" aria-hidden="true" />
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
              <i :style="pinballLauncherStyle" />
            </span>
            <small>{{ pinballCharging ? `${pinballCharge}%` : "发射" }}</small>
          </button>
          <div v-if="pinballStatus === 'paused' || pinballStatus === 'gameover'" class="pet-pinball-overlay" role="status">
            <strong>{{ pinballStatus === "gameover" ? "收工" : "暂停" }}</strong>
            <span>{{ pinballStatus === "gameover" ? `本局 ${pinballScore} 分` : "按 P 或继续返回球台" }}</span>
          </div>
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
          <button type="button" class="is-pause" @click="togglePinballPause">
            {{ pinballStatus === "running" ? "暂停" : pinballStatus === "gameover" ? "重开" : pinballStatus === "ready" ? "快速发射" : "继续" }}
          </button>
          <button
            type="button"
            aria-label="抬起右挡板"
            @pointerdown.prevent="setPinballFlipper('right', true)"
            @pointerup.prevent="setPinballFlipper('right', false)"
            @pointercancel.prevent="setPinballFlipper('right', false)"
            @pointerleave="setPinballFlipper('right', false)"
          >右挡板 → /</button>
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

.pet-game-choice.is-pinball {
  grid-column: 1 / -1;
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

.pet-pinball-board {
  position: relative;
  width: 274px;
  height: 326px;
  box-sizing: border-box;
  overflow: hidden;
  margin: 0 auto;
  border: 8px solid #6f4b32;
  border-radius: 28px 28px 17px 17px;
  background:
    radial-gradient(circle at 50% 43%, rgba(244, 225, 171, .09) 0 2px, transparent 3px),
    linear-gradient(155deg, #315654 0%, #274a49 55%, #1f403f 100%);
  box-shadow:
    inset 0 0 0 2px #c69b54,
    inset 0 0 28px rgba(7, 24, 24, .45),
    0 7px 13px rgba(72, 48, 31, .2);
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
  border: 1px solid rgba(236, 205, 134, .22);
  border-radius: 18px 18px 9px 9px;
}

.pet-pinball-board::after {
  left: 33px;
  right: 43px;
  bottom: -42px;
  height: 92px;
  border: 2px solid rgba(212, 175, 98, .48);
  border-radius: 50%;
  background: rgba(14, 39, 38, .55);
}

.pet-pinball-paper-score {
  position: absolute;
  z-index: 2;
  left: 27px;
  top: 15px;
  display: flex;
  min-width: 82px;
  flex-direction: column;
  gap: 1px;
  padding: 4px 6px 3px;
  border: 1px solid #b68c4b;
  border-radius: 2px;
  background: #f0e4c6;
  box-shadow: 2px 3px 0 rgba(22, 45, 44, .3);
  color: #65472f;
  transform: rotate(-1.5deg);
}

.pet-pinball-paper-score span {
  font-size: 6px;
  letter-spacing: .08em;
}

.pet-pinball-paper-score strong {
  color: #3f3830;
  font-size: 12px;
  letter-spacing: .12em;
  line-height: 13px;
}

.pet-pinball-lives {
  position: absolute;
  z-index: 2;
  right: 30px;
  top: 18px;
  display: flex;
  align-items: center;
  gap: 3px;
  color: #ead79e;
}

.pet-pinball-lives > span {
  width: 7px;
  height: 7px;
  border: 1px solid #c89b52;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, #fff9e8 0 18%, #b9c1bd 42%, #596b68 100%);
  box-shadow: 0 1px 2px rgba(9, 25, 24, .55);
}

.pet-pinball-lives > span.is-spent {
  background: transparent;
  box-shadow: none;
  opacity: .35;
}

.pet-pinball-lives small {
  margin-left: 2px;
  font-size: 7px;
}

.pet-pinball-rail {
  position: absolute;
  z-index: 1;
  left: 222px;
  top: 56px;
  width: 9px;
  height: 188px;
  border-left: 3px solid #c49a54;
  border-radius: 12px 0 0 12px;
  box-shadow: -2px 0 0 rgba(238, 210, 146, .22);
}

.pet-pinball-arch {
  position: absolute;
  z-index: 1;
  left: 17px;
  top: 47px;
  width: 215px;
  height: 72px;
  border-top: 3px solid rgba(201, 158, 81, .62);
  border-right: 3px solid rgba(201, 158, 81, .62);
  border-radius: 50% 46% 0 0;
  transform: rotate(-2deg);
}

.pet-pinball-post {
  position: absolute;
  z-index: 1;
  top: 214px;
  width: 7px;
  height: 53px;
  border-radius: 999px;
  background: linear-gradient(90deg, #8e6539, #e2c070 48%, #8e6539);
  box-shadow: 0 0 0 2px rgba(20, 51, 49, .55);
}

.pet-pinball-post.is-left { left: 48px; transform: rotate(-29deg); }
.pet-pinball-post.is-right { right: 52px; transform: rotate(29deg); }

.pet-pinball-bumper {
  position: absolute;
  z-index: 3;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  border: 4px solid #ead7a0;
  border-radius: 50%;
  transform: translate(-50%, -50%);
  box-shadow:
    0 0 0 2px #7c5a35,
    0 4px 7px rgba(8, 27, 26, .4),
    inset 0 0 0 2px rgba(255, 255, 255, .3);
}

.pet-pinball-bumper.is-rose { background: #c8665a; }
.pet-pinball-bumper.is-teal { background: #5da8a0; }
.pet-pinball-bumper.is-gold { background: #c99f52; }

.pet-pinball-bumper i {
  width: 30%;
  height: 30%;
  border-radius: 50%;
  background: rgba(255, 248, 222, .82);
  box-shadow: 0 0 7px rgba(255, 240, 183, .72);
}

.pet-pinball-bumper small {
  position: absolute;
  top: calc(100% + 6px);
  color: #ead8a7;
  font-size: 6px;
  letter-spacing: .03em;
  text-shadow: 0 1px 1px #183735;
}

.pet-pinball-flipper {
  position: absolute;
  z-index: 4;
  top: 268px;
  width: 58px;
  height: 12px;
  box-sizing: border-box;
  border: 2px solid #715135;
  border-radius: 999px;
  background: linear-gradient(180deg, #f2dd9f, #c69b50);
  box-shadow: 0 3px 4px rgba(11, 31, 30, .42), inset 0 2px 1px rgba(255, 248, 217, .5);
}

.pet-pinball-flipper::before {
  position: absolute;
  top: 2px;
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #754e32;
  content: "";
}

.pet-pinball-flipper.is-left {
  left: 78px;
  transform-origin: 6px 6px;
}

.pet-pinball-flipper.is-left::before { left: 2px; }

.pet-pinball-flipper.is-right {
  left: 138px;
  transform-origin: 52px 6px;
}

.pet-pinball-flipper.is-right::before { right: 2px; }

.pet-pinball-flipper.is-pressed {
  background: linear-gradient(180deg, #fff0bd, #d7ad58);
  box-shadow: 0 0 8px rgba(241, 205, 118, .4), 0 3px 4px rgba(11, 31, 30, .42);
}

.pet-pinball-ball {
  position: absolute;
  z-index: 6;
  width: 12px;
  height: 12px;
  border: 1px solid #655a45;
  border-radius: 50%;
  background: radial-gradient(circle at 33% 28%, #fff 0 14%, #d9dfdc 26%, #7d8d89 65%, #344946 100%);
  box-shadow: 1px 3px 4px rgba(8, 26, 25, .48);
  transform: translate(-50%, -50%);
  pointer-events: none;
}

.pet-pinball-drain {
  position: absolute;
  z-index: 2;
  left: 93px;
  bottom: -2px;
  width: 72px;
  height: 16px;
  border-radius: 50% 50% 0 0;
  background: #112e2d;
  box-shadow: 0 -2px 0 #bc8e4c;
}

.pet-pinball-plunger {
  position: absolute;
  z-index: 7;
  right: 6px;
  bottom: 9px;
  display: flex;
  width: 27px;
  height: 65px;
  flex-direction: column;
  align-items: center;
  justify-content: flex-end;
  gap: 3px;
  padding: 3px 2px;
  border: 1px solid rgba(227, 200, 134, .42);
  border-radius: 9px;
  background: rgba(18, 48, 46, .72);
  color: #ecd9a5;
  cursor: ns-resize;
}

.pet-pinball-plunger:disabled {
  cursor: default;
  opacity: .45;
}

.pet-pinball-plunger:not(:disabled):focus-visible {
  outline: 2px solid #f1d58d;
  outline-offset: 2px;
}

.pet-pinball-plunger-track {
  position: relative;
  display: flex;
  width: 8px;
  height: 44px;
  align-items: flex-end;
  justify-content: center;
  overflow: hidden;
  border: 1px solid #9b743e;
  border-radius: 999px;
  background: #173735;
}

.pet-pinball-plunger-track i {
  display: block;
  width: 6px;
  max-height: 42px;
  border-radius: 999px;
  background: repeating-linear-gradient(0deg, #e5c574 0 3px, #815b32 3px 5px);
}

.pet-pinball-plunger small {
  font-size: 6px;
  line-height: 8px;
  white-space: nowrap;
}

.pet-pinball-overlay {
  position: absolute;
  z-index: 9;
  inset: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  background: rgba(19, 42, 41, .68);
  color: #f1e2b9;
  backdrop-filter: blur(1px);
}

.pet-pinball-overlay strong {
  font-family: var(--ta-font-sans, "Noto Sans SC", sans-serif);
  font-size: 19px;
  letter-spacing: .16em;
}

.pet-pinball-overlay span { font-size: 8px; }

.pet-pinball-controls {
  display: grid;
  width: 274px;
  grid-template-columns: 1fr 68px 1fr;
  gap: 5px;
  margin: 8px auto 0;
}

.pet-pinball-controls button {
  min-width: 0;
  height: 28px;
  padding: 0 5px;
  border: 1px solid #c9b38c;
  border-radius: 7px;
  background: #fffaf0;
  color: #654d37;
  cursor: pointer;
  font-size: 8px;
  font-weight: 650;
}

.pet-pinball-controls button.is-pause {
  border-color: #aab9b8;
  background: #eff4f2;
  color: #315451;
}

.pet-pinball-controls button:hover,
.pet-pinball-controls button:focus-visible {
  border-color: #9e7945;
  outline: none;
  background: #f7ebd2;
}

@media (prefers-reduced-motion: reduce) {
  .pet-game-choice { transition: none; }
  .pet-game-choice:hover,
  .pet-game-choice:focus-visible { transform: none; }
}
</style>
