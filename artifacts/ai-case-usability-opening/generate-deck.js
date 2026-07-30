const pptxgen = require('pptxgenjs');
const sharp = require('sharp');
const path = require('path');

const pptx = new pptxgen();
pptx.layout = 'LAYOUT_WIDE';
pptx.author = 'MIMO 测试智能体课题组';
pptx.company = '测试效能团队';
pptx.subject = 'AI 生成测试案例的可用性评价、问题标注、质量统计与智能体迭代闭环';
pptx.title = 'AI 案例可用性评估看板｜开题汇报';
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
  tx(slide, `MIMO 测试智能体 · AI 案例可用性评估看板`, 0.6, 7.12, 5.4, 0.2, { fontSize: 9, color: C.muted });
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
    tx(slide, 'AI 案例\n可用性评估看板', 0.72, 1.18, 7.4, 1.75, { fontSize: 46, bold: true, color: C.white, breakLine: true, valign: 'top' });
    tx(slide, '从“会生成”走向“可衡量、可定位、可改进”', 0.76, 3.1, 6.7, 0.5, { fontSize: 19, color: 'C9D3E7' });
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
      ['01', '生成', C.blueSoft, C.blue],
      ['02', '评价', C.mint, C.tealDark],
      ['03', '洞察', C.purpleSoft, C.purple],
      ['04', '优化', C.limeSoft, C.green]
    ];
    steps.forEach((s, i) => {
      pill(slide, `${s[0]}  ${s[1]}`, 0.76 + i * 1.38, 5.7, 1.16, s[2], s[3], 10.5);
      if (i < steps.length - 1) arrow(slide, 1.93 + i * 1.38, 5.87, 2.08 + i * 1.38, 5.87, '7C8DAA');
    });
    tx(slide, '2026.07', 11.65, 6.85, 0.9, 0.25, { fontSize: 10, color: '8FA1BE', align: 'right' });
    addNotes(slide, '开场先讲清楚：这个课题不是再做一个案例生成器，而是给现有生成能力补上质量反馈闭环。整场汇报围绕“已有底座、关键缺口、目标效果、实施与验收”四部分展开。');
  }

  // 02 One sentence
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '课题定义', '先把课题讲成一句话', '把每一批 AI 案例的人类反馈，沉淀成可追溯、可统计、可行动的质量证据。', 2);
    card(slide, 0.72, 2.08, 11.9, 2.0, C.ink);
    tx(slide, '“', 0.98, 2.14, 0.6, 0.85, { fontSize: 54, bold: true, color: C.lime, valign: 'top' });
    tx(slide, '会生成，只代表有产物；\n能被稳定评价，才代表质量可管理。', 1.62, 2.43, 9.8, 1.08, { fontSize: 26, bold: true, color: C.white, breakLine: true, valign: 'mid' });

    const qs = [
      ['能否直接用？', 'A/B/C/D 可用性', C.mint, C.tealDark, 'check'],
      ['改了多少？', '修改量与耗时', C.blueSoft, C.blue, 'edit'],
      ['影响哪里？', '应用 / 功能标签', C.purpleSoft, C.purple, 'target'],
      ['问题在哪？', '问题类型与证据', C.coralSoft, C.red, 'tag']
    ];
    for (let i = 0; i < qs.length; i++) {
      const x = 0.72 + i * 3.02;
      card(slide, x, 4.45, 2.75, 1.65, C.white, C.grayLine);
      await iconBadge(slide, qs[i][4], x + 0.22, 4.68, qs[i][3], qs[i][2], 0.5);
      tx(slide, qs[i][0], x + 0.86, 4.66, 1.66, 0.34, { fontSize: 16, bold: true });
      tx(slide, qs[i][1], x + 0.22, 5.22, 2.25, 0.36, { fontSize: 12.5, color: C.muted });
    }
    pill(slide, '北极星：直接可用率持续提升', 4.66, 6.38, 4.02, C.limeSoft, C.green, 11.5);
    addNotes(slide, '建议让新员工记住这句话。课题的业务价值不是“收集评价”本身，而是把评价转成能驱动技能、规约、知识和提示词改进的证据。');
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
    pill(slide, '课题只补“质量域”', 10.3, 5.91, 1.98, C.lime, C.ink, 10.2);
    addNotes(slide, '这一页用于建立信心：底座能力已经覆盖了工作区、上下文、运行、产物、版本和资产运营。新员工不用重做聊天、编辑器或 Git，只需把精力集中在案例质量域。');
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
    titleBlock(slide, '01 · 已有底座', '复用什么，新增什么', '新功能嵌入现有分层：浏览器只访问平台 API，评估数据进入平台持久化与权限体系。', 5);

    const layers = [
      ['体验层', 'agent-web', '评价入口 · 案例详情 · 统计看板', C.blueSoft, C.blue],
      ['平台 API', 'backend-api + test-agent-api', '统一响应 · 鉴权 · traceId · 分页筛选', C.mint, C.tealDark],
      ['质量业务', '新增案例质量域', '评价口径 · 标签体系 · 指标计算 · 钻取', C.limeSoft, C.green],
      ['运行与资产', 'Run / Workspace / Snapshot / Hub', '关联生成上下文、源码快照、个人空间与技能修订', C.purpleSoft, C.purple],
      ['数据层', 'domain + persistence', 'MyBatis XML · Flyway · 审计字段', C.coralSoft, C.red]
    ];
    for (let i = 0; i < layers.length; i++) {
      const y = 1.98 + i * 0.88;
      rect(slide, 0.78, y, 11.75, 0.67, i === 2 ? C.ink : C.white, 0.13, i === 2 ? C.ink : C.grayLine);
      pill(slide, layers[i][0], 0.98, y + 0.16, 1.05, layers[i][3], layers[i][4], 10.2);
      tx(slide, layers[i][1], 2.27, y + 0.13, 3.05, 0.28, { fontSize: 13.3, bold: true, color: i === 2 ? C.white : C.ink });
      tx(slide, layers[i][2], 5.18, y + 0.13, 6.85, 0.3, { fontSize: 12.5, color: i === 2 ? 'CBD5E7' : C.muted });
    }
    rect(slide, 8.85, 2.2, 3.08, 3.02, C.ink2, 0.18);
    circle(slide, 9.5, 2.72, 1.78, '1D3151', C.teal);
    circle(slide, 9.89, 3.11, 1.0, C.teal);
    await iconBadge(slide, 'eye', 10.1, 3.32, C.ink, C.lime, 0.58);
    tx(slide, 'Case Quality', 9.24, 4.05, 2.26, 0.35, { fontSize: 17, color: C.white, bold: true, align: 'center' });
    tx(slide, '新增核心', 9.65, 4.5, 1.42, 0.3, { fontSize: 11, color: C.lime, bold: true, align: 'center' });
    tx(slide, '边界建议在详细设计评审后定版，避免把业务逻辑塞进 API 或 app 启动模块。', 0.83, 6.58, 10.8, 0.3, { fontSize: 11.5, color: C.muted, italic: true });
    addNotes(slide, '这是给新员工看的边界提示。实现时优先复用现有前端 client、API 规范、领域模型和持久化规范；案例质量是独立业务概念，具体是否新增 Maven 模块，在详细设计阶段按 module-map 和 dependency-rules 决定。');
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
    titleBlock(slide, '03 · 目标产品', '目标体验：生成后 30 秒完成评价', '案例列表、案例正文和评价抽屉同屏；评价结果与当前生成版本绑定。', 8);

    // window shell
    card(slide, 0.55, 1.86, 12.23, 4.9, 'F8FAFD', C.grayLine);
    rect(slide, 0.55, 1.86, 12.23, 0.48, C.ink, 0.12);
    circle(slide, 0.77, 2.03, 0.11, C.coral);
    circle(slide, 0.96, 2.03, 0.11, C.amber);
    circle(slide, 1.15, 2.03, 0.11, C.teal);
    tx(slide, '案例质量工作台 · 支付应用 / S20260730-001 / Run #2841', 1.48, 1.98, 6.3, 0.18, { fontSize: 10.5, color: 'DDE6F3', bold: true });
    pill(slide, '已评价 2 / 3', 10.98, 1.94, 1.36, '203552', C.lime, 9.5);

    // left list
    rect(slide, 0.76, 2.53, 2.55, 3.92, C.white, 0.1, C.grayLine);
    tx(slide, '案例列表', 0.98, 2.72, 0.9, 0.26, { fontSize: 13.2, bold: true });
    pill(slide, '批量评价', 2.13, 2.68, 0.91, C.wash, C.slate, 9.2);
    const cases = [
      ['TC-001', '登录成功', 'A', C.green, 'E1F6EB'],
      ['TC-002', '失败次数与锁定', 'B', C.blue, C.blueSoft],
      ['TC-003', '解锁后再次登录', '—', C.muted, C.fog]
    ];
    cases.forEach((c, i) => {
      const y = 3.17 + i * 0.86;
      rect(slide, 0.9, y, 2.26, 0.68, i === 1 ? C.mint : C.wash, 0.09, i === 1 ? C.teal : C.wash);
      tx(slide, c[0], 1.04, y + 0.09, 0.73, 0.2, { fontSize: 9.8, bold: true, color: C.muted });
      tx(slide, c[1], 1.04, y + 0.31, 1.55, 0.22, { fontSize: 11.3, bold: i === 1 });
      pill(slide, c[2], 2.66, y + 0.17, 0.34, c[4], c[3], 9.2);
    });
    tx(slide, '筛选：未评价 · C/D · 有问题标签', 0.98, 5.9, 2.0, 0.25, { fontSize: 9.4, color: C.muted });

    // center case
    rect(slide, 3.47, 2.53, 5.2, 3.92, C.white, 0.1, C.grayLine);
    pill(slide, 'GENERATED · v3', 3.71, 2.72, 1.42, C.blueSoft, C.blue, 9.2);
    tx(slide, 'TC-002  连续失败后锁定账户', 3.71, 3.08, 4.56, 0.36, { fontSize: 16.2, bold: true });
    const fields = [
      ['前置条件', '用户已注册，账户状态正常'],
      ['测试步骤', '1. 连续输入错误密码 5 次\n2. 第 6 次输入正确密码'],
      ['预期结果', '账户被锁定；正确密码仍禁止登录，并提示解锁方式']
    ];
    fields.forEach((f, i) => {
      const y = 3.58 + i * 0.75;
      tx(slide, f[0], 3.72, y, 0.72, 0.23, { fontSize: 10.5, bold: true, color: C.tealDark });
      tx(slide, f[1], 4.53, y - 0.02, 3.73, 0.48, { fontSize: 10.8, color: C.slate, breakLine: true, valign: 'top' });
      if (i < 2) line(slide, 3.72, y + 0.55, 4.55, 0, C.fog, 1);
    });
    pill(slide, '修改差异 3 处', 3.72, 5.98, 1.28, C.amberSoft, '946A13', 9.2);
    tx(slide, '+ 补充锁定阈值  + 明确断言  + 增加解锁提示', 5.15, 5.98, 3.08, 0.25, { fontSize: 9.4, color: C.muted });

    // evaluation drawer
    rect(slide, 8.84, 2.53, 3.72, 3.92, 'F2F7FB', 0.1, C.grayLine);
    tx(slide, '评价本案例', 9.08, 2.74, 1.4, 0.3, { fontSize: 14.5, bold: true });
    pill(slide, '必填', 11.57, 2.71, 0.61, C.coralSoft, C.red, 9);
    tx(slide, '可用性', 9.09, 3.19, 0.8, 0.22, { fontSize: 10.5, bold: true, color: C.slate });
    const grades = [
      ['A 直接', C.limeSoft, C.green], ['B 小改', C.blue, C.white],
      ['C 大改', C.amberSoft, '946A13'], ['D 不可用', C.coralSoft, C.red]
    ];
    grades.forEach((g, i) => pill(slide, g[0], 9.09 + (i % 2) * 1.54, 3.49 + Math.floor(i / 2) * 0.46, 1.38, g[1], g[2], 9.5));
    tx(slide, '问题类型（多选）', 9.09, 4.47, 1.2, 0.22, { fontSize: 10.5, bold: true, color: C.slate });
    pill(slide, '预期结果', 9.09, 4.76, 0.9, C.coralSoft, C.red, 8.8);
    pill(slide, '业务规则', 10.08, 4.76, 0.9, C.purpleSoft, C.purple, 8.8);
    pill(slide, '测试数据', 11.07, 4.76, 0.9, C.amberSoft, '946A13', 8.8);
    tx(slide, '影响功能', 9.09, 5.28, 0.7, 0.22, { fontSize: 10.5, bold: true, color: C.slate });
    pill(slide, '认证 / 登录 / 锁定', 9.85, 5.22, 1.77, C.wash, C.slate, 8.9);
    tx(slide, '说明：阈值来自详细设计 5.2，生成时遗漏。', 9.09, 5.7, 2.8, 0.28, { fontSize: 9.4, color: C.muted });
    pill(slide, '保存评价', 10.58, 6.01, 1.55, C.teal, C.white, 10.2);
    addNotes(slide, '这是最终效果的核心示意。建议以抽屉或侧栏嵌入现有工作台，不让用户跳出生成现场。必须同时支持单案例和批量评价，并自动关联生成版本、Run 和后续修改差异。');
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
    titleBlock(slide, '03 · 目标产品', '看板效果：从总览一路钻到案例证据', '下图为演示数据；核心是“指标 → 问题 → 功能 → 明细”的可钻取链路。', 12);
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
      ['78%', '评价覆盖率', C.tealDark, C.mint],
      ['46%', '直接可用率 A', C.green, C.limeSoft],
      ['81%', '可用率 A+B', C.blue, C.blueSoft],
      ['6.4m', '平均修改耗时', C.purple, C.purpleSoft]
    ];
    kpis.forEach((k, i) => {
      const x = 0.77 + i * 2.91;
      rect(slide, x, 2.67, 2.64, 0.86, C.white, 0.1, C.grayLine);
      tx(slide, k[0], x + 0.18, 2.79, 1.15, 0.34, { fontSize: 22, bold: true, color: k[2] });
      tx(slide, k[1], x + 1.22, 2.8, 1.18, 0.3, { fontSize: 10.3, bold: true, color: C.slate });
    });

    // trend panel
    rect(slide, 0.77, 3.72, 4.85, 2.72, C.white, 0.1, C.grayLine);
    tx(slide, '直接可用率趋势', 0.98, 3.91, 1.65, 0.25, { fontSize: 12.3, bold: true });
    tx(slide, '按 Skill 修订', 4.32, 3.92, 0.98, 0.2, { fontSize: 9.2, color: C.muted, align: 'right' });
    // chart grid and line
    [0, 1, 2].forEach(i => line(slide, 1.12, 4.48 + i * 0.62, 4.12, 0, C.fog, 1));
    const pts = [[1.22,5.63],[2.0,5.35],[2.78,5.43],[3.56,4.91],[4.34,4.72],[5.04,4.47]];
    for (let i = 0; i < pts.length - 1; i++) line(slide, pts[i][0], pts[i][1], pts[i+1][0]-pts[i][0], pts[i+1][1]-pts[i][1], C.teal, 2.6);
    pts.forEach((p, i) => {
      circle(slide, p[0]-0.07, p[1]-0.07, 0.14, i === pts.length-1 ? C.lime : C.teal);
      tx(slide, `v${i+1}`, p[0]-0.2, 5.96, 0.4, 0.18, { fontSize: 8.2, color: C.muted, align: 'center' });
    });
    pill(slide, '46%', 4.72, 4.16, 0.54, C.limeSoft, C.green, 8.7);

    // problem bars
    rect(slide, 5.81, 3.72, 3.19, 2.72, C.white, 0.1, C.grayLine);
    tx(slide, '问题类型 Top 4', 6.02, 3.91, 1.6, 0.25, { fontSize: 12.3, bold: true });
    const probs = [['需求理解',0.82,27,C.purple],['预期断言',0.67,22,C.coral],['前置数据',0.55,18,C.amber],['范围覆盖',0.46,15,C.blue]];
    probs.forEach((p, i) => {
      const y = 4.42 + i * 0.48;
      tx(slide, p[0], 6.02, y, 0.75, 0.19, { fontSize: 9.4, color: C.slate });
      rect(slide, 6.82, y + 0.03, 1.48, 0.13, C.fog, 0.06);
      rect(slide, 6.82, y + 0.03, 1.48 * p[1], 0.13, p[3], 0.06);
      tx(slide, `${p[2]}%`, 8.36, y - 0.02, 0.38, 0.2, { fontSize: 9.2, bold: true, align: 'right' });
    });

    // heatmap
    rect(slide, 9.18, 3.72, 3.37, 2.72, C.white, 0.1, C.grayLine);
    tx(slide, '功能问题热区', 9.4, 3.91, 1.45, 0.25, { fontSize: 12.3, bold: true });
    const funcs = ['登录', '支付', '对账', '通知'];
    const cats = ['理解', '数据', '断言'];
    funcs.forEach((f, r) => tx(slide, f, 9.4, 4.43 + r * 0.42, 0.48, 0.18, { fontSize: 9, color: C.slate }));
    cats.forEach((c, col) => tx(slide, c, 10.08 + col * 0.68, 4.22, 0.48, 0.16, { fontSize: 8.4, color: C.muted, align: 'center' }));
    const heat = [
      [C.coral, C.amberSoft, C.coralSoft], [C.purpleSoft, C.coral, C.amberSoft],
      [C.blueSoft, C.amberSoft, C.coralSoft], [C.mint, C.blueSoft, C.amberSoft]
    ];
    heat.forEach((row, r) => row.forEach((col, c) => rect(slide, 10.08 + c * 0.68, 4.42 + r * 0.42, 0.48, 0.25, col, 0.05)));
    pill(slide, '点击下钻 12 条案例', 9.43, 6.06, 2.24, C.ink, C.white, 9);
    addNotes(slide, '看板数据全部标成演示数据，避免被误认为已有生产结论。真正验收重点是任意指标都能钻到案例明细和评价证据，且筛选后的汇总与明细数量一致。');
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
    titleBlock(slide, '04 · 版本库关联', '案例与开发版本库：双资产、只读关联、证据可追溯', '开发源码是被测事实，测试案例是测试资产；评价层用版本身份与代码证据把两者连接起来。', 14, '结合 Codex 任务 019fae77… 的源码快照与人员工作区分析');

    // Development side
    card(slide, 0.66, 2.02, 3.32, 3.92, C.white, C.grayLine);
    pill(slide, '开发侧 · 被测对象', 0.9, 2.27, 1.55, C.blueSoft, C.blue, 9.5);
    await iconBadge(slide, 'file', 3.12, 2.22, C.blue, C.blueSoft, 0.54);
    tx(slide, '应用源码快照', 0.92, 2.86, 1.85, 0.34, { fontSize: 17, bold: true });
    tx(slide, 'generation\ntargetCommit\nselectedPaths\nindexSha256', 0.92, 3.34, 1.45, 1.05, { fontSize: 11.2, color: C.muted, breakLine: true, valign: 'top' });
    pill(slide, '运行目录无 .git', 2.47, 3.42, 1.12, C.coralSoft, C.red, 8.8);
    tx(slide, '读取代码、接口、模块和数据结构事实；\n需要版本差异时，使用物化阶段提前冻结的 ChangeSet。', 0.92, 4.72, 2.62, 0.76, { fontSize: 10.7, color: C.slate, breakLine: true, valign: 'top' });
    pill(slide, '已存在：快照身份', 0.92, 5.48, 1.47, C.mint, C.tealDark, 8.6);
    pill(slide, '扩展：ChangeSet', 2.48, 5.48, 1.25, C.purpleSoft, C.purple, 8.6);

    // Bridge
    circle(slide, 4.37, 2.78, 2.48, C.ink);
    circle(slide, 4.82, 3.23, 1.58, '152B45', C.teal);
    await iconBadge(slide, 'link', 5.29, 3.7, C.ink, C.lime, 0.64);
    tx(slide, '案例—源码\n证据关联', 4.73, 4.54, 1.78, 0.58, { fontSize: 16.4, bold: true, color: C.white, align: 'center', breakLine: true });
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
      ['一致性判断', '支持 / 冲突 / 未覆盖'],
      ['评价结论', 'A–D / 标签 / 说明']
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
    tx(slide, '验证案例可执行性与预期结果 · 识别代码已变但案例未覆盖 · 区分案例生成问题与代码 / 需求事实不清', 1.86, 6.36, 10.25, 0.24, { fontSize: 11.2, bold: true, color: C.white });
    addNotes(slide, '这一页只讨论案例与开发版本库的分析关系，不把夜间回归纳入课题范围。开发源码快照和测试工作区保持分离；评价记录通过 sourceGeneration/targetCommit、代码路径/API/符号等证据关联案例。源码快照无 .git，若需要版本差异，应在快照物化阶段提前冻结 ChangeSet；该 ChangeSet 是扩展设计，不是当前已实现能力。');
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
    titleBlock(slide, '05 · 实施范围', 'MVP：先闭环，再做“聪明”', '第一版只做能采集、能统计、能钻取、能对比的最小完整链路。', 16);
    const cols = [
      ['必须完成', C.green, C.limeSoft, [
        '生成完成后的评价入口', '四级可用性 + 多选问题标签', '影响功能 + 补充说明', '案例 / 源码快照版本关联', '代码路径 / API 证据关联', '看板、钻取、权限与审计'
      ]],
      ['条件允许', C.blue, C.blueSoft, [
        '自动计算修改差异', '批量评价与快捷键', '源码事实一致性辅助分析', 'Skill 修订前后对比', '冻结 ChangeSet 对接', '抽样复核 / 双人一致性'
      ]],
      ['本期不做', C.red, C.coralSoft, [
        '用自动评分完全替代人工', '直接基于原始反馈自动训练', '一次覆盖所有测试对象标签', '恢复源码快照中的 .git', '混淆开发库与测试工作区', '跨系统大而全数据中台'
      ]]
    ];
    for (let c = 0; c < cols.length; c++) {
      const x = 0.72 + c * 4.04;
      card(slide, x, 1.99, 3.72, 4.55, C.white, C.grayLine);
      pill(slide, cols[c][0], x + 0.24, 2.23, 1.17, cols[c][2], cols[c][1], 10.2);
      cols[c][3].forEach((item, i) => {
        circle(slide, x + 0.27, 2.92 + i * 0.53, 0.25, cols[c][2]);
        tx(slide, c === 2 ? '×' : '✓', x + 0.27, 2.96 + i * 0.53, 0.25, 0.15, { fontSize: 8.5, bold: true, color: cols[c][1], align: 'center' });
        tx(slide, item, x + 0.67, 2.86 + i * 0.53, 2.62, 0.3, { fontSize: 11.5, color: C.slate, bold: i === 0 });
      });
    }
    addNotes(slide, '对新员工来说，范围控制尤其重要。只有评价入口、数据模型、统计与证据钻取全部打通，才算最小完整产品；自动评分、自动训练和全域标签都留到后续。');
  }

  // 17 Plan and team
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '05 · 实施范围', '8 周推进：每两周交付一次可演示增量', '建议 5–7 人小组按能力交叉协作；每个阶段都用真实案例验证。', 17);
    const phases = [
      ['W1–2', '定义', '样本盘点\n口径校准\n案例—源码数据字典', C.teal, C.mint],
      ['W3–4', '采集', '评价 API\n数据存储\n工作台入口', C.blue, C.blueSoft],
      ['W5–6', '洞察', '指标查询\n看板与钻取\n版本 / 模块筛选', C.purple, C.purpleSoft],
      ['W7–8', '验证', '联调 / E2E\n试点评价\n版本对比与答辩', C.coral, C.coralSoft]
    ];
    line(slide, 1.18, 2.91, 10.88, 0, C.grayLine, 5);
    phases.forEach((p, i) => {
      const x = 0.84 + i * 3.04;
      circle(slide, x + 0.84, 2.52, 0.78, p[3]);
      tx(slide, p[0], x + 0.84, 2.75, 0.78, 0.18, { fontSize: 9.2, bold: true, color: C.white, align: 'center' });
      card(slide, x, 3.4, 2.46, 1.58, C.white, C.grayLine);
      pill(slide, p[1], x + 0.22, 3.62, 0.66, p[4], p[3], 9.8);
      tx(slide, p[2], x + 0.22, 4.08, 1.98, 0.67, { fontSize: 11, color: C.slate, breakLine: true, valign: 'top' });
    });
    const roles = [
      ['产品 / 口径', '需求、原型、判例'], ['前端', '评价与看板'], ['后端 / 数据', 'API、模型、统计'], ['质量 / 测试', '样本、自动化、验收']
    ];
    roles.forEach((r, i) => {
      const x = 0.85 + i * 3.0;
      rect(slide, x, 5.53, 2.55, 0.7, i === 0 ? C.ink : C.wash, 0.13, i === 0 ? C.ink : C.grayLine);
      tx(slide, r[0], x + 0.18, 5.66, 0.98, 0.22, { fontSize: 10.7, bold: true, color: i === 0 ? C.lime : C.ink });
      tx(slide, r[1], x + 1.06, 5.66, 1.25, 0.22, { fontSize: 9.8, color: i === 0 ? C.white : C.muted });
    });
    pill(slide, '每周一次口径校准', 4.53, 6.52, 1.87, C.limeSoft, C.green, 9.8);
    pill(slide, '每两周一次产品演示', 6.72, 6.52, 2.05, C.blueSoft, C.blue, 9.8);
    addNotes(slide, '按新员工人数可以合并角色，但产品口径、前端、后端数据和测试验收四个责任不能缺失。建议两周一个可演示增量，不要等到最后一周才首次集成。');
  }

  // 18 Acceptance
  {
    const slide = pptx.addSlide();
    titleBlock(slide, '06 · 验收方式', '用一条真实生成任务，演示完整闭环', '验收不是看页面数量，而是看案例能否追溯到评价、开发版本与代码证据。', 18);
    const demo = [
      ['1', '生成', '从需求与设计生成一批案例', C.blue],
      ['2', '评价', '完成单条 / 批量 A–D 评价', C.teal],
      ['3', '统计', '看直接可用率与问题分布', C.purple],
      ['4', '钻取', '回到案例、源码版本与代码证据', C.coral],
      ['5', '对比', '比较 Skill 修订前后变化', C.green]
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
      '评价绑定案例版本、源码快照与操作者',
      '单次评价中位耗时不高于 30 秒',
      '看板汇总与明细抽查结果一致',
      '任意指标可钻取到原案例与反馈证据',
      '至少 30 条案例完成双人校准试评',
      '权限、审计、异常与兼容性完成验证'
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
    pill(slide, '成功 = 评价被使用，而不是页面被打开', 4.47, 6.48, 4.4, C.limeSoft, C.green, 10.5);
    addNotes(slide, '验收演示要跑一条真实链路，不要只展示静态看板。30 条案例是首轮校准建议样本，不代表生产统计门槛；后续可按应用与案例类型扩大样本。');
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

  const out = path.resolve(__dirname, 'AI案例可用性评估看板-开题汇报.pptx');
  await pptx.writeFile({ fileName: out });
  process.stdout.write(`${out}\n`);
}

buildDeck().catch((error) => {
  console.error(error);
  process.exit(1);
});
