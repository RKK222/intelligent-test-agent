#!/usr/bin/env node

const path = require("path");
const PptxGenJS = require("pptxgenjs");

const repositoryRoot = path.resolve(__dirname, "../..");
const outputFile = path.join(
  repositoryRoot,
  "docs/presentations/开发测试协同知识沉淀方法论.pptx"
);

const color = {
  red: "C60024",
  blue: "1F4E79",
  gold: "C69C3C",
  black: "1E1E1E",
  gray: "C9CDD3"
};

/** 绘制可编辑的目录文件夹图标，避免把整页设计稿扁平化为图片。 */
function addFolderIcon(slide, pptx, x, y) {
  slide.addShape(pptx.ShapeType.rect, {
    x: x + 0.04,
    y,
    w: 0.16,
    h: 0.08,
    fill: { color: "FFFFFF", transparency: 100 },
    line: { color: color.red, width: 1.1 }
  });
  slide.addShape(pptx.ShapeType.roundRect, {
    x,
    y: y + 0.06,
    w: 0.34,
    h: 0.2,
    rectRadius: 0.03,
    fill: { color: "FFFFFF", transparency: 100 },
    line: { color: color.red, width: 1.1 }
  });
}

/** 以原生线条表达目录父子关系，确保后续可在 PowerPoint 内单独调整。 */
function addTreeLinks(slide, pptx, x, parentY, childYs) {
  const lineEnd = childYs[childYs.length - 1];
  slide.addShape(pptx.ShapeType.line, {
    x,
    y: parentY,
    w: 0.001,
    h: lineEnd - parentY,
    line: { color: "8F969D", width: 0.8 }
  });
  childYs.forEach((childY) => {
    slide.addShape(pptx.ShapeType.line, {
      x,
      y: childY,
      w: 0.18,
      h: 0.001,
      line: { color: "8F969D", width: 0.8 }
    });
  });
}

function addText(slide, text, options) {
  slide.addText(text, {
    fontFace: "Arial",
    color: color.black,
    margin: 0,
    breakLine: false,
    ...options
  });
}

function addDirectoryParent(slide, pptx, options) {
  const { y, name, isShared = false, isTestCoBuilt = false, textColor = color.black } = options;
  addFolderIcon(slide, pptx, 0.67, y + 0.02);
  let nameX = 1.19;
  if (isShared) {
    addText(slide, "*", {
      x: nameX,
      y,
      w: 0.16,
      h: 0.22,
      fontSize: 15,
      bold: true,
      color: color.red
    });
    nameX += 0.23;
  }
  addText(slide, name, {
    x: nameX,
    y,
    w: 2.3,
    h: 0.24,
    fontSize: 14.5,
    bold: true,
    color: textColor
  });
  if (isTestCoBuilt) {
    addText(slide, "★", {
      x: nameX + 1.28,
      y: y - 0.01,
      w: 0.22,
      h: 0.25,
      fontSize: 16,
      color: color.gold
    });
  }
}

function addDirectoryChild(slide, text, y, textColor = color.black, fontSize = 11.5) {
  addText(slide, text, {
    x: 1.72,
    y,
    w: 4.06,
    h: 0.18,
    fontSize,
    color: textColor
  });
}

