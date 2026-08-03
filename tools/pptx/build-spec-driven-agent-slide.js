#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

/**
 * 优先复用项目已安装依赖；Codex 桌面环境未向仓库暴露 node_modules 时，
 * 允许通过自定义变量 CODEX_BUNDLED_NODE_MODULES 指向工作区内置运行时。
 */
function requireDependency(name) {
  try {
    return require(name);
  } catch (error) {
    const bundledModules = process.env.CODEX_BUNDLED_NODE_MODULES;
    if (!bundledModules) throw error;
    return require(path.join(bundledModules, name));
  }
}

const PptxGenJS = requireDependency("pptxgenjs");
const sharp = requireDependency("sharp");

const repositoryRoot = path.resolve(__dirname, "../..");
const outputFile = path.join(
  repositoryRoot,
  "docs/presentations/智能研发规范驱动测试智能体落地.pptx"
);

const pptx = new PptxGenJS();
pptx.layout = "LAYOUT_WIDE";
pptx.author = "RKK222";
pptx.company = "智能测试";
pptx.subject = "智能研发规范驱动范式在测试智能体的落地";
pptx.title = "2.2 智能研发 “规范驱动” 范式在测试智能体的落地";
pptx.lang = "zh-CN";
pptx.theme = {
  headFontFace: "PingFang SC",
  bodyFontFace: "PingFang SC",
  lang: "zh-CN"
};

const FONT = "PingFang SC";
const FONT_LATIN = "Arial";
const C = {
  red: "D40000",
  brightRed: "ED0000",
  softRed: "FFF0F0",
  paleRed: "FFD6D6",
  stackRed: "FFC1C1",
  stackTop: "FFDADA",
  ink: "202020",
  muted: "666666",
  lightMuted: "9A9A9A",
  border: "DFDFDF",
  divider: "E7E7E7",
  white: "FFFFFF"
};

const iconPaths = {
  user: '<circle cx="12" cy="7" r="4"/><path d="M4.5 21c.8-5 3.3-7 7.5-7s6.7 2 7.5 7"/>',
  cpu: '<rect x="7" y="7" width="10" height="10" rx="2"/><path d="M9 1v3M15 1v3M9 20v3M15 20v3M1 9h3M1 15h3M20 9h3M20 15h3"/><rect x="10" y="10" width="4" height="4" rx="1"/>',
  file: '<path d="M6 2h8l4 4v16H6z"/><path d="M14 2v5h5M9 12h6M9 16h6"/>',
  cloud: '<path d="M7 18h10a4 4 0 0 0 .8-7.9A6 6 0 0 0 6.4 8.5 4.5 4.5 0 0 0 7 18z"/><path d="m9 15 3-3 3 3M12 12v8"/>',
  window: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="M3 9h18M7 6.5h.01M10 6.5h.01"/>',
  wrench: '<path d="M14.7 6.3a5 5 0 0 0-6-6l3.1 3.1-3 3-3.1-3.1a5 5 0 0 0 6 6L20 17.6a2 2 0 1 1-2.8 2.8l-8.3-8.3"/>',
  box: '<path d="m12 2 9 5-9 5-9-5z"/><path d="m3 7 9 5 9-5v10l-9 5-9-5zM12 12v10"/>',
  table: '<rect x="3" y="3" width="18" height="18" rx="1"/><path d="M3 9h18M3 15h18M9 3v18M15 3v18"/>',
  route: '<circle cx="5" cy="5" r="2"/><circle cx="19" cy="5" r="2"/><circle cx="12" cy="19" r="2"/><path d="M7 5h10M6.5 7l4.4 9.7M17.5 7l-4.4 9.7"/>',
  database: '<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v7c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 12v7c0 1.7 3.6 3 8 3s8-1.3 8-3v-7"/>',
  grid: '<rect x="3" y="3" width="7" height="7"/><rect x="14" y="3" width="7" height="7"/><rect x="3" y="14" width="7" height="7"/><rect x="14" y="14" width="7" height="7"/>',
  code: '<path d="m8 9-4 3 4 3M16 9l4 3-4 3M14 5l-4 14"/>',
  clipboard: '<path d="M9 4h6l1 2h3v16H5V6h3z"/><rect x="9" y="2" width="6" height="4" rx="1"/><path d="M9 11h6M9 15h6"/>',
  wand: '<path d="m15 4 5 5L9 20l-5-5zM13 6l5 5M5 3v3M3.5 4.5h3M19 15v4M17 17h4"/>',
  shield: '<path d="M12 3 4 6v6c0 5 3.2 8 8 10 4.8-2 8-5 8-10V6z"/><path d="m9 12 2 2 4-5"/>',
  folder: '<path d="M3 6h6l2 2h10v11H3z"/><path d="M3 10h18"/>',
  plug: '<path d="M8 3v5M16 3v5M6 8h12v3a6 6 0 0 1-6 6v4M9 21h6"/>',
  tool: '<path d="M14.7 6.3a5 5 0 0 0-6-6l3.1 3.1-3 3-3.1-3.1a5 5 0 0 0 6 6L20 17.6a2 2 0 1 1-2.8 2.8l-8.3-8.3"/>',
  layers: '<path d="M12 3 3 8l9 5 9-5z"/><path d="m3 12 9 5 9-5M3 16l9 5 9-5"/>',
  sprout: '<path d="M12 22V10M12 14c-5 0-8-3-8-8 5 0 8 3 8 8zM12 10c5 0 8-3 8-8-5 0-8 3-8 8z"/>',
  terminal: '<rect x="3" y="4" width="18" height="16" rx="2"/><path d="m7 9 3 3-3 3M12 15h5"/>'
};

