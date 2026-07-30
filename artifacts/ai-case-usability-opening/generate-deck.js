const pptxgen = require('pptxgenjs');
const sharp = require('sharp');
const path = require('path');

const pptx = new pptxgen();
pptx.layout = 'LAYOUT_WIDE';
pptx.author = 'MIMO 测试智能体课题组';
pptx.company = '测试效能团队';
pptx.subject = '面向开发变更的测试资产质量评估与优化 Agent 及 Skills';
pptx.title = '测试资产质量评估与优化 Agent｜开题汇报';
pptx.lang = 'zh-CN';
pptx.theme = {
  headFontFace: 'Arial',
  bodyFontFace: 'Arial',
  lang: 'zh-CN'
};
pptx.defineLayout({ name: 'CUSTOM_WIDE', width: 13.333, height: 7.5 });
pptx.layout = 'CUSTOM_WIDE';

const W = 13.333;
const H = 7.5;
const FONT = 'Arial';
const C = {
  ink: '0B132B',
  ink2: '17213D',
  slate: '43506B',
  muted: '70809D',
  fog: 'E9EEF5',
  wash: 'F5F8FC',
  white: 'FFFFFF',
  teal: '00A896',
  tealDark: '007F75',
  mint: 'DDF7F1',
  lime: 'B8F36B',
  limeSoft: 'EAF9D6',
  coral: 'FF715B',
  coralSoft: 'FFE7E2',
  amber: 'F4B942',
  amberSoft: 'FFF1CE',
  blue: '4D7CFE',
  blueSoft: 'E7EDFF',
  purple: '8A6FE8',
  purpleSoft: 'EEE9FF',
  red: 'D84654',
  green: '27A269',
  grayLine: 'D9E1EC'
};

const iconPaths = {
  folder: '<path d="M3 6.5h6l2 2H21v9.5a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/><path d="M3 10h18"/>',
  layers: '<path d="M12 3 3 8l9 5 9-5z"/><path d="m3 12 9 5 9-5"/><path d="m3 16 9 5 9-5"/>',
  spark: '<path d="m12 2 1.7 5.1L19 9l-5.3 1.9L12 16l-1.7-5.1L5 9l5.3-1.9z"/><path d="m19 15 .8 2.2L22 18l-2.2.8L19 21l-.8-2.2L16 18l2.2-.8z"/>',
  flow: '<circle cx="5" cy="5" r="2"/><circle cx="19" cy="5" r="2"/><circle cx="12" cy="19" r="2"/><path d="M7 5h10M6.5 7l4.4 9.7M17.5 7l-4.4 9.7"/>',
  chat: '<path d="M4 4h16v12H9l-5 4z"/><path d="M8 9h8M8 12h5"/>',
  git: '<circle cx="6" cy="4" r="2"/><circle cx="6" cy="20" r="2"/><circle cx="18" cy="8" r="2"/><path d="M6 6v12M8 6c2 4 8 1 8 4v0"/>',
  hub: '<circle cx="12" cy="12" r="3"/><circle cx="4" cy="5" r="2"/><circle cx="20" cy="5" r="2"/><circle cx="4" cy="19" r="2"/><circle cx="20" cy="19" r="2"/><path d="m9.8 9.8-4.4-3.6m8.8 3.6 4.4-3.6m-8.8 8-4.4 3.6m8.8-3.6 4.4 3.6"/>',
  check: '<circle cx="12" cy="12" r="9"/><path d="m8 12 2.7 2.7L16.5 9"/>',
  target: '<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1"/>',
  user: '<circle cx="12" cy="8" r="4"/><path d="M4.5 21c.8-5 3.3-7 7.5-7s6.7 2 7.5 7"/>',
  chart: '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2"/>',
  tag: '<path d="M3 4h8l10 10-7 7L4 11z"/><circle cx="8" cy="8" r="1.5"/>',
  database: '<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v7c0 1.7 3.6 3 8 3s8-1.3 8-3V5M4 12v7c0 1.7 3.6 3 8 3s8-1.3 8-3v-7"/>',
  link: '<path d="M9 15 7 17a4 4 0 1 1-6-6l3-3a4 4 0 0 1 6 0"/><path d="m15 9 2-2a4 4 0 1 1 6 6l-3 3a4 4 0 0 1-6 0"/><path d="m8 16 8-8"/>',
  alert: '<path d="M12 3 2.5 20h19z"/><path d="M12 9v5M12 17h.01"/>',
  clock: '<circle cx="12" cy="12" r="9"/><path d="M12 7v6l4 2"/>',
  shield: '<path d="M12 3 4 6v6c0 5 3.2 8 8 10 4.8-2 8-5 8-10V6z"/><path d="m9 12 2 2 4-5"/>',
  filter: '<path d="M3 5h18l-7 8v6l-4 2v-8z"/>',
  edit: '<path d="m14 5 5 5L9 20H4v-5z"/><path d="m13 6 5 5"/>',
  search: '<circle cx="10" cy="10" r="7"/><path d="m15 15 6 6"/>',
  refresh: '<path d="M20 7v5h-5M4 17v-5h5"/><path d="M6.1 8a8 8 0 0 1 13 1.5M17.9 16a8 8 0 0 1-13-1.5"/>',
  file: '<path d="M6 2h8l4 4v16H6z"/><path d="M14 2v5h5M9 12h6M9 16h6"/>',
  eye: '<path d="M2 12s4-7 10-7 10 7 10 7-4 7-10 7S2 12 2 12z"/><circle cx="12" cy="12" r="3"/>'
};

const iconCache = new Map();
async function iconData(name, color = C.ink, size = 256) {
  const key = `${name}-${color}-${size}`;
  if (iconCache.has(key)) return iconCache.get(key);
  const pathData = iconPaths[name] || iconPaths.check;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${size}" height="${size}" viewBox="0 0 24 24" fill="none" stroke="#${color}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">${pathData}</svg>`;
  const buf = await sharp(Buffer.from(svg)).png().toBuffer();
  const data = `data:image/png;base64,${buf.toString('base64')}`;
  iconCache.set(key, data);
  return data;
}

function tx(slide, text, x, y, w, h, opts = {}) {
  slide.addText(text, {
    x, y, w, h,
    fontFace: FONT,
    fontSize: 14,
    color: C.ink,
    margin: 0,
    breakLine: false,
    valign: 'mid',
    fit: 'shrink',
    ...opts
  });
}

function rect(slide, x, y, w, h, fill, radius = 0.12, line = null, transparency = 0) {
  slide.addShape(radius ? pptx.ShapeType.roundRect : pptx.ShapeType.rect, {
    x, y, w, h,
    rectRadius: radius,
    fill: { color: fill, transparency },
    line: line ? { color: line, width: 1 } : { color: fill, transparency: 100 }
  });
}

function circle(slide, x, y, d, fill, line = null, transparency = 0) {
  slide.addShape(pptx.ShapeType.ellipse, {
    x, y, w: d, h: d,
    fill: { color: fill, transparency },
    line: line ? { color: line, width: 1 } : { color: fill, transparency: 100 }
  });
}

function line(slide, x, y, w, h, color = C.grayLine, width = 1.3, dash = 'solid', arrow = null) {
  slide.addShape(pptx.ShapeType.line, {
    x, y, w, h,
    line: { color, width, dash, endArrowType: arrow || undefined }
  });
}

function shadow() {
  return { type: 'outer', color: '1B2A4A', opacity: 0.12, blur: 2, angle: 45, distance: 1.2 };
}

function card(slide, x, y, w, h, fill = C.white, border = null) {
  slide.addShape(pptx.ShapeType.roundRect, {
    x, y, w, h,
    rectRadius: 0.12,
    fill: { color: fill },
    line: border ? { color: border, width: 1 } : { color: fill, transparency: 100 },
    shadow: shadow()
  });
}

function pill(slide, text, x, y, w, fill, color = C.ink, fs = 11) {
  rect(slide, x, y, w, 0.34, fill, 0.17);
  tx(slide, text, x + 0.12, y + 0.02, w - 0.24, 0.29, { fontSize: fs, bold: true, color, align: 'center' });
}

async function iconBadge(slide, name, x, y, color = C.teal, fill = C.mint, d = 0.52) {
  circle(slide, x, y, d, fill);
  const data = await iconData(name, color);
  slide.addImage({ data, x: x + d * 0.23, y: y + d * 0.23, w: d * 0.54, h: d * 0.54 });
}

function footer(slide, n, source = '') {
  tx(slide, `MIMO 测试智能体 · 测试资产质量评估与优化 Agent`, 0.6, 7.12, 5.4, 0.2, { fontSize: 9, color: C.muted });
  if (source) tx(slide, source, 6.1, 7.12, 6.2, 0.2, { fontSize: 8.5, color: '8794AA', align: 'right' });
  tx(slide, String(n).padStart(2, '0'), 12.45, 7.08, 0.3, 0.25, { fontSize: 10, bold: true, color: C.slate, align: 'right' });
}