async function buildDeck() {
  const presentation = new PptxGenJS();
  presentation.layout = "LAYOUT_WIDE";
  presentation.author = "RKK222";
  presentation.subject = "开发测试协同的知识沉淀方法论";
  presentation.title = "开发测试协同的知识沉淀方法论";
  presentation.company = "智能测试";
  presentation.lang = "zh-CN";

  const slide = presentation.addSlide();
  slide.background = { color: "FFFFFF" };

  // 标题区：所有文字与装饰均由原生对象组成，便于汇报前按需改字或替换颜色。
  slide.addShape(presentation.ShapeType.rect, {
    x: 0.2,
    y: 0.19,
    w: 0.16,
    h: 0.92,
    fill: { color: color.red },
    line: { color: color.red, transparency: 100 }
  });
  addText(slide, "开发测试协同的知识沉淀方法论", {
    x: 0.55,
    y: 0.22,
    w: 7.2,
    h: 0.42,
    fontSize: 30,
    bold: true,
    color: color.red
  });
  addText(slide, "以应用级共享知识目录为底座，开发沉淀系统事实，测试深度参与功能与数据知识结构化，持续转化为可复用、可追溯的测试资产。", {
    x: 0.56,
    y: 0.83,
    w: 12.15,
    h: 0.25,
    fontSize: 11.6,
    color: color.black
  });

  // 左右两栏保留相同的边界与标题形状；右侧仅留空，方便直接插入真实案例。
  slide.addShape(presentation.ShapeType.roundRect, {
    x: 0.18,
    y: 1.28,
    w: 5.92,
    h: 5.29,
    rectRadius: 0.07,
    fill: { color: "FFFFFF", transparency: 100 },
    line: { color: color.gray, width: 0.8 }
  });
  slide.addShape(presentation.ShapeType.roundRect, {
    x: 6.95,
    y: 1.28,
    w: 6.2,
    h: 5.29,
    rectRadius: 0.07,
    fill: { color: "FFFFFF", transparency: 100 },
    line: { color: color.gray, width: 0.8 }
  });
  slide.addShape(presentation.ShapeType.roundRect, {
    x: 0.51,
    y: 1.4,
    w: 5.28,
    h: 0.42,
    rectRadius: 0.06,
    fill: { color: color.red },
    line: { color: color.red, transparency: 100 }
  });
  slide.addShape(presentation.ShapeType.roundRect, {
    x: 7.28,
    y: 1.4,
    w: 5.59,
    h: 0.42,
    rectRadius: 0.06,
    fill: { color: color.red },
    line: { color: color.red, transparency: 100 }
  });
  addText(slide, "应用级整合目录  docs/", {
    x: 1.5,
    y: 1.49,
    w: 3.25,
    h: 0.2,
    fontSize: 18,
    bold: true,
    align: "center",
    color: "FFFFFF"
  });
  addText(slide, "测试实际案例", {
    x: 8.8,
    y: 1.49,
    w: 2.58,
    h: 0.2,
    fontSize: 18,
    bold: true,
    align: "center",
    color: "FFFFFF"
  });

  // 目录区：蓝色为开发主整理资产，黑色为共享目录、测试资产和其余内容。
  addDirectoryParent(slide, presentation, { y: 2.02, name: "应用架构/", isShared: true });
  addTreeLinks(slide, presentation, 1.37, 2.28, [2.39, 2.62]);
  addDirectoryChild(slide, "应用架构.md", 2.33, color.blue);
  addDirectoryChild(slide, "应用场景说明书_XXX.md", 2.56);

  addDirectoryParent(slide, presentation, { y: 2.92, name: "技术架构/", textColor: color.blue });
  addTreeLinks(slide, presentation, 1.37, 3.18, [3.3]);
  addDirectoryChild(slide, "工程概览_A/B.md", 3.24, color.blue);

  addDirectoryParent(slide, presentation, { y: 3.54, name: "功能模块/", isShared: true, isTestCoBuilt: true });
  addTreeLinks(slide, presentation, 1.37, 3.8, [3.91, 4.15, 4.39]);
  addDirectoryChild(slide, "功能模块_XXX.md", 3.85, color.blue);
  addDirectoryChild(slide, "测试设计文档_X1.md", 4.09);
  addDirectoryChild(slide, "测试案例_X1.md", 4.33);

  addDirectoryParent(slide, presentation, { y: 4.64, name: "功能文档/", textColor: color.blue });
  addTreeLinks(slide, presentation, 1.37, 4.9, [5.02]);
  addDirectoryChild(slide, "功能文档_X1.md", 4.96, color.blue);

  addDirectoryParent(slide, presentation, { y: 5.29, name: "数据架构/", isShared: true, isTestCoBuilt: true });
  addTreeLinks(slide, presentation, 1.37, 5.55, [5.67, 5.91]);
  addDirectoryChild(slide, "F-ABC_Gauss_1.yaml / F-ABC_MySQL_1.yaml", 5.61, color.blue, 10.8);
  addDirectoryChild(slide, "数据实体_X1.md", 5.85);

  addDirectoryParent(slide, presentation, { y: 6.08, name: "业务知识/", isShared: true, textColor: color.blue });
  addTreeLinks(slide, presentation, 1.37, 6.34, [6.45]);
  addDirectoryChild(slide, "领域术语 · 业务规则", 6.39, color.blue);

  // 中央“知识沉淀”只作为两侧关系说明，避免侵入右侧的真实案例留白空间。
  slide.addShape(presentation.ShapeType.line, {
    x: 6.55,
    y: 1.96,
    w: 0.001,
    h: 1.0,
    line: { color: "8F969D", width: 0.9 }
  });
  slide.addShape(presentation.ShapeType.line, {
    x: 6.55,
    y: 4.52,
    w: 0.001,
    h: 1.0,
    line: { color: "8F969D", width: 0.9 }
  });
  addText(slide, "知\n识\n沉\n淀", {
    x: 6.35,
    y: 3.07,
    w: 0.4,
    h: 1.3,
    fontSize: 16,
    bold: true,
    align: "center",
    valign: "mid",
    color: color.red
  });

  // 页脚图例同样拆为独立文本对象，可自行修改标记定义。
  slide.addShape(presentation.ShapeType.line, {
    x: 0.18,
    y: 6.79,
    w: 12.98,
    h: 0.001,
    line: { color: color.red, width: 1.0 }
  });
  addText(slide, "*", { x: 0.94, y: 7.0, w: 0.14, h: 0.2, fontSize: 16, bold: true, color: color.red });
  addText(slide, "开发、测试共同使用", { x: 1.18, y: 7.02, w: 1.75, h: 0.18, fontSize: 11.4 });
  addText(slide, "★", { x: 3.82, y: 6.99, w: 0.2, h: 0.2, fontSize: 16, color: color.gold });
  addText(slide, "测试深度参与整理", { x: 4.13, y: 7.02, w: 1.65, h: 0.18, fontSize: 11.4 });
  addText(slide, "|", { x: 6.18, y: 7.01, w: 0.08, h: 0.18, fontSize: 14, color: color.black });
  addText(slide, "统一沉淀于 docs/**，作为应用级可发布稳定资产", {
    x: 6.77,
    y: 7.02,
    w: 4.5,
    h: 0.18,
    fontSize: 11.4
  });

  slide.addNotes("开发测试协同的知识沉淀方法论：左侧为应用级整合目录，右侧预留测试实际案例。");
  await presentation.writeFile({ fileName: outputFile });
}

buildDeck().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