const iconCache = new Map();

/** 将线性图标统一渲染为高分辨率 PNG，保证 LibreOffice 与 PowerPoint 均稳定显示。 */
async function iconData(name, color = C.brightRed, size = 256) {
  const cacheKey = `${name}-${color}-${size}`;
  if (iconCache.has(cacheKey)) return iconCache.get(cacheKey);
  const pathData = iconPaths[name] || iconPaths.file;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="#${color}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${pathData}</svg>`;
  const buffer = await sharp(Buffer.from(svg)).png().toBuffer();
  const data = `data:image/png;base64,${buffer.toString("base64")}`;
  iconCache.set(cacheKey, data);
  return data;
}

function addText(slide, text, x, y, w, h, options = {}) {
  slide.addText(text, {
    x,
    y,
    w,
    h,
    fontFace: FONT,
    fontSize: 12,
    color: C.ink,
    margin: 0,
    breakLine: false,
    valign: "mid",
    ...options
  });
}

function addShape(slide, type, x, y, w, h, options = {}) {
  slide.addShape(type, {
    x,
    y,
    w,
    h,
    ...options
  });
}

function addLine(slide, x, y, w, h, options = {}) {
  addShape(slide, pptx.ShapeType.line, x, y, w, h, {
    line: { color: C.red, width: 1, ...options }
  });
}

function addCard(slide, x, y, w, h) {
  addShape(slide, pptx.ShapeType.roundRect, x, y, w, h, {
    rectRadius: 0.08,
    fill: { color: C.white },
    line: { color: C.border, width: 0.9 }
  });
}

function addPill(slide, text, x, y, w, h = 0.28, fontSize = 15) {
  addShape(slide, pptx.ShapeType.roundRect, x, y, w, h, {
    rectRadius: h / 2,
    fill: { color: C.brightRed },
    line: { color: C.brightRed, transparency: 100 }
  });
  addText(slide, text, x, y + 0.005, w, h - 0.01, {
    fontSize,
    bold: true,
    color: C.white,
    align: "center"
  });
}

async function addIcon(slide, name, x, y, w, h, color = C.brightRed) {
  slide.addImage({ data: await iconData(name, color), x, y, w, h });
}