function titleBlock(slide, eyebrow, title, subtitle, n, source = '') {
  pill(slide, eyebrow, 0.6, 0.42, Math.max(1.1, eyebrow.length * 0.18 + 0.45), C.mint, C.tealDark, 10.5);
  tx(slide, title, 0.6, 0.88, 12.0, 0.58, { fontSize: 30, bold: true, color: C.ink, valign: 'top' });
  if (subtitle) tx(slide, subtitle, 0.62, 1.48, 11.8, 0.36, { fontSize: 13.2, color: C.muted, valign: 'top' });
  footer(slide, n, source);
}

function darkSlide(slide) {
  slide.background = { color: C.ink };
  circle(slide, 10.3, -0.9, 4.2, C.teal, null, 78);
  circle(slide, 10.95, -0.25, 2.9, C.lime, null, 86);
  circle(slide, -1.3, 5.65, 3.2, C.purple, null, 88);
}

function arrow(slide, x1, y1, x2, y2, color = C.teal) {
  line(slide, x1, y1, x2 - x1, y2 - y1, color, 1.7, 'solid', 'triangle');
}

function addNotes(slide, text) {
  slide.addNotes(text);
}

async function buildDeck() {
  // 01 Cover
  {
    const slide = pptx.addSlide();
    darkSlide(slide);
    pill(slide, '新员工实战课题 · 开题汇报', 0.72, 0.58, 2.42, C.lime, C.ink, 10.5);
    tx(slide, '测试资产质量评估\n与优化 Agent', 0.72, 1.15, 7.65, 1.85, { fontSize: 40, bold: true, color: C.white, breakLine: true, valign: 'top' });
    tx(slide, '面向开发变更，交付能真实运行的 Agent + Skills', 0.76, 3.18, 7.0, 0.5, { fontSize: 17.5, color: 'C9D3E7' });
    tx(slide, '基于现有 MIMO 测试智能体底座', 0.76, 3.72, 5.2, 0.35, { fontSize: 13, color: C.mint, bold: true });

    // Hero lens
    circle(slide, 8.45, 1.02, 3.92, '132342', '2A3E61');
    circle(slide, 8.91, 1.48, 3.0, '0B1833', C.teal);
    circle(slide, 9.37, 1.94, 2.08, C.white);
    rect(slide, 9.68, 2.18, 1.46, 1.55, C.wash, 0.12, C.grayLine);
    pill(slide, 'CASE-017', 9.87, 2.38, 1.05, C.blueSoft, C.blue, 9.3);
    tx(slide, '登录失败\n锁定校验', 9.86, 2.82, 1.1, 0.55, { fontSize: 13, bold: true, color: C.ink, align: 'center' });
    await iconBadge(slide, 'check', 10.12, 3.22, C.green, 'E1F6EB', 0.46);
    line(slide, 11.5, 4.73, 1.05, 1.12, C.teal, 8);
    circle(slide, 12.15, 5.45, 0.52, C.teal);

    const steps = [
      ['01', '变更', C.blueSoft, C.blue],
      ['02', '关联', C.mint, C.tealDark],
      ['03', '评估', C.purpleSoft, C.purple],
      ['04', '优化', C.limeSoft, C.green]
    ];
    steps.forEach((s, i) => {
      pill(slide, `${s[0]}  ${s[1]}`, 0.76 + i * 1.38, 5.7, 1.16, s[2], s[3], 10.5);
      if (i < steps.length - 1) arrow(slide, 1.93 + i * 1.38, 5.87, 2.08 + i * 1.38, 5.87, '7C8DAA');
    });
    tx(slide, '2026.07', 11.65, 6.85, 0.9, 0.25, { fontSize: 10, color: '8FA1BE', align: 'right' });
    addNotes(slide, '开场先讲清楚：本课题不交付看板或网页，而是在现有平台中建成一个能调用的测试资产质量评估与优化 Agent，以及它编排的可复用 Skills。');
  }

  // 02 One sentence
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '课题定义', '一句话：让 Agent 把开发变更转成可用的测试资产', '输入代码变更和现有案例，输出有证据的质量结论与可直接复核的优化资产。', 2);
    card(slide, 0.72, 2.08, 11.9, 2.0, C.ink);
    tx(slide, '“', 0.98, 2.14, 0.6, 0.85, { fontSize: 54, bold: true, color: C.lime, valign: 'top' });
    tx(slide, '代码变了，资产不一定必须改；\n但必须能说明“为何无需改，或准备怎么改”。', 1.62, 2.39, 10.15, 1.18, { fontSize: 24, bold: true, color: C.white, breakLine: true, valign: 'mid' });

    const qs = [
      ['资产能用吗？', 'A/B/C/D 可用性', C.mint, C.tealDark, 'check'],
      ['代码影响哪？', '路径 / 模块 / 功能', C.blueSoft, C.blue, 'target'],
      ['资产响应了吗？', '新增 / 修改 / 确认', C.purpleSoft, C.purple, 'refresh'],
      ['下一步怎么做？', '补充 / 修正 / 重新生成', C.coralSoft, C.red, 'edit']
    ];
    for (let i = 0; i < qs.length; i++) {
      const x = 0.72 + i * 3.02;
      card(slide, x, 4.45, 2.75, 1.65, C.white, C.grayLine);
      await iconBadge(slide, qs[i][4], x + 0.22, 4.68, qs[i][3], qs[i][2], 0.5);
      tx(slide, qs[i][0], x + 0.86, 4.66, 1.66, 0.34, { fontSize: 16, bold: true });
      tx(slide, qs[i][1], x + 0.22, 5.22, 2.25, 0.36, { fontSize: 12.5, color: C.muted });
    }
    pill(slide, '北极星：真实样例的结论可采信、优化结果可直接复核', 3.85, 6.38, 5.65, C.limeSoft, C.green, 10.7);
    addNotes(slide, '“能用”不是文件能被发现就算完成。Agent 必须在真实变更和案例上稳定运行，结论能追溯到代码证据，优化后案例能被测试人员直接复核。');
  }

  // 03 Existing base
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '01 · 已有底座', '我们不是从零开始', '当前平台已具备案例生成所需的上下文、运行、资产和工程化能力。', 3, '依据：module-map / user-manual / http-api（2026-07-30）');
    const items = [
      ['应用与工作区', '应用、版本、个人 workspace、需求子条目', 'folder', C.blue, C.blueSoft],
      ['上下文装配', '# 引入需求；@ 选择 Agent / 文件', 'layers', C.purple, C.purpleSoft],
      ['测试设计资产', '测试分析、案例生成、案例审核已沉淀', 'spark', C.tealDark, C.mint],
      ['会话与运行', 'Session / Run / RunEvent SSE 实时呈现', 'flow', C.coral, C.coralSoft],
      ['文件与版本', '编辑、Markdown 预览、Diff、Git 留痕', 'git', C.green, C.limeSoft],
      ['Agent & Skill Hub', '浏览、发布、引用、更新与修订管理', 'hub', C.amber, C.amberSoft]
    ];
    for (let i = 0; i < items.length; i++) {
      const row = Math.floor(i / 3);
      const col = i % 3;
      const x = 0.72 + col * 4.05;
      const y = 2.08 + row * 1.72;
      card(slide, x, y, 3.72, 1.42, C.white, C.grayLine);
      await iconBadge(slide, items[i][2], x + 0.23, y + 0.24, items[i][3], items[i][4], 0.56);
      tx(slide, items[i][0], x + 0.95, y + 0.22, 2.45, 0.34, { fontSize: 16.2, bold: true });
      tx(slide, items[i][1], x + 0.95, y + 0.65, 2.42, 0.52, { fontSize: 11.5, color: C.muted, valign: 'top' });
    }
    rect(slide, 0.72, 5.72, 11.87, 0.73, C.ink, 0.16);
    await iconBadge(slide, 'shield', 0.98, 5.84, C.lime, '21304D', 0.47);
    tx(slide, '公共地基', 1.57, 5.82, 1.05, 0.25, { fontSize: 11, bold: true, color: C.lime });
    tx(slide, '统一 API / traceId / 权限 · 只读源码快照身份 · 个人测试工作区 · 文件与版本治理', 2.58, 5.81, 8.9, 0.28, { fontSize: 12.4, color: C.white, bold: true });
    pill(slide, '课题只补“1 Agent + 4 Skills”', 9.95, 5.91, 2.33, C.lime, C.ink, 9.8);
    addNotes(slide, '底座已覆盖工作区、上下文、运行、文件与版本、Agent/Skill 发现与调用、Hub 发布与引用。新员工不做网页和平台 API，直接在现有配置与运行机制上交付 Agent 和 Skills。');
  }

  // 04 Existing case chain
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '01 · 已有底座', '现有案例生成链路已经能跑通', '从需求与设计输入，到测试分析、案例生成、审核和文件留痕，已有完整工作路径。', 4, '测试设计 workagent：分析 / 生成 / 审核均标记 implemented');
    const steps = [
      ['输入资产', '需求子条目\n详细设计\n规约 / 存量案例\n被测版本元数据', 'layers', C.blue, C.blueSoft],
      ['测试分析', '识别范围\n业务流程\n关键可测点', 'search', C.purple, C.purpleSoft],
      ['案例生成', '前置条件\n步骤 / 数据\n预期结果', 'spark', C.tealDark, C.mint],
      ['案例审核', '完整性\n一致性\n可执行性检查', 'check', C.green, C.limeSoft],
      ['产物沉淀', '案例文件\n预览 / 编辑\nDiff / Git', 'file', C.coral, C.coralSoft]
    ];
    for (let i = 0; i < steps.length; i++) {
      const x = 0.63 + i * 2.52;
      card(slide, x, 2.15, 2.12, 2.75, C.white, C.grayLine);
      pill(slide, `0${i + 1}`, x + 0.18, 2.34, 0.56, i === 4 ? C.coralSoft : C.wash, i === 4 ? C.red : C.slate, 10.5);
      await iconBadge(slide, steps[i][2], x + 1.33, 2.29, steps[i][3], steps[i][4], 0.56);
      tx(slide, steps[i][0], x + 0.18, 3.08, 1.72, 0.35, { fontSize: 17, bold: true });
      tx(slide, steps[i][1], x + 0.18, 3.62, 1.72, 0.98, { fontSize: 12.3, color: C.muted, breakLine: true, valign: 'top', lineSpacingMultiple: 1.02 });
      if (i < steps.length - 1) arrow(slide, x + 2.15, 3.52, x + 2.42, 3.52, C.teal);
    }

    card(slide, 1.05, 5.34, 11.0, 0.88, C.ink);
    tx(slide, '当前终点', 1.31, 5.56, 1.0, 0.26, { fontSize: 11, color: C.lime, bold: true });
    tx(slide, '“案例已经生成并保存”', 2.21, 5.47, 3.15, 0.42, { fontSize: 18, color: C.white, bold: true });
    arrow(slide, 5.62, 5.79, 6.38, 5.79, C.coral);
    tx(slide, '尚未回答：它到底好不好用？', 6.67, 5.47, 4.4, 0.42, { fontSize: 18, color: C.coral, bold: true });
    addNotes(slide, '强调现有链路的终点仍是“文件存在”。课题要把终点向后延伸：评价结果也要成为正式数据资产，并能回到生成能力的迭代中。');
  }

  // 05 Reuse map
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '02 · 交付形态', '最终交付：1 个编排 Agent + 4 个可复用 Skill', '不建看板、网页或专用 API；直接复用现有 Agent/Skill 加载、对话运行、文件产物和 Hub 发布能力。', 4);

    const layers = [
      ['主 Agent', 'Test Asset Quality Optimizer', '理解任务、编排 Skills、汇总结论、写入产物', C.purpleSoft, C.purple],
      ['Skill 01', 'change-impact-analysis', '读取版本与变更文件，提取影响模块、功能和证据', C.blueSoft, C.blue],
      ['Skill 02', 'asset-change-alignment', '关联受影响测试资产，识别未响应或无需调整项', C.mint, C.tealDark],
      ['Skill 03', 'test-asset-quality-review', '按准确性、完整性、可执行性和可维护性评估', C.amberSoft, '946A13'],
      ['Skill 04', 'test-asset-optimization', '产出优化建议、修订后案例和可复核 Diff', C.limeSoft, C.green]
    ];
    for (let i = 0; i < layers.length; i++) {
      const y = 1.98 + i * 0.88;
      rect(slide, 0.78, y, 11.75, 0.67, i === 2 ? C.ink : C.white, 0.13, i === 2 ? C.ink : C.grayLine);
      pill(slide, layers[i][0], 0.98, y + 0.16, 1.05, layers[i][3], layers[i][4], 10.2);
      tx(slide, layers[i][1], 2.27, y + 0.13, 3.05, 0.28, { fontSize: 13.3, bold: true, color: i === 2 ? C.white : C.ink });
      tx(slide, layers[i][2], 5.18, y + 0.13, 3.98, 0.3, { fontSize: 11.8, color: i === 2 ? 'CBD5E7' : C.muted });
    }
    rect(slide, 9.48, 2.2, 2.72, 3.02, C.ink2, 0.18);
    circle(slide, 10.02, 2.73, 1.64, '1D3151', C.teal);
    circle(slide, 10.39, 3.1, 0.9, C.teal);
    await iconBadge(slide, 'eye', 10.55, 3.26, C.ink, C.lime, 0.58);
    tx(slide, 'Agent + Skills', 9.72, 4.05, 2.2, 0.35, { fontSize: 16.5, color: C.white, bold: true, align: 'center' });
    tx(slide, '对话可调用 · 文件可落盘', 9.7, 4.5, 2.25, 0.3, { fontSize: 10.5, color: C.lime, bold: true, align: 'center' });
    pill(slide, '最终运行 = 输入变更与资产 → Agent 编排 4 Skills → 评估报告 + 优化资产', 3.22, 6.42, 7.0, C.ink, C.white, 9.7);
    addNotes(slide, '主 Agent 专注任务理解、调用顺序和产物收口；4 个 Skill 保持单一职责，既能被主 Agent 编排，也能被单独调用和测试。这样新员工的交付是可运行配置，不是原型或仅有提示词文档。');
  }

  // 06 Gap divider
  {
    const slide = pptx.addSlide();
    darkSlide(slide);
    pill(slide, '02 · 核心缺口', 0.72, 0.58, 1.62, C.coral, C.white, 10.5);
    tx(slide, '缺的不是另一个“生成”按钮，\n而是一条可信的评价闭环。', 0.72, 1.32, 8.8, 1.3, { fontSize: 35, bold: true, color: C.white, breakLine: true, valign: 'top' });

    const qs = [
      ['01', '可用性', '能否直接投入使用？'],
      ['02', '修改量', '需要人工改多少？'],
      ['03', '影响面', '哪些功能最容易出问题？'],
      ['04', '问题型', '错误集中在哪些类型？']
    ];
    qs.forEach((q, i) => {
      const x = 0.75 + i * 3.07;
      circle(slide, x, 3.62, 0.66, i === 3 ? C.coral : C.teal);
      tx(slide, q[0], x, 3.78, 0.66, 0.22, { fontSize: 10, bold: true, color: C.white, align: 'center' });
      tx(slide, q[1], x + 0.87, 3.6, 1.65, 0.32, { fontSize: 17, bold: true, color: C.white });
      tx(slide, q[2], x + 0.87, 4.03, 1.9, 0.56, { fontSize: 11.8, color: 'B7C3D9', valign: 'top' });
    });

    rect(slide, 0.73, 5.35, 11.83, 0.93, '14213B', 0.17, '273C5C');
    tx(slide, '没有统一口径', 1.0, 5.62, 1.78, 0.28, { fontSize: 15, bold: true, color: C.coral });
    tx(slide, '→ 反馈不可比', 2.72, 5.62, 1.64, 0.28, { fontSize: 14, color: C.white });
    tx(slide, '没有版本关联', 4.52, 5.62, 1.78, 0.28, { fontSize: 15, bold: true, color: C.coral });
    tx(slide, '→ 改进无法验证', 6.23, 5.62, 1.8, 0.28, { fontSize: 14, color: C.white });
    tx(slide, '没有证据钻取', 8.15, 5.62, 1.78, 0.28, { fontSize: 15, bold: true, color: C.coral });
    tx(slide, '→ 看板只剩“漂亮数字”', 9.85, 5.62, 2.3, 0.28, { fontSize: 14, color: C.white });
    tx(slide, '06', 12.45, 7.08, 0.3, 0.25, { fontSize: 10, bold: true, color: '8FA1BE', align: 'right' });
    addNotes(slide, '这一页是问题定义。评价闭环至少同时解决口径、版本和证据三个问题，否则只能得到不可比较的主观反馈，不能用于指导智能体迭代。');
  }

  // 07 Personas and moments
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '03 · 目标产品', '先分清两类人，再服务三类角色', '开发人员交付被测版本；测试人员在个人测试工作区评价、设计和执行。', 7, '结合 Codex 任务 019fae77… 的角色与工作区边界结论');
    const personas = [
      ['开发人员', '交付固定提交的只读源码快照；不进入测试人员个人工作区。', 'file', C.slate, C.fog],
      ['测试人员 / 审核人', '在个人测试工作区判断可用性，补充标签和说明。', 'user', C.tealDark, C.mint],
      ['测试负责人', '看团队质量趋势、功能风险和评价覆盖。', 'chart', C.blue, C.blueSoft],
      ['Agent / Skill 负责人', '定位高频问题，比较修订前后效果。', 'refresh', C.purple, C.purpleSoft]
    ];
    for (let i = 0; i < personas.length; i++) {
      const x = 0.72 + i * 3.03;
      card(slide, x, 2.0, 2.74, 1.45, C.white, C.grayLine);
      await iconBadge(slide, personas[i][2], x + 0.23, 2.22, personas[i][3], personas[i][4], 0.58);
      tx(slide, personas[i][0], x + 0.95, 2.18, 1.55, 0.34, { fontSize: 14.3, bold: true });
      tx(slide, personas[i][1], x + 0.95, 2.59, 1.55, 0.66, { fontSize: 10.2, color: C.muted, valign: 'top' });
    }

    // journey
    const moments = [
      ['生成完成', '弹出轻量评价入口', C.teal, 'spark'],
      ['人工修改', '自动记录前后差异', C.blue, 'edit'],
      ['每周复盘', '看趋势、问题与功能热区', C.purple, 'chart'],
      ['技能发布前', '对比修订前后质量', C.coral, 'refresh']
    ];
    line(slide, 1.38, 4.85, 10.55, 0, 'B8C6D9', 2);
    for (let i = 0; i < moments.length; i++) {
      const x = 1.12 + i * 3.15;
      circle(slide, x, 4.5, 0.7, moments[i][2]);
      const data = await iconData(moments[i][3], C.white);
      slide.addImage({ data, x: x + 0.18, y: 4.68, w: 0.34, h: 0.34 });
      tx(slide, moments[i][0], x - 0.45, 5.39, 1.6, 0.28, { fontSize: 14.5, bold: true, align: 'center' });
      tx(slide, moments[i][1], x - 0.75, 5.8, 2.2, 0.5, { fontSize: 11.3, color: C.muted, align: 'center', valign: 'top' });
    }
    pill(slide, '核心体验要求：评价动作 ≤ 30 秒', 4.64, 6.5, 4.05, C.limeSoft, C.green, 11.2);
    addNotes(slide, '先强调开发与测试是两类人：开发人员交付被测版本；测试人员在个人测试工作区评价和执行。评价入口要贴近案例生成完成的时刻；若流程太重，覆盖率会下降，因此建议单次评价控制在 30 秒内。');
  }

  // 08 Evaluation workbench mockup
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '04 · 运行效果', 'Agent 一次调用：产出评估报告和优化后资产', '用户在现有工作台选择 Agent、引入变更与案例；结果以 Markdown / JSON 文件落到测试工作区。', 6);

    // window shell
    card(slide, 0.55, 1.86, 12.23, 4.9, 'F8FAFD', C.grayLine);
    rect(slide, 0.55, 1.86, 12.23, 0.48, C.ink, 0.12);
    circle(slide, 0.77, 2.03, 0.11, C.coral);
    circle(slide, 0.96, 2.03, 0.11, C.amber);
    circle(slide, 1.15, 2.03, 0.11, C.teal);
    tx(slide, '@Test Asset Quality Optimizer · 支付应用 / gen-42 / commit abc123', 1.48, 1.98, 6.3, 0.18, { fontSize: 10.5, color: 'DDE6F3', bold: true });
    pill(slide, '4 Skills 已完成', 10.88, 1.94, 1.56, '203552', C.lime, 9.2);

    // left list
    rect(slide, 0.76, 2.53, 2.55, 3.92, C.white, 0.1, C.grayLine);
    tx(slide, '输入资料', 0.98, 2.72, 0.9, 0.26, { fontSize: 13.2, bold: true });
    pill(slide, '3 个文件', 2.13, 2.68, 0.91, C.wash, C.slate, 9.2);
    const cases = [
      ['CHANGE', 'changed-files.md', '✓', C.green, 'E1F6EB'],
      ['ASSET', 'TC-002.md', '✓', C.blue, C.blueSoft],
      ['DESIGN', 'detailed-design.md', '✓', C.green, C.limeSoft]
    ];
    cases.forEach((c, i) => {
      const y = 3.17 + i * 0.86;
      rect(slide, 0.9, y, 2.26, 0.68, i === 1 ? C.mint : C.wash, 0.09, i === 1 ? C.teal : C.wash);
      tx(slide, c[0], 1.04, y + 0.09, 0.73, 0.2, { fontSize: 9.8, bold: true, color: C.muted });
      tx(slide, c[1], 1.04, y + 0.31, 1.55, 0.22, { fontSize: 11.3, bold: i === 1 });
      pill(slide, c[2], 2.66, y + 0.17, 0.34, c[4], c[3], 9.2);
    });
    tx(slide, '任务：分析锁定阈值变更，评估并优化关联案例。', 0.98, 5.72, 2.0, 0.52, { fontSize: 9.2, color: C.muted, valign: 'top' });

    // center case
    rect(slide, 3.47, 2.53, 5.2, 3.92, C.white, 0.1, C.grayLine);
    pill(slide, 'AGENT ORCHESTRATION', 3.71, 2.72, 1.82, C.blueSoft, C.blue, 8.8);
    tx(slide, '锁定阈值变更影响分析', 3.71, 3.08, 4.56, 0.36, { fontSize: 16.2, bold: true });
    const fields = [
      ['变更证据', 'auth/lock-service.ts：失败阈值从 5 次调整为 3 次'],
      ['资产评估', 'TC-002 仍使用 5 次阈值；核心场景正确，数据与断言过期'],
      ['优化结果', '阈值更新为 3 次，补充第 4 次正确密码仍禁止登录的断言']
    ];
    fields.forEach((f, i) => {
      const y = 3.58 + i * 0.75;
      tx(slide, f[0], 3.72, y, 0.72, 0.23, { fontSize: 10.5, bold: true, color: C.tealDark });
      tx(slide, f[1], 4.53, y - 0.02, 3.73, 0.48, { fontSize: 10.8, color: C.slate, breakLine: true, valign: 'top' });
      if (i < 2) line(slide, 3.72, y + 0.55, 4.55, 0, C.fog, 1);
    });
    pill(slide, '结论 B · 小改可用', 3.72, 5.98, 1.56, C.amberSoft, '946A13', 9.0);
    tx(slide, 'change-impact → alignment → review → optimization', 5.45, 5.98, 2.78, 0.25, { fontSize: 8.9, color: C.muted });

    // evaluation drawer
    rect(slide, 8.84, 2.53, 3.72, 3.92, 'F2F7FB', 0.1, C.grayLine);
    tx(slide, '产出文件', 9.08, 2.74, 1.4, 0.3, { fontSize: 14.5, bold: true });
    pill(slide, '已落盘', 11.35, 2.71, 0.83, C.limeSoft, C.green, 9);
    tx(slide, '质量评估报告.md', 9.09, 3.27, 2.5, 0.28, { fontSize: 11.2, bold: true, color: C.slate });
    tx(slide, '等级、问题、代码证据、风险理由', 9.09, 3.61, 2.75, 0.32, { fontSize: 9.5, color: C.muted });
    line(slide, 9.09, 4.03, 2.85, 0, C.grayLine, 1);
    tx(slide, '优化后测试案例.md', 9.09, 4.18, 2.5, 0.28, { fontSize: 11.2, bold: true, color: C.slate });
    tx(slide, '保留原场景，修正过期数据与断言', 9.09, 4.52, 2.75, 0.32, { fontSize: 9.5, color: C.muted });
    line(slide, 9.09, 4.94, 2.85, 0, C.grayLine, 1);
    tx(slide, '处理记录.json', 9.09, 5.09, 2.5, 0.28, { fontSize: 11.2, bold: true, color: C.slate });
    tx(slide, '记录版本、Skill 结果、处置类型', 9.09, 5.43, 2.75, 0.32, { fontSize: 9.5, color: C.muted });
    pill(slide, '打开结果', 10.58, 6.01, 1.55, C.teal, C.white, 10.2);
    addNotes(slide, '这一页用一次真实调用说明“可用”。Agent 需要自动编排 4 个 Skill，将分析结论、代码证据和优化后案例写入测试工作区；不以聊天中有一段回答作为完成。');
  }

  // 09 Rubric
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '03 · 目标产品', '评价口径：统一四级可用性', '等级按“是否改变案例语义和可执行性”判断；修改比例只作为辅助证据。', 9);
    const grades = [
      ['A', '直接可用', '无需语义修改，可直接进入评审或执行。', C.green, 'E1F6EB'],
      ['B', '小改可用', '仅补充措辞、数据、局部步骤；核心场景不变。', C.blue, C.blueSoft],
      ['C', '大改可用', '需重构场景、步骤或预期结果后才能使用。', '9B7217', C.amberSoft],
      ['D', '不可用', '理解方向错误、无法执行，建议废弃并重写。', C.red, C.coralSoft]
    ];
    for (let i = 0; i < grades.length; i++) {
      const x = 0.72 + i * 3.02;
      card(slide, x, 2.06, 2.74, 2.57, C.white, C.grayLine);
      circle(slide, x + 0.22, 2.28, 0.72, grades[i][4]);
      tx(slide, grades[i][0], x + 0.22, 2.44, 0.72, 0.27, { fontSize: 22, bold: true, color: grades[i][3], align: 'center' });
      tx(slide, grades[i][1], x + 1.11, 2.32, 1.33, 0.34, { fontSize: 17, bold: true });
      tx(slide, grades[i][2], x + 0.24, 3.11, 2.2, 0.92, { fontSize: 12, color: C.muted, valign: 'top' });
      pill(slide, i === 0 ? '主指标' : i === 1 ? '可用' : i === 2 ? '重工' : '废弃', x + 0.24, 4.08, 0.8, grades[i][4], grades[i][3], 9.2);
    }

    card(slide, 0.72, 5.05, 7.5, 1.05, C.ink);
    await iconBadge(slide, 'shield', 0.98, 5.3, C.lime, '21304D', 0.5);
    tx(slide, '校准机制', 1.61, 5.23, 1.05, 0.28, { fontSize: 12, color: C.lime, bold: true });
    tx(slide, '先用 30 条样例做双人盲评；分歧案例形成判例库，持续更新口径说明。', 2.63, 5.2, 5.1, 0.46, { fontSize: 13.2, color: C.white, bold: true });
    card(slide, 8.52, 5.05, 4.07, 1.05, C.white, C.grayLine);
    tx(slide, '不建议', 8.8, 5.27, 0.7, 0.28, { fontSize: 11, bold: true, color: C.red });
    tx(slide, '仅用“满意 / 不满意”', 9.55, 5.21, 2.5, 0.3, { fontSize: 14, bold: true });
    tx(slide, '无法区分小修与推倒重来', 9.55, 5.56, 2.5, 0.25, { fontSize: 10.5, color: C.muted });
    addNotes(slide, '四级口径要先做校准。不能把“修改比例”直接当等级，因为少量关键逻辑修改可能影响很大；应以语义和可执行性为主，差异比例和耗时为辅。');
  }

  // 10 Taxonomy
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '03 · 目标产品', '问题标签：既能统计，也能行动', '首版控制在 8 个一级类型；允许多选，并保留“其他 + 补充说明”。', 10);
    const tags = [
      ['需求理解', '误解目标、约束或角色', C.purple, C.purpleSoft],
      ['范围与覆盖', '漏场景、边界或异常路径', C.blue, C.blueSoft],
      ['业务规则', '规则错误、冲突或过期', C.tealDark, C.mint],
      ['前置与数据', '条件不全、数据不可获得', '9B7217', C.amberSoft],
      ['步骤可执行性', '步骤跳跃、对象或操作不明确', C.coral, C.coralSoft],
      ['预期与断言', '结果模糊、不可验证或错误', C.red, 'FFE1E5'],
      ['冗余与粒度', '重复、过碎或场景混杂', C.green, C.limeSoft],
      ['格式与一致性', '字段缺失、命名或模板不一致', C.slate, C.fog]
    ];
    for (let i = 0; i < tags.length; i++) {
      const row = Math.floor(i / 4);
      const col = i % 4;
      const x = 0.72 + col * 3.03;
      const y = 2.02 + row * 1.55;
      card(slide, x, y, 2.73, 1.23, C.white, C.grayLine);
      pill(slide, String(i + 1).padStart(2, '0'), x + 0.2, y + 0.2, 0.5, tags[i][3], tags[i][2], 9.5);
      tx(slide, tags[i][0], x + 0.84, y + 0.17, 1.62, 0.3, { fontSize: 14.4, bold: true });
      tx(slide, tags[i][1], x + 0.2, y + 0.64, 2.22, 0.32, { fontSize: 10.5, color: C.muted });
    }
    card(slide, 0.72, 5.36, 11.84, 0.9, C.ink);
    await iconBadge(slide, 'target', 0.98, 5.55, C.lime, '21304D', 0.48);
    tx(slide, '影响功能单独记录', 1.6, 5.48, 1.68, 0.28, { fontSize: 13, bold: true, color: C.lime });
    tx(slide, '应用 → 模块 → 功能点（层级标签）', 3.35, 5.46, 3.2, 0.3, { fontSize: 14.2, bold: true, color: C.white });
    tx(slide, '问题类型解释“为什么差”，功能标签解释“差在哪里”。', 7.0, 5.47, 4.8, 0.3, { fontSize: 12.3, color: 'C9D3E7' });
    pill(slide, '其他 + 说明', 10.92, 6.45, 1.3, C.wash, C.slate, 9.3);
    addNotes(slide, '问题标签必须能映射到改进行动。例如“预期与断言”通常需要改模板或审核规则，“需求理解”可能需要补输入文档或知识，“范围与覆盖”可能要调整分析 Skill。');
  }

  // 11 Data model
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '03 · 目标产品', '数据模型：必须绑定“哪一版生成物”', '评价不是孤立打分；要能回到案例文本、生成上下文和 Agent / Skill 修订。', 11);

    const nodes = [
      ['被测源码版本', 'sourceGeneration\ntargetCommit', 0.82, 2.45, C.blueSoft, C.blue, 'file'],
      ['测试资产版本', 'testWorkspaceVersionId\ncaseVersionId / hash', 3.34, 2.45, C.mint, C.tealDark, 'layers'],
      ['人员与空间', 'userId\npersonalWorkspaceId', 5.86, 2.45, C.amberSoft, '9B7217', 'user'],
      ['人工评价', 'grade / tags / notes\nreviewer / evaluatedAt', 8.38, 2.45, C.limeSoft, C.green, 'check'],
      ['迭代对比', 'runId / agent\nskillRevision / model?', 10.9, 2.45, C.purpleSoft, C.purple, 'refresh']
    ];
    for (let i = 0; i < nodes.length; i++) {
      const n = nodes[i];
      card(slide, n[2], n[3], 1.75, 2.0, C.white, n[5]);
      await iconBadge(slide, n[6], n[2] + 0.2, n[3] + 0.21, n[5], n[4], 0.52);
      tx(slide, n[0], n[2] + 0.2, n[3] + 0.88, 1.3, 0.3, { fontSize: 14, bold: true });
      tx(slide, n[1], n[2] + 0.2, n[3] + 1.3, 1.35, 0.5, { fontSize: 9.8, color: C.muted, breakLine: true, valign: 'top' });
      if (i < nodes.length - 1) arrow(slide, n[2] + 1.77, 3.47, nodes[i + 1][2] - 0.08, 3.47, C.slate);
    }
    rect(slide, 1.24, 5.14, 10.86, 0.96, C.ink, 0.16);
    const guards = [
      ['三重身份', '源码 + 测试资产 + 人员空间'],
      ['版本不可漂移', '评价绑定 caseVersionId'],
      ['变更可审计', '保留操作者、时间与差异'],
      ['兼容可扩展', '上下文字段允许为空并 additive']
    ];
    guards.forEach((g, i) => {
      const x = 1.47 + i * 2.62;
      tx(slide, g[0], x, 5.33, 1.45, 0.24, { fontSize: 11, color: C.lime, bold: true });
      tx(slide, g[1], x, 5.63, 2.1, 0.22, { fontSize: 10.2, color: C.white });
    });
    addNotes(slide, '结合指定 Codex 任务的结论，评价要同时固化三重身份：被测源码 generation/targetCommit、测试资产与案例版本、测试人员及个人工作区。用户评价的是某一版案例，不是抽象 caseId；Run、Agent、Skill 修订等作为可扩展上下文。');
  }

  // 12 Dashboard mockup
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '04 · 页面效果', '质量看板：同时看“好不好用”和“有没有响应变更”', '下图为演示数据；核心是“指标 → 风险 → 优化动作 → 资产证据”的可钻取链路。', 7);
    card(slide, 0.55, 1.82, 12.23, 4.98, C.wash, C.grayLine);
    // filters
    rect(slide, 0.77, 2.02, 11.78, 0.48, C.white, 0.09, C.grayLine);
    await iconBadge(slide, 'filter', 0.93, 2.1, C.tealDark, C.mint, 0.3);
    tx(slide, '应用：全部', 1.38, 2.1, 0.95, 0.2, { fontSize: 9.2, bold: true });
    tx(slide, '被测版本：gen-42 / abc123', 2.45, 2.1, 1.95, 0.2, { fontSize: 9.2, bold: true });
    tx(slide, '测试资产：2026.07', 4.55, 2.1, 1.42, 0.2, { fontSize: 9.2, bold: true });
    tx(slide, '测试人员：全部', 6.15, 2.1, 1.25, 0.2, { fontSize: 9.2, bold: true });
    tx(slide, 'Skill 修订：v3', 7.58, 2.1, 1.2, 0.2, { fontSize: 9.2, bold: true });
    pill(slide, '演示数据', 11.3, 2.08, 0.92, C.amberSoft, '946A13', 8.8);

    const kpis = [
      ['46%', '直接可用率 A', C.green, C.limeSoft],
      ['72%', '资产变更响应率', C.tealDark, C.mint],
      ['12', '未响应变更风险', C.coral, C.coralSoft],
      ['18', '待优化测试资产', C.purple, C.purpleSoft]
    ];
    kpis.forEach((k, i) => {
      const x = 0.77 + i * 2.91;
      rect(slide, x, 2.67, 2.64, 0.86, C.white, 0.1, C.grayLine);
      tx(slide, k[0], x + 0.18, 2.79, 1.15, 0.34, { fontSize: 22, bold: true, color: k[2] });
      tx(slide, k[1], x + 1.22, 2.8, 1.18, 0.3, { fontSize: 10.3, bold: true, color: C.slate });
    });

    // trend panel
    rect(slide, 0.77, 3.72, 4.85, 2.72, C.white, 0.1, C.grayLine);
    tx(slide, '资产变更响应率趋势', 0.98, 3.91, 1.95, 0.25, { fontSize: 12.3, bold: true });
    tx(slide, '按生产版本', 4.32, 3.92, 0.98, 0.2, { fontSize: 9.2, color: C.muted, align: 'right' });
    // chart grid and line
    [0, 1, 2].forEach(i => line(slide, 1.12, 4.48 + i * 0.62, 4.12, 0, C.fog, 1));
    const pts = [[1.22,5.63],[2.0,5.35],[2.78,5.43],[3.56,4.91],[4.34,4.72],[5.04,4.47]];
    for (let i = 0; i < pts.length - 1; i++) line(slide, pts[i][0], pts[i][1], pts[i+1][0]-pts[i][0], pts[i+1][1]-pts[i][1], C.teal, 2.6);
    pts.forEach((p, i) => {
      circle(slide, p[0]-0.07, p[1]-0.07, 0.14, i === pts.length-1 ? C.lime : C.teal);
      tx(slide, `v${i+1}`, p[0]-0.2, 5.96, 0.4, 0.18, { fontSize: 8.2, color: C.muted, align: 'center' });
    });
    pill(slide, '72%', 4.72, 4.16, 0.54, C.limeSoft, C.green, 8.7);

    // problem bars
    rect(slide, 5.81, 3.72, 3.19, 2.72, C.white, 0.1, C.grayLine);
    tx(slide, '优化动作 Top 4', 6.02, 3.91, 1.6, 0.25, { fontSize: 12.3, bold: true });
    const probs = [['补充场景',0.82,27,C.purple],['修正断言',0.67,22,C.coral],['更新数据',0.55,18,C.amber],['重新生成',0.46,15,C.blue]];
    probs.forEach((p, i) => {
      const y = 4.42 + i * 0.48;
      tx(slide, p[0], 6.02, y, 0.75, 0.19, { fontSize: 9.4, color: C.slate });
      rect(slide, 6.82, y + 0.03, 1.48, 0.13, C.fog, 0.06);
      rect(slide, 6.82, y + 0.03, 1.48 * p[1], 0.13, p[3], 0.06);
      tx(slide, `${p[2]}%`, 8.36, y - 0.02, 0.38, 0.2, { fontSize: 9.2, bold: true, align: 'right' });
    });

    // heatmap
    rect(slide, 9.18, 3.72, 3.37, 2.72, C.white, 0.1, C.grayLine);
    tx(slide, '变更未响应热区', 9.4, 3.91, 1.6, 0.25, { fontSize: 12.3, bold: true });
    const funcs = ['登录', '支付', '对账', '通知'];
    const cats = ['未改', '待确认', '待补充'];
    funcs.forEach((f, r) => tx(slide, f, 9.4, 4.43 + r * 0.42, 0.48, 0.18, { fontSize: 9, color: C.slate }));
    cats.forEach((c, col) => tx(slide, c, 10.08 + col * 0.68, 4.22, 0.48, 0.16, { fontSize: 8.4, color: C.muted, align: 'center' }));
    const heat = [
      [C.coral, C.amberSoft, C.coralSoft], [C.purpleSoft, C.coral, C.amberSoft],
      [C.blueSoft, C.amberSoft, C.coralSoft], [C.mint, C.blueSoft, C.amberSoft]
    ];
    heat.forEach((row, r) => row.forEach((col, c) => rect(slide, 10.08 + c * 0.68, 4.42 + r * 0.42, 0.48, 0.25, col, 0.05)));
    pill(slide, '点击下钻 12 条风险', 9.43, 6.06, 2.24, C.ink, C.white, 9);
    addNotes(slide, '看板数据全部标成演示数据，避免被误认为生产结论。可用性指标说明资产好不好用；资产响应率说明受影响的开发变更是否已被更新或人工确认。任意指标都必须钻取到变更证据、资产版本和处置记录。');
  }

  // 13 Metrics
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '03 · 目标产品', '指标体系：主指标、诊断指标、过程指标', '生成数量不是质量；先回答“能否用”，再解释“为什么”和“反馈够不够”。', 13);
    const groups = [
      ['主指标', '直接可用率', 'A 级案例数 ÷ 已评价案例数', '判断智能体输出是否能直接进入后续环节', C.limeSoft, C.green, 'target'],
      ['诊断指标', '问题分布 / 修改量 / 功能热区', '按标签、应用、案例类型、Skill 修订切片', '告诉团队应该改规则、知识、模板还是生成策略', C.coralSoft, C.red, 'search'],
      ['过程指标', '评价覆盖率 / 评价时延', '已评价案例数 ÷ 待评价案例数', '判断看板结论是否有代表性，防止样本偏差', C.blueSoft, C.blue, 'clock']
    ];
    for (let i = 0; i < groups.length; i++) {
      const y = 2.0 + i * 1.43;
      card(slide, 0.72, y, 11.86, 1.12, C.white, C.grayLine);
      await iconBadge(slide, groups[i][6], 0.98, y + 0.27, groups[i][5], groups[i][4], 0.56);
      pill(slide, groups[i][0], 1.73, y + 0.25, 0.93, groups[i][4], groups[i][5], 9.7);
      tx(slide, groups[i][1], 2.91, y + 0.18, 2.62, 0.3, { fontSize: 16, bold: true });
      tx(slide, groups[i][2], 2.91, y + 0.58, 3.0, 0.26, { fontSize: 10.8, color: C.muted });
      tx(slide, groups[i][3], 6.35, y + 0.31, 5.55, 0.42, { fontSize: 12.3, color: C.slate, bold: true });
    }
    rect(slide, 0.72, 6.38, 11.86, 0.38, C.ink, 0.18);
    tx(slide, '反例', 0.98, 6.46, 0.48, 0.18, { fontSize: 9.5, bold: true, color: C.coral });
    tx(slide, '“本月生成 10,000 条案例”只能说明产量，不能说明可用性。', 1.55, 6.43, 7.25, 0.2, { fontSize: 11.3, color: C.white, bold: true });
    pill(slide, '所有比例必须显示样本量 n', 9.61, 6.4, 2.57, C.lime, C.ink, 9.5);
    addNotes(slide, '主指标建议先用“直接可用率”，它最能体现减少人工返工的价值；A+B 可用率可以作为辅助。任何百分比必须同时展示样本量和评价覆盖率。');
  }

  // 14 Development repository relationship
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '03 · Agent 判断', 'Agent 如何建立“变更—资产—优化”关系', '代码变更且关联资产未响应时生成关注项，再给出无需调整、更新、补充或重新生成建议。', 5, '结合 Codex 任务 019fae77… 的源码快照与人员工作区分析');

    // Development side
    card(slide, 0.66, 2.02, 3.32, 3.92, C.white, C.grayLine);
    pill(slide, '开发侧 · 生产变更', 0.9, 2.27, 1.55, C.blueSoft, C.blue, 9.5);
    await iconBadge(slide, 'file', 3.12, 2.22, C.blue, C.blueSoft, 0.54);
    tx(slide, '前后两版源码快照', 0.92, 2.86, 2.15, 0.34, { fontSize: 17, bold: true });
    tx(slide, 'generation / targetCommit\nselectedPaths / indexSha256\n变更文件清单（输入）', 0.92, 3.34, 2.18, 0.92, { fontSize: 11.0, color: C.muted, breakLine: true, valign: 'top' });
    pill(slide, '运行目录无 .git', 2.47, 4.18, 1.12, C.coralSoft, C.red, 8.8);
    tx(slide, '本周读取版本身份与已有/导入的变更文件清单；\n不在运行快照中恢复 .git，不开发完整 Diff 引擎。', 0.92, 4.62, 2.62, 0.82, { fontSize: 10.5, color: C.slate, breakLine: true, valign: 'top' });
    pill(slide, '已存在：快照身份', 0.92, 5.48, 1.47, C.mint, C.tealDark, 8.6);
    pill(slide, '本周：变更输入', 2.48, 5.48, 1.25, C.limeSoft, C.green, 8.6);

    // Bridge
    circle(slide, 4.37, 2.78, 2.48, C.ink);
    circle(slide, 4.82, 3.23, 1.58, '152B45', C.teal);
    await iconBadge(slide, 'link', 5.29, 3.7, C.ink, C.lime, 0.64);
    tx(slide, '变更—资产\n关联判断', 4.73, 4.54, 1.78, 0.58, { fontSize: 16.4, bold: true, color: C.white, align: 'center', breakLine: true });
    arrow(slide, 3.98, 3.98, 4.48, 3.98, C.blue);
    arrow(slide, 6.85, 3.98, 7.34, 3.98, C.teal);

    // Testing side
    card(slide, 7.36, 2.02, 5.3, 3.92, C.white, C.grayLine);
    pill(slide, '测试侧 · 质量资产', 7.62, 2.27, 1.55, C.mint, C.tealDark, 9.5);
    await iconBadge(slide, 'check', 11.82, 2.22, C.green, C.limeSoft, 0.54);
    tx(slide, '测试案例版本', 7.62, 2.86, 1.75, 0.34, { fontSize: 17, bold: true });
    tx(slide, 'caseVersionId / contentHash\n测试工作库版本 / personalWorkspaceId', 7.62, 3.32, 4.28, 0.58, { fontSize: 11.2, color: C.muted, breakLine: true, valign: 'top' });
    const links = [
      ['代码证据', 'paths / APIs / symbols'],
      ['影响功能', '应用 → 模块 → 功能点'],
      ['资产响应', '新增 / 修改 / 确认无需改'],
      ['风险状态', '未响应 / 处理中 / 已关闭']
    ];
    links.forEach((l, i) => {
      const col = i % 2;
      const row = Math.floor(i / 2);
      const x = 7.63 + col * 2.37;
      const y = 4.13 + row * 0.72;
      rect(slide, x, y, 2.12, 0.57, i === 3 ? C.limeSoft : C.wash, 0.09, i === 3 ? C.limeSoft : C.grayLine);
      tx(slide, l[0], x + 0.15, y + 0.09, 0.78, 0.2, { fontSize: 9.7, bold: true, color: i === 3 ? C.green : C.ink });
      tx(slide, l[1], x + 0.15, y + 0.31, 1.72, 0.17, { fontSize: 8.5, color: C.muted });
    });

    rect(slide, 0.75, 6.25, 11.82, 0.54, C.ink, 0.14);
    tx(slide, '分析价值', 1.0, 6.39, 0.74, 0.2, { fontSize: 10.5, bold: true, color: C.lime });
    tx(slide, '代码已变且资产未响应 → Agent 给出风险理由 → 产出处置建议和优化后资产', 1.86, 6.36, 10.25, 0.24, { fontSize: 10.8, bold: true, color: C.white });
    addNotes(slide, '关键口径：“代码变了、资产没变”是风险信号，不是自动判定资产有错。Agent 必须引用代码与资产证据，说明为何可以保留原资产，或生成可复核的修订结果。');
  }

  // 15 Feedback loop
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '04 · 价值闭环', '从看板回到智能体：让每次改进都可验证', '同一套口径比较修订前后，并用代码证据区分“生成问题”与“被测事实变化”。', 15);
    const stages = [
      ['1', '采集', 'A/B/C/D\n标签 + 说明', C.teal, 'check'],
      ['2', '诊断', 'Top 问题\n功能热区', C.blue, 'chart'],
      ['3', '改进', 'Skill / Rule\n知识 / 模板', C.purple, 'edit'],
      ['4', '发布', '新修订\n灰度或试点', C.coral, 'hub'],
      ['5', '验证', '同口径对比\n样本与置信度', C.green, 'refresh']
    ];
    for (let i = 0; i < stages.length; i++) {
      const x = 0.72 + i * 2.46;
      circle(slide, x + 0.58, 2.25, 1.16, stages[i][3]);
      const data = await iconData(stages[i][4], C.white);
      slide.addImage({ data, x: x + 0.91, y: 2.58, w: 0.5, h: 0.5 });
      circle(slide, x + 0.53, 2.2, 0.35, C.ink);
      tx(slide, stages[i][0], x + 0.53, 2.27, 0.35, 0.18, { fontSize: 9.5, bold: true, color: C.white, align: 'center' });
      tx(slide, stages[i][1], x + 0.23, 3.63, 1.85, 0.3, { fontSize: 16.2, bold: true, align: 'center' });
      tx(slide, stages[i][2], x + 0.14, 4.05, 2.03, 0.64, { fontSize: 11.3, color: C.muted, align: 'center', breakLine: true, valign: 'top' });
      if (i < stages.length - 1) arrow(slide, x + 1.8, 2.83, x + 2.33, 2.83, C.slate);
    }
    arrow(slide, 11.65, 5.07, 1.32, 5.07, C.teal);
    tx(slide, '持续循环', 6.03, 5.18, 1.25, 0.25, { fontSize: 11, bold: true, color: C.tealDark, align: 'center' });

    card(slide, 1.25, 5.72, 10.83, 0.74, C.ink);
    await iconBadge(slide, 'shield', 1.52, 5.85, C.lime, '21304D', 0.44);
    tx(slide, 'MVP 护栏', 2.08, 5.84, 0.85, 0.22, { fontSize: 10.5, bold: true, color: C.lime });
    tx(slide, '人工反馈不直接自动训练；先做脱敏、复核、样本校准与版本对比。', 2.96, 5.8, 6.15, 0.3, { fontSize: 12.5, color: C.white, bold: true });
    pill(slide, '证据优先', 10.08, 5.91, 1.35, C.lime, C.ink, 9.8);
    addNotes(slide, 'MVP 不要把范围扩成自动训练平台。先把高质量反馈数据收集起来，通过人工分析改 Skill、Rule、知识和模板，再用同一评价口径对比修订前后。');
  }

  // 16 MVP scope
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '04 · 一周任务', '必须完成：1 个 Agent、4 个 Skill、1 组真实验证', '每个能力都必须可发现、可调用、可独立测试；周五串成完整运行链路。', 7);
    const packages = [
      ['01', '主 Agent', '定义角色、输入、编排顺序、失败处理、落盘目录和输出契约。', 'flow', C.purple, C.purpleSoft, 'Agent 组'],
      ['02', '变更影响 Skill', '从版本身份与变更文件提取模块、功能、接口和代码证据。', 'search', C.blue, C.blueSoft, 'Skill A'],
      ['03', '资产关联 Skill', '根据功能、路径、接口和语义关联案例，识别变更未响应项。', 'link', C.tealDark, C.mint, 'Skill B'],
      ['04', '质量评估 Skill', '输出 A–D 等级、问题证据、影响面与处置建议。', 'check', C.green, C.limeSoft, 'Skill C'],
      ['05', '资产优化 Skill', '在不破坏原始意图的前提下修正、补充或重新生成案例。', 'edit', C.coral, C.coralSoft, 'Skill D'],
      ['06', '真实验证', '建立 30 组样例与期望结果，运行 Agent、回归 Skills、记录通过率。', 'target', '9B7217', C.amberSoft, '质量 + 全员']
    ];
    for (let i = 0; i < packages.length; i++) {
      const row = Math.floor(i / 3);
      const col = i % 3;
      const x = 0.72 + col * 4.04;
      const y = 1.98 + row * 2.0;
      card(slide, x, y, 3.72, 1.65, C.white, C.grayLine);
      pill(slide, packages[i][0], x + 0.2, y + 0.21, 0.52, packages[i][5], packages[i][4], 9.5);
      await iconBadge(slide, packages[i][3], x + 2.95, y + 0.19, packages[i][4], packages[i][5], 0.52);
      tx(slide, packages[i][1], x + 0.86, y + 0.19, 1.9, 0.32, { fontSize: 15.5, bold: true });
      tx(slide, packages[i][2], x + 0.2, y + 0.72, 3.05, 0.48, { fontSize: 10.6, color: C.muted, valign: 'top' });
      pill(slide, packages[i][6], x + 0.2, y + 1.28, 1.2, C.wash, C.slate, 8.6);
    }
    pill(slide, '全部完成才算交付：1 Agent + 4 Skills + 评估/优化文件产物 + 30 组验证样例', 3.14, 6.27, 7.05, C.ink, C.white, 10.0);
    addNotes(slide, '一周范围只包含这六个工作包。不建页面、不建专用 API、不建统计模型。必须使用平台实际 Agent/Skill 目录、真实模型和真实工作区运行，不把静态提示词或演示输出当成可用交付。');
  }

  // 17 Plan and team
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '04 · 一周任务', '任务拆分', '建议 5–7 人分成 Agent 编排、变更/关联 Skills、评估/优化 Skills、质量验收四条小线。', 8);
    const phases = [
      ['周一', '定契约', 'Agent 输入输出\n评估口径\n30 组样例', C.teal, C.mint],
      ['周二', '通前两技能', '变更影响\n资产关联\n独立调用', C.blue, C.blueSoft],
      ['周三', '通后两技能', '质量评估\n资产优化\n文件落盘', C.green, C.limeSoft],
      ['周四', '通 Agent', '编排 4 Skills\n真实模型运行\n修复失败样例', C.purple, C.purpleSoft],
      ['周五', '验收', '30 组全量运行\nSkill 回归\n演示答辩', C.coral, C.coralSoft]
    ];
    line(slide, 1.1, 2.86, 11.05, 0, C.grayLine, 5);
    phases.forEach((p, i) => {
      const x = 0.65 + i * 2.48;
      circle(slide, x + 0.69, 2.47, 0.78, p[3]);
      tx(slide, p[0], x + 0.69, 2.7, 0.78, 0.18, { fontSize: 9.2, bold: true, color: C.white, align: 'center' });
      card(slide, x, 3.34, 2.15, 1.53, C.white, C.grayLine);
      pill(slide, p[1], x + 0.22, 3.62, 1.0, p[4], p[3], 9.2);
      tx(slide, p[2], x + 0.22, 4.03, 1.66, 0.61, { fontSize: 10.5, color: C.slate, breakLine: true, valign: 'top' });
    });
    const roles = [
      ['Agent 编排', '契约、调度、落盘'], ['Skills A/B', '变更与关联'], ['Skills C/D', '评估与优化'], ['质量 / 测试', '样例、评测、验收']
    ];
    roles.forEach((r, i) => {
      const x = 0.85 + i * 3.0;
      rect(slide, x, 5.53, 2.55, 0.7, i === 0 ? C.ink : C.wash, 0.13, i === 0 ? C.ink : C.grayLine);
      tx(slide, r[0], x + 0.18, 5.66, 0.98, 0.22, { fontSize: 10.7, bold: true, color: i === 0 ? C.lime : C.ink });
      tx(slide, r[1], x + 1.06, 5.66, 1.25, 0.22, { fontSize: 9.8, color: i === 0 ? C.white : C.muted });
    });
    pill(slide, '每日 17:00 集成演示', 4.45, 6.48, 1.95, C.limeSoft, C.green, 9.8);
    pill(slide, '周四晚冻结功能，周五只修问题', 6.7, 6.48, 2.86, C.blueSoft, C.blue, 9.8);
    addNotes(slide, '一周实战每天 17 点集成一次。周二、周三分别以独立 Skill 调用为验收门槛；周四必须用主 Agent 在真实模型和工作区中编排全链路；周五只做全量样例、修复和答辩。');
  }

  // 18 Acceptance
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '05 · 最终验收', '答辩现场：让 Agent 处理一次真实开发变更', '不演示原型图；现场选择 Agent、引入代码与案例，运行 4 个 Skill，并打开落盘产物。', 9);
    const demo = [
      ['1', '准备输入', '版本身份、变更文件、现有案例', C.blue],
      ['2', '调用 Agent', '用自然语言下达评估与优化任务', C.teal],
      ['3', '编排 Skills', '展示变更、关联、评估和优化过程', C.green],
      ['4', '查看产物', '打开评估报告、优化案例和处理记录', C.purple],
      ['5', '人工复核', '校验代码证据、风险理由与案例可用性', C.coral]
    ];
    for (let i = 0; i < demo.length; i++) {
      const x = 0.71 + i * 2.48;
      circle(slide, x, 2.22, 0.58, demo[i][3]);
      tx(slide, demo[i][0], x, 2.38, 0.58, 0.18, { fontSize: 10.2, bold: true, color: C.white, align: 'center' });
      tx(slide, demo[i][1], x + 0.78, 2.24, 1.1, 0.28, { fontSize: 15.5, bold: true });
      tx(slide, demo[i][2], x + 0.78, 2.63, 1.37, 0.55, { fontSize: 10.5, color: C.muted, valign: 'top' });
      if (i < demo.length - 1) arrow(slide, x + 2.08, 2.51, x + 2.35, 2.51, C.slate);
    }

    card(slide, 0.72, 3.88, 11.86, 2.2, C.ink);
    tx(slide, '最小验收条件', 0.98, 4.15, 1.62, 0.3, { fontSize: 15.2, bold: true, color: C.lime });
    const checks = [
      '1 个主 Agent 能在平台被发现、选择和真实调用',
      '4 个 Skill 均可独立发现、调用和回归测试',
      '结论可追溯到变更文件、代码路径与资产版本',
      '代码已变但资产未响应时，能说明风险而非简单报错',
      '优化后案例以文件落盘，差异清晰且可人工复核',
      '30 组真实样例运行完成，失败项有记录、修复与回归'
    ];
    checks.forEach((c, i) => {
      const col = i % 2;
      const row = Math.floor(i / 2);
      const x = 1.0 + col * 5.75;
      const y = 4.68 + row * 0.42;
      circle(slide, x, y + 0.03, 0.22, C.lime);
      tx(slide, '✓', x, y + 0.07, 0.22, 0.12, { fontSize: 7.8, bold: true, color: C.ink, align: 'center' });
      tx(slide, c, x + 0.36, y, 5.02, 0.25, { fontSize: 11.3, color: C.white });
    });
    pill(slide, '最终交付 = 1 Agent + 4 Skills + 3 类落盘产物 + 30 组验证样例 + 1 条真实演示链路', 2.85, 6.43, 7.65, C.limeSoft, C.green, 9.8);
    addNotes(slide, '这一页必须回答“最终到底交付什么”。没有看板、网页、专用 API 和数据模型。交付的是可被平台加载和运行的 Agent/Skill 配置、落盘产物契约、真实样例评测结果和现场演示链路。');
  }

  // 19 Closing
  {
    const slide = pptx.addSlide();
    darkSlide(slide);
    pill(slide, '结论', 0.73, 0.63, 0.82, C.lime, C.ink, 10.5);
    tx(slide, '最终交付的不是一张报表，\n而是一套让智能体持续变好的质量闭环。', 0.73, 1.34, 9.85, 1.25, { fontSize: 34, bold: true, color: C.white, breakLine: true, valign: 'top' });
    const outcomes = [
      ['可评价', '每条反馈有口径、有版本、有证据', 'check', C.teal],
      ['可定位', '知道问题类型、影响功能和责任资产', 'target', C.purple],
      ['可验证', 'Skill / Rule 修订前后可量化比较', 'refresh', C.coral]
    ];
    for (let i = 0; i < outcomes.length; i++) {
      const o = outcomes[i];
      const x = 0.76 + i * 4.08;
      rect(slide, x, 3.33, 3.72, 1.42, '152442', 0.17, '2A3C5E');
      await iconBadge(slide, o[2], x + 0.24, 3.61, C.white, o[3], 0.54);
      tx(slide, o[0], x + 0.94, 3.53, 1.2, 0.34, { fontSize: 18, bold: true, color: C.white });
      tx(slide, o[1], x + 0.94, 3.96, 2.35, 0.5, { fontSize: 11.2, color: 'BBC8DC', valign: 'top' });
    }
    rect(slide, 0.76, 5.31, 11.83, 0.95, C.lime, 0.18);
    tx(slide, '第一周就做三件事', 1.05, 5.52, 1.72, 0.28, { fontSize: 13, bold: true, color: C.ink });
    tx(slide, '① 选 30 条真实案例   ② 完成双人评价校准   ③ 评审原型与数据字典', 2.89, 5.45, 8.7, 0.38, { fontSize: 16, bold: true, color: C.ink });
    tx(slide, 'Q & A', 11.2, 6.7, 1.1, 0.32, { fontSize: 14, bold: true, color: C.lime, align: 'right' });
    addNotes(slide, '收尾再次强调：看板只是闭环的可视化部分，真正的交付是统一评价口径、可追溯数据和可验证的智能体迭代机制。开题通过后，第一周立即开始样本与口径校准。');
  }

  // 一周实战版只保留 9 页：背景、底座、Agent/Skills 交付形态、变更关联、运行效果、任务、排期和验收。
  const compactSlideIndexes = [0, 1, 2, 4, 13, 7, 15, 16, 17];
  pptx._slides = compactSlideIndexes.map((index) => pptx._slides[index]);
  pptx._slides.forEach((slide, index) => {
    slide._name = `Slide ${index + 1}`;
    slide._rId = index + 2;
    slide._slideId = index + 256;
    slide._slideNum = index + 1;
  });

  const out = path.resolve(__dirname, '面向开发变更的测试资产质量评估与优化-开题汇报.pptx');
  await pptx.writeFile({ fileName: out });
  process.stdout.write(`${out}\n`);
}

buildDeck().catch((error) => {
  console.error(error);
  process.exit(1);
});
