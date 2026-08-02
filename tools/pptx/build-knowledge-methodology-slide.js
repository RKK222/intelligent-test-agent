#!/usr/bin/env node

const path = require("path");
const pptxgen = require("pptxgenjs");

const repositoryRoot = path.resolve(__dirname, "../..");
const sourceImage = path.join(
  repositoryRoot,
  "docs/presentations/assets/开发测试协同知识沉淀方法论.png"
);
const outputFile = path.join(
  repositoryRoot,
  "docs/presentations/开发测试协同知识沉淀方法论.pptx"
);

async function buildDeck() {
  const presentation = new pptxgen();
  presentation.layout = "LAYOUT_WIDE";
  presentation.author = "RKK222";
  presentation.subject = "开发测试协同的知识沉淀方法论";
  presentation.title = "开发测试协同的知识沉淀方法论";
  presentation.company = "智能测试";
  presentation.lang = "zh-CN";

  const slide = presentation.addSlide();
  slide.background = { color: "FFFFFF" };
  // 源图已按 16:9 汇报版式制作，整页嵌入以保留设计稿的字体与对齐效果。
  slide.addImage({ path: sourceImage, x: 0, y: 0, w: 13.333333, h: 7.5 });
  slide.addNotes("开发测试协同的知识沉淀方法论：左侧为应用级整合目录，右侧预留测试实际案例。");

  await presentation.writeFile({ fileName: outputFile });
}

buildDeck().catch((error) => {
  console.error(error);
  process.exitCode = 1;
});