/** 用原生形状绘制小型目录图标，左侧目录树保持可编辑。 */
function addFolderNodeIcon(slide, x, y, size = 0.12, color = C.brightRed) {
  addShape(slide, pptx.ShapeType.rect, x + 0.02, y, size * 0.55, size * 0.32, {
    fill: { color: C.white, transparency: 100 },
    line: { color, width: 0.8 }
  });
  addShape(slide, pptx.ShapeType.rect, x, y + size * 0.23, size, size * 0.7, {
    fill: { color: C.white, transparency: 100 },
    line: { color, width: 0.8 }
  });
}

function addFileNodeIcon(slide, x, y, size = 0.1, color = C.brightRed) {
  addShape(slide, pptx.ShapeType.rect, x, y, size * 0.78, size, {
    fill: { color: C.white, transparency: 100 },
    line: { color, width: 0.8 }
  });
}

function addTreeEntry(slide, level, y, text, options = {}) {
  const iconX = 0.84 + level * 0.29;
  const isFolder = options.folder !== false;
  if (isFolder) addFolderNodeIcon(slide, iconX, y + 0.02, 0.11);
  else addFileNodeIcon(slide, iconX + 0.01, y + 0.015, 0.1);
  addText(slide, text, iconX + 0.16, y, Math.max(0.55, 2.34 - level * 0.18), 0.16, {
    fontFace: FONT_LATIN,
    fontSize: options.fontSize || 8.7,
    bold: options.bold || false,
    color: options.color || C.ink,
    valign: "mid"
  });
}

/** 目录树连接线按当前公共/应用配置实际根层级分别绘制，避免把公共配置误画进应用 worktree。 */
function addTreeConnector(slide, x, y1, y2, branchYs, dashed = true) {
  addLine(slide, x, y1, 0.001, y2 - y1, {
    color: C.brightRed,
    width: 0.8,
    dash: dashed ? "dash" : "solid"
  });
  branchYs.forEach((branchY) => {
    addLine(slide, x, branchY, 0.12, 0.001, {
      color: C.brightRed,
      width: 0.8,
      dash: dashed ? "dash" : "solid"
    });
  });
}

function addScopeBracket(slide, y, h, title, subtitle) {
  const x = 3.18;
  addLine(slide, x, y, 0.001, h, { color: C.red, width: 1.2 });
  addLine(slide, x - 0.08, y, 0.08, 0.001, { color: C.red, width: 1.2 });
  addLine(slide, x - 0.08, y + h, 0.08, 0.001, { color: C.red, width: 1.2 });
  addLine(slide, x, y + h / 2, 0.14, 0.001, { color: C.red, width: 1.2 });
  addShape(slide, pptx.ShapeType.roundRect, 3.33, y + h / 2 - 0.22, 1.05, 0.44, {
    rectRadius: 0.06,
    fill: { color: C.softRed },
    line: { color: C.paleRed, width: 0.9 }
  });
  addText(slide, title, 3.38, y + h / 2 - 0.18, 0.95, 0.17, {
    fontSize: 10.4,
    bold: true,
    color: "B82D2D",
    align: "center"
  });
  addText(slide, subtitle, 3.38, y + h / 2 - 0.01, 0.95, 0.17, {
    fontSize: 9.2,
    bold: true,
    align: "center"
  });
}

function addDownArrow(slide, x, y1, y2) {
  addLine(slide, x, y1, 0.001, y2 - y1, {
    color: C.brightRed,
    width: 1.1,
    endArrowType: "triangle"
  });
}

/** 堆叠圆柱层由可编辑矩形与椭圆组合，替代整页截图。 */
function addCylinderLayer(slide, x, y, w, h, label) {
  addShape(slide, pptx.ShapeType.rect, x, y + 0.055, w, h - 0.11, {
    fill: { color: C.stackRed },
    line: { color: C.stackRed, transparency: 100 }
  });
  addShape(slide, pptx.ShapeType.ellipse, x, y, w, 0.13, {
    fill: { color: C.stackTop },
    line: { color: C.stackTop, transparency: 100 }
  });
  addShape(slide, pptx.ShapeType.ellipse, x, y + h - 0.13, w, 0.13, {
    fill: { color: C.stackRed },
    line: { color: C.stackRed, transparency: 100 }
  });
  addText(slide, label, x + 0.72, y + 0.095, w - 0.78, h - 0.15, {
    fontFace: FONT_LATIN,
    fontSize: 10.2,
    bold: true,
    color: C.brightRed,
    valign: "mid"
  });
}

function addCallout(slide, y, title, detail) {
  addLine(slide, 10.56, y, 0.9, 0.001, {
    color: C.brightRed,
    width: 1,
    dash: "dash"
  });
  addShape(slide, pptx.ShapeType.ellipse, 11.44, y - 0.025, 0.055, 0.055, {
    fill: { color: C.brightRed },
    line: { color: C.brightRed, transparency: 100 }
  });
  addText(slide, title, 11.55, y - 0.08, 1.02, 0.14, {
    fontSize: 7.8,
    bold: true,
    color: C.ink
  });
  addText(slide, detail, 11.55, y + 0.09, 1.05, 0.16, {
    fontSize: 5.8,
    color: C.muted,
    valign: "top"
  });
}

async function addBottomAdvantage(slide, x, icon, title, detail) {
  await addIcon(slide, icon, x + 0.12, 6.16, 0.23, 0.23);
  addText(slide, title, x + 0.47, 6.09, 1.58, 0.22, {
    fontSize: 10.2,
    bold: true
  });
  addText(slide, detail, x + 0.47, 6.36, 1.58, 0.17, {
    fontSize: 8,
    color: C.muted
  });
}

async function buildSlide() {
  const slide = pptx.addSlide();
  slide.background = { color: C.white };

  // 顶部标题与工行风格识别区。
  addShape(slide, pptx.ShapeType.triangle, 0, 0.14, 0.67, 0.88, {
    rotate: 90,
    fill: { color: C.red },
    line: { color: C.red, transparency: 100 }
  });
  addText(slide, "2.2 智能研发 “规范驱动” 范式在测试智能体的落地", 0.72, 0.31, 10.6, 0.52, {
    fontSize: 30,
    bold: true,
    color: C.red,
    valign: "mid",
    fit: "shrink"
  });
  addShape(slide, pptx.ShapeType.rect, 0.68, 1.02, 7.49, 0.08, {
    fill: { color: C.red },
    line: { color: C.red, transparency: 100 }
  });
  addShape(slide, pptx.ShapeType.rect, 8.17, 1.075, 4.57, 0.02, {
    fill: { color: C.red },
    line: { color: C.red, transparency: 100 }
  });
  addText(slide, "ICBC", 11.73, 0.66, 0.69, 0.26, {
    fontFace: FONT_LATIN,
    fontSize: 17,
    bold: true,
    color: "050505",
    align: "right"
  });
  addShape(slide, pptx.ShapeType.ellipse, 12.48, 0.63, 0.31, 0.31, {
    fill: { color: C.white },
    line: { color: C.red, width: 1.5 }
  });
  addText(slide, "工", 12.515, 0.67, 0.24, 0.2, {
    fontSize: 11,
    bold: true,
    color: C.red,
    align: "center"
  });

  // 三栏主体卡片。
  addCard(slide, 0.52, 1.65, 4.08, 4.12);
  addCard(slide, 4.71, 1.65, 3.67, 4.12);
  addCard(slide, 8.49, 1.65, 4.32, 4.12);
  addPill(slide, "标准化工作区目录", 1.74, 1.74, 1.78, 0.28, 15);
  addPill(slide, "执行链路", 5.85, 1.74, 1.45, 0.28, 15);
  addPill(slide, "SDD 规范驱动", 9.75, 1.74, 1.65, 0.28, 14.5);

  // 左栏：按当前真实结构区分公共 opencode/ 与应用 .opencode/，spec/docs 直接位于 workspace。
  addTreeEntry(slide, 0, 2.13, "public-config/", { bold: true, fontSize: 9.1 });
  addTreeEntry(slide, 1, 2.31, "opencode/", { bold: true, fontSize: 9.1 });
  addTreeEntry(slide, 2, 2.49, "agents/", { bold: true });
  addTreeEntry(slide, 3, 2.67, "test-design-agent.md", { folder: false, fontSize: 7.8 });
  addTreeEntry(slide, 3, 2.83, "test-execution-agent.md", { folder: false, fontSize: 7.8 });
  addTreeEntry(slide, 2, 3.00, "skills/", { bold: true });
  addTreeEntry(slide, 2, 3.17, "tools/", { bold: true });
  addTreeEntry(slide, 2, 3.34, "opencode.jsonc", { folder: false, fontSize: 8 });

  addTreeEntry(slide, 0, 3.57, "workspace/", { bold: true, fontSize: 9.1 });
  addTreeEntry(slide, 1, 3.75, ".opencode/", { bold: true, fontSize: 9.1 });
  addTreeEntry(slide, 2, 3.93, "agents/", { bold: true });
  addTreeEntry(slide, 3, 4.11, "test-design-agent.md", { folder: false, fontSize: 7.8 });
  addTreeEntry(slide, 3, 4.27, "test-execution-agent.md", { folder: false, fontSize: 7.8 });
  addTreeEntry(slide, 2, 4.44, "skills/", { bold: true });
  addTreeEntry(slide, 2, 4.61, "tools/", { bold: true });
  addTreeEntry(slide, 2, 4.78, "opencode.jsonc", { folder: false, fontSize: 8 });
  addTreeEntry(slide, 1, 5.06, "spec/", { bold: true });
  addTreeEntry(slide, 1, 5.28, "docs/", { bold: true });

  addTreeConnector(slide, 0.9, 2.25, 3.4, [2.31]);
  addTreeConnector(slide, 1.19, 2.43, 3.4, [2.49, 3.0, 3.17, 3.34]);
  addTreeConnector(slide, 1.48, 2.61, 2.89, [2.67, 2.83]);
  addTreeConnector(slide, 0.9, 3.69, 5.34, [3.75, 5.06, 5.28]);
  addTreeConnector(slide, 1.19, 3.87, 4.84, [3.93, 4.44, 4.61, 4.78]);
  addTreeConnector(slide, 1.48, 4.05, 4.33, [4.11, 4.27]);

  addScopeBracket(slide, 2.27, 1.14, "公共 Agent", "公共能力区");
  addScopeBracket(slide, 3.71, 1.14, "应用 Agent", "应用定制区");
  addScopeBracket(slide, 4.99, 0.36, "spec / docs", "应用资产区");

  // 中栏：用户指令经主控与专业子 Agent 进入标准工序和测试资产。
  addShape(slide, pptx.ShapeType.roundRect, 5.45, 2.11, 2.17, 0.31, {
    rectRadius: 0.05,
    fill: { color: C.softRed },
    line: { color: C.paleRed, width: 0.9 }
  });
  await addIcon(slide, "user", 6.07, 2.16, 0.17, 0.17);
  addText(slide, "用户指令", 6.34, 2.12, 0.78, 0.28, {
    fontSize: 12.5,
    bold: true
  });
  addDownArrow(slide, 6.535, 2.42, 2.57);

  addShape(slide, pptx.ShapeType.roundRect, 5.39, 2.58, 2.29, 0.35, {
    rectRadius: 0.12,
    fill: { color: C.brightRed },
    line: { color: C.brightRed, transparency: 100 }
  });
  await addIcon(slide, "cpu", 5.66, 2.66, 0.18, 0.18, C.white);
  addText(slide, "Master 主控智能体", 5.9, 2.59, 1.55, 0.33, {
    fontSize: 11.5,
    bold: true,
    color: C.white,
    align: "center",
    fit: "shrink"
  });
  addDownArrow(slide, 6.535, 2.93, 3.1);

  addShape(slide, pptx.ShapeType.roundRect, 5.21, 3.11, 2.65, 0.75, {
    rectRadius: 0.05,
    fill: { color: C.softRed },
    line: { color: C.paleRed, width: 0.9 }
  });
  addText(slide, "专业子智能体", 5.72, 3.18, 1.62, 0.21, {
    fontSize: 12,
    bold: true,
    align: "center"
  });
  const childAgents = [
    { x: 5.55, icon: "file", label: "test-design-agent", labelX: 5.27, labelW: 0.76 },
    { x: 6.39, icon: "cloud", label: "api-agent", labelX: 6.12, labelW: 0.75 },
    { x: 7.18, icon: "window", label: "ui-agent", labelX: 6.99, labelW: 0.58 }
  ];
  for (const agent of childAgents) {
    await addIcon(slide, agent.icon, agent.x, 3.42, 0.2, 0.2);
    addText(slide, agent.label, agent.labelX, 3.65, agent.labelW, 0.13, {
      fontFace: FONT_LATIN,
      fontSize: 6.5,
      align: "center",
      fit: "shrink"
    });
  }
  addDownArrow(slide, 6.535, 3.86, 4.0);

  addShape(slide, pptx.ShapeType.roundRect, 5.21, 4.01, 2.65, 0.29, {
    rectRadius: 0.04,
    fill: { color: C.softRed },
    line: { color: C.paleRed, width: 0.9 }
  });
  await addIcon(slide, "wrench", 5.69, 4.06, 0.18, 0.18);
  addText(slide, "Skill 标准工序", 6.01, 4.01, 1.25, 0.28, {
    fontSize: 12,
    bold: true,
    align: "center"
  });
  addDownArrow(slide, 6.535, 4.3, 4.43);

  addShape(slide, pptx.ShapeType.roundRect, 5.21, 4.44, 2.65, 0.29, {
    rectRadius: 0.04,
    fill: { color: C.softRed },
    line: { color: C.paleRed, width: 0.9 }
  });
  await addIcon(slide, "box", 5.72, 4.49, 0.17, 0.17);
  addText(slide, "标准测试资产", 6.04, 4.44, 1.16, 0.28, {
    fontSize: 12,
    bold: true,
    align: "center"
  });
  addDownArrow(slide, 6.535, 4.73, 4.88);

  addShape(slide, pptx.ShapeType.roundRect, 4.86, 4.89, 3.35, 0.66, {
    rectRadius: 0.05,
    fill: { color: C.white },
    line: { color: C.brightRed, width: 1, dash: "dash" }
  });
  const resources = [
    { icon: "table", label: "案例表格" },
    { icon: "route", label: "路径图" },
    { icon: "database", label: "测试数据" },
    { icon: "grid", label: "自动化脚本" },
    { icon: "code", label: "Mock/断言" }
  ];
  for (let i = 0; i < resources.length; i += 1) {
    const itemX = 4.99 + i * 0.64;
    if (i > 0) addLine(slide, itemX - 0.09, 5.04, 0.001, 0.28, { color: C.divider, width: 0.7 });
    await addIcon(slide, resources[i].icon, itemX + 0.13, 5.02, 0.19, 0.19);
    addText(slide, resources[i].label, itemX, 5.31, 0.54, 0.13, {
      fontSize: 7.3,
      bold: true,
      color: C.muted,
      align: "center"
    });
  }

  // 右栏：Repo-Wiki 已移除，增加 MCP 与 Tools 两个独立能力层。
  const stack = [
    { label: "SOP", icon: "clipboard", title: "定义流程与步骤", detail: "需求解析 → 功能点识别 → 场景生成 → 结构化输出" },
    { label: "Skill", icon: "wand", title: "固化方法与工序", detail: "需求理解、场景设计、数据构造、脚本生成" },
    { label: "Rule", icon: "shield", title: "约束规则与标准", detail: "交互、异常、边界值与历史缺陷约束" },
    { label: "Spec", icon: "file", title: "承载任务与规格", detail: "需求文档、详细设计与接口文档" },
    { label: "Template", icon: "folder", title: "统一格式与模板", detail: "case_id、scenario、steps、test_data、expected" },
    { label: "MCP", icon: "plug", title: "接入外部能力", detail: "标准协议连接代码库、数据库与平台服务" },
    { label: "Tools", icon: "tool", title: "封装可复用操作", detail: "HTTP、RPC、数据库、UI 执行等原子工具" },
    { label: "Docs", icon: "file", title: "过程资料与证据", detail: "历史问题、存量案例、公共案例与执行证据" }
  ];
  for (let i = 0; i < stack.length; i += 1) {
    const y = 2.36 + i * 0.4;
    addCylinderLayer(slide, 8.78, y, 1.68, 0.43, stack[i].label);
    await addIcon(slide, stack[i].icon, 9.35, y + 0.13, 0.16, 0.16);
    addCallout(slide, y + 0.22, stack[i].title, stack[i].detail);
  }

  // 底部优势条。
  addShape(slide, pptx.ShapeType.roundRect, 0.52, 6.0, 12.29, 0.72, {
    rectRadius: 0.06,
    fill: { color: C.white },
    line: { color: C.border, width: 0.8 }
  });
  addShape(slide, pptx.ShapeType.roundRect, 0.62, 6.12, 1.34, 0.46, {
    rectRadius: 0.08,
    fill: { color: C.brightRed },
    line: { color: C.brightRed, transparency: 100 }
  });
  addText(slide, "目录结构优势", 0.68, 6.16, 1.22, 0.33, {
    fontSize: 11.5,
    bold: true,
    color: C.white,
    align: "center",
    fit: "shrink"
  });
  const advantageStart = 2.03;
  const cellWidth = 2.155;
  for (let i = 0; i < 5; i += 1) {
    addLine(slide, advantageStart + i * cellWidth, 6.0, 0.001, 0.72, {
      color: C.divider,
      width: 0.8
    });
  }
  await addBottomAdvantage(slide, 2.03, "layers", "平台先交付公共能力", "开箱可用");
  await addBottomAdvantage(slide, 4.185, "shield", "扩展但边界可控", "不破坏主干");
  await addBottomAdvantage(slide, 6.34, "sprout", "为未来演进预留空间", "支持定制");
  await addBottomAdvantage(slide, 8.495, "folder", "输入与知识资产统一管理", "统一沉淀");
  await addBottomAdvantage(slide, 10.65, "terminal", "支持平滑迁移到 IDE", "复用既有资产");

  // 页脚与页码保持原稿风格。
  addText(slide, "中国工商银行版权所有", 0.35, 7.1, 2.0, 0.2, {
    fontSize: 11.5,
    color: "A6A6A6"
  });
  addShape(slide, pptx.ShapeType.rtTriangle, 12.62, 6.82, 0.71, 0.68, {
    rotate: 270,
    fill: { color: C.brightRed },
    line: { color: C.brightRed, transparency: 100 }
  });
  addText(slide, "8", 12.98, 7.13, 0.19, 0.2, {
    fontFace: FONT_LATIN,
    fontSize: 16,
    color: C.white,
    align: "center"
  });

  slide.addNotes(
    "按当前平台结构复原：公共 Agent 使用公共配置 opencode/，应用 Agent 使用工作区 .opencode/；content 层已移除，spec/docs 直接位于 workspace；原知识库层已移除，SDD 能力栈增加 MCP 与 Tools。"
  );
}

async function main() {
  fs.mkdirSync(path.dirname(outputFile), { recursive: true });
  await buildSlide();
  await pptx.writeFile({ fileName: outputFile });
  process.stdout.write(`${outputFile}\n`);
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error.message}\n`);
  process.exitCode = 1;
});
