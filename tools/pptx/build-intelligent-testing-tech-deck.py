#!/usr/bin/env python3
"""基于 A6 原稿生成“智能测试技术专题汇报”逻辑优化版。

保留原稿的封面、目录、测试智能体演进页和结束页，复用原母版；
以一项测试任务为主线串起底座演进、当前架构、设计执行融合和资产闭环；
新增内容严格依据当前仓库稳定文档，规划态内容会在页面中显式标注。
"""

from __future__ import annotations

import argparse
from pathlib import Path

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_CONNECTOR, MSO_SHAPE, MSO_SHAPE_TYPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.util import Inches, Pt


RED = "D40000"
RED_DARK = "A90000"
RED_LIGHT = "FCEBEC"
RED_PALE = "FFF6F6"
INK = "252A34"
MUTED = "69707D"
LINE = "D9DDE5"
PANEL = "F5F6F8"
WHITE = "FFFFFF"
BLUE = "3167D5"
BLUE_LIGHT = "EAF1FF"
PURPLE = "6B4FCB"
PURPLE_LIGHT = "F0ECFF"
GREEN = "238B62"
GREEN_LIGHT = "E9F7F1"
ORANGE = "D66A16"
ORANGE_LIGHT = "FFF1E5"
CODE_BG = "262A33"
FONT = "Arial"
MONO = "Courier New"


def rgb(value: str) -> RGBColor:
    return RGBColor.from_string(value)


def set_run_style(run, *, size: float, color: str, bold: bool = False, font: str = FONT) -> None:
    run.font.name = font
    run.font.size = Pt(size)
    run.font.bold = bold
    run.font.color.rgb = rgb(color)


def add_text(
    slide,
    text: str,
    x: float,
    y: float,
    w: float,
    h: float,
    *,
    size: float = 15,
    color: str = INK,
    bold: bool = False,
    font: str = FONT,
    align: PP_ALIGN = PP_ALIGN.LEFT,
    valign: MSO_ANCHOR = MSO_ANCHOR.TOP,
    margin: float = 0.04,
):
    shape = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    frame = shape.text_frame
    frame.clear()
    frame.word_wrap = True
    frame.margin_left = Inches(margin)
    frame.margin_right = Inches(margin)
    frame.margin_top = Inches(margin)
    frame.margin_bottom = Inches(margin)
    frame.vertical_anchor = valign
    paragraph = frame.paragraphs[0]
    paragraph.alignment = align
    paragraph.space_after = Pt(0)
    paragraph.space_before = Pt(0)
    paragraph.line_spacing = 1.05
    run = paragraph.add_run()
    run.text = text
    set_run_style(run, size=size, color=color, bold=bold, font=font)
    return shape


def add_rich_text(
    slide,
    parts: list[tuple[str, bool, str]],
    x: float,
    y: float,
    w: float,
    h: float,
    *,
    size: float = 14,
    align: PP_ALIGN = PP_ALIGN.LEFT,
    valign: MSO_ANCHOR = MSO_ANCHOR.TOP,
):
    shape = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    frame = shape.text_frame
    frame.clear()
    frame.word_wrap = True
    frame.margin_left = frame.margin_right = Inches(0.04)
    frame.margin_top = frame.margin_bottom = Inches(0.04)
    frame.vertical_anchor = valign
    paragraph = frame.paragraphs[0]
    paragraph.alignment = align
    paragraph.space_after = Pt(0)
    paragraph.line_spacing = 1.04
    for text, bold, color in parts:
        run = paragraph.add_run()
        run.text = text
        set_run_style(run, size=size, color=color, bold=bold)
    return shape


def add_paragraphs(
    slide,
    lines: list[str],
    x: float,
    y: float,
    w: float,
    h: float,
    *,
    size: float = 13,
    color: str = INK,
    bullet: bool = False,
    gap: float = 3,
    font: str = FONT,
):
    shape = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    frame = shape.text_frame
    frame.clear()
    frame.word_wrap = True
    frame.margin_left = frame.margin_right = Inches(0.05)
    frame.margin_top = frame.margin_bottom = Inches(0.03)
    for index, line in enumerate(lines):
        paragraph = frame.paragraphs[0] if index == 0 else frame.add_paragraph()
        paragraph.text = ("• " if bullet else "") + line
        paragraph.space_after = Pt(gap)
        paragraph.line_spacing = 1.05
        for run in paragraph.runs:
            set_run_style(run, size=size, color=color, font=font)
    return shape


def add_rect(
    slide,
    x: float,
    y: float,
    w: float,
    h: float,
    *,
    fill: str = WHITE,
    line: str = LINE,
    radius: bool = True,
    transparency: int = 0,
):
    shape_type = MSO_SHAPE.ROUNDED_RECTANGLE if radius else MSO_SHAPE.RECTANGLE
    shape = slide.shapes.add_shape(shape_type, Inches(x), Inches(y), Inches(w), Inches(h))
    shape.fill.solid()
    shape.fill.fore_color.rgb = rgb(fill)
    shape.fill.transparency = transparency
    shape.line.color.rgb = rgb(line)
    shape.line.width = Pt(0.8)
    if radius and shape.adjustments:
        shape.adjustments[0] = 0.08
    return shape


def add_card(
    slide,
    x: float,
    y: float,
    w: float,
    h: float,
    title: str,
    body: list[str] | str,
    *,
    fill: str = WHITE,
    line: str = LINE,
    accent: str = RED,
    title_size: float = 16,
    body_size: float = 12.5,
    tag: str | None = None,
):
    add_rect(slide, x, y, w, h, fill=fill, line=line)
    add_text(slide, title, x + 0.18, y + 0.14, w - 0.36, 0.34, size=title_size, color=accent, bold=True)
    if tag:
        add_pill(slide, tag, x + w - 1.15, y + 0.14, 0.96, 0.28, fill=accent, color=WHITE, size=10)
    if isinstance(body, str):
        add_text(slide, body, x + 0.18, y + 0.55, w - 0.36, h - 0.68, size=body_size, color=INK)
    else:
        add_paragraphs(slide, body, x + 0.16, y + 0.54, w - 0.32, h - 0.66, size=body_size, bullet=True)


def add_pill(
    slide,
    text: str,
    x: float,
    y: float,
    w: float,
    h: float,
    *,
    fill: str = RED_LIGHT,
    color: str = RED_DARK,
    line: str | None = None,
    size: float = 10.5,
):
    shape = add_rect(slide, x, y, w, h, fill=fill, line=line or fill)
    shape.text_frame.clear()
    shape.text_frame.margin_left = shape.text_frame.margin_right = Inches(0.03)
    shape.text_frame.margin_top = shape.text_frame.margin_bottom = Inches(0)
    shape.text_frame.vertical_anchor = MSO_ANCHOR.MIDDLE
    paragraph = shape.text_frame.paragraphs[0]
    paragraph.alignment = PP_ALIGN.CENTER
    run = paragraph.add_run()
    run.text = text
    set_run_style(run, size=size, color=color, bold=True)
    return shape


def add_arrow(slide, x: float, y: float, w: float = 0.42, h: float = 0.26, color: str = RED):
    shape = slide.shapes.add_shape(MSO_SHAPE.RIGHT_ARROW, Inches(x), Inches(y), Inches(w), Inches(h))
    shape.fill.solid()
    shape.fill.fore_color.rgb = rgb(color)
    shape.line.fill.background()
    return shape


def add_title(slide, section: str, title: str, page: int) -> None:
    add_rich_text(
        slide,
        [(section + "  ", True, RED), (title, True, INK)],
        0.60,
        0.23,
        10.7,
        0.55,
        size=24,
        valign=MSO_ANCHOR.MIDDLE,
    )
    add_text(slide, str(page), 12.70, 7.10, 0.45, 0.24, size=10, color=RED, bold=True, align=PP_ALIGN.RIGHT)


def add_kicker(slide, text: str) -> None:
    add_text(slide, text, 0.65, 0.88, 12.0, 0.35, size=13.5, color=MUTED)


def add_step(slide, x: float, y: float, w: float, number: int, title: str, body: str, color: str) -> None:
    add_rect(slide, x, y, w, 1.35, fill=WHITE, line=color)
    add_pill(slide, f"{number:02d}", x + 0.16, y + 0.15, 0.48, 0.30, fill=color, color=WHITE, size=10)
    add_text(slide, title, x + 0.74, y + 0.13, w - 0.9, 0.32, size=14, color=color, bold=True)
    add_text(slide, body, x + 0.16, y + 0.56, w - 0.32, 0.63, size=11.2, color=INK)


def set_shape_text(shape, text: str) -> None:
    """只替换原稿单一文本框文字，保留其既有格式。"""
    if not shape.has_text_frame:
        return
    paragraphs = shape.text_frame.paragraphs
    if not paragraphs:
        return
    runs = paragraphs[0].runs
    if runs:
        runs[0].text = text
        for run in runs[1:]:
            run.text = ""
    else:
        paragraphs[0].text = text
    for paragraph in paragraphs[1:]:
        paragraph.text = ""


def find_text_shape(slide, exact: str):
    def walk(shapes):
        for shape in shapes:
            if getattr(shape, "has_text_frame", False) and shape.text == exact:
                return shape
            if shape.shape_type == MSO_SHAPE_TYPE.GROUP:
                found = walk(shape.shapes)
                if found is not None:
                    return found
        return None

    return walk(slide.shapes)


def shape_texts(shape) -> list[str]:
    """递归提取组合图形文字，用于识别原稿目录项。"""
    texts: list[str] = []
    if getattr(shape, "has_text_frame", False) and shape.text.strip():
        texts.append(shape.text.strip())
    if shape.shape_type == MSO_SHAPE_TYPE.GROUP:
        for child in shape.shapes:
            texts.extend(shape_texts(child))
    return texts


def remove_shape(shape) -> None:
    """从页面中移除已确认的原稿图形。"""
    shape._element.getparent().remove(shape._element)


def delete_unwanted_original_slides(prs: Presentation, keep_indexes: set[int]):
    original_slides = list(prs.slides)
    original_ids = list(prs.slides._sldIdLst)
    for index, (slide, slide_id) in enumerate(zip(original_slides, original_ids)):
        if index in keep_indexes:
            continue
        prs.slides._sldIdLst.remove(slide_id)
        prs.part.drop_rel(slide_id.rId)
    return [original_slides[index] for index in sorted(keep_indexes)]


def reorder_slides(prs: Presentation, ordered_slides: list) -> None:
    slide_id_list = prs.slides._sldIdLst
    ids_by_part = {}
    for slide_id in list(slide_id_list):
        ids_by_part[prs.part.related_part(slide_id.rId)] = slide_id
        slide_id_list.remove(slide_id)
    for slide in ordered_slides:
        slide_id_list.append(ids_by_part[slide.part])


def build_transition_slide(prs: Presentation, page: int):
    slide = prs.slides.add_slide(prs.slide_layouts[7])
    add_title(slide, "1.2", "为什么从 Dify 走向灵犀 Code", page)
    add_kicker(slide, "以“读详细设计，完成 API/UI 测试并输出证据”为例：关键差别是任务路径由谁决定。")

    columns = [
        (0.55, "Dify：平台先画好流程", "固定路径", ["节点、顺序和输入输出预先编排", "新增工具或分支需要修改流程", "适合稳定、单点、边界清晰的任务"], RED),
        (4.55, "真实测试任务会变化", "变化来源", ["文档、代码、接口、页面上下文不同", "执行中需要观察、判断、修正与重试", "过程会生成案例、脚本、日志和截图"], ORANGE),
        (8.55, "灵犀 Code：Agent 决定路径", "动态规划", ["个人 worktree 是任务的真实现场", "按任务装配 Agent、Skill 与 Tool", "Session / Run / Diff / 事件让过程可控"], BLUE),
    ]
    for x, title, tag, body, color in columns:
        add_card(slide, x, 1.48, 3.65, 3.65, title, body, fill=WHITE, line=color, accent=color, tag=tag)
    add_arrow(slide, 4.22, 3.08, color=ORANGE)
    add_arrow(slide, 8.22, 3.08, color=BLUE)

    add_rect(slide, 0.65, 5.42, 12.05, 1.08, fill=RED_PALE, line=RED_LIGHT)
    add_text(slide, "底座变化", 0.92, 5.66, 1.05, 0.28, size=12, color=RED, bold=True)
    add_text(slide, "Dify：平台定义任务路径", 2.03, 5.60, 3.25, 0.40, size=14, color=INK, bold=True, align=PP_ALIGN.CENTER)
    add_arrow(slide, 5.57, 5.69, w=0.52, h=0.24, color=RED)
    add_text(slide, "灵犀 Code：平台定义边界，Agent 规划路径", 6.30, 5.60, 5.74, 0.40, size=14, color=BLUE, bold=True, align=PP_ALIGN.CENTER)
    add_text(slide, "从“交付一个流程”变成“交付一套能在真实工程现场持续完成任务的能力”。", 1.54, 6.08, 10.28, 0.28, size=11.3, color=MUTED, align=PP_ALIGN.CENTER)
    return slide


def build_architecture_slide(prs: Presentation, page: int):
    slide = prs.slides.add_slide(prs.slide_layouts[7])
    add_title(slide, "2.1", "当前底座：四层分工，一条任务链", page)
    add_kicker(slide, "工作台承接任务，平台守住边界，灵犀 Code（基于 OpenCode）完成推理和执行，数据层保证恢复与审计。")

    layer_specs = [
        (1.38, "用户工作台", RED_LIGHT, RED, "提交任务、选择应用与工作区、查看过程和结果", "agent-web · frontend-opencode · backend-api"),
        (2.34, "平台控制面", PANEL, INK, "鉴权、路由、工作区、进程和运行态治理", "test-agent-api · workspace-management · opencode-runtime"),
        (3.30, "Agent 执行面", PURPLE_LIGHT, PURPLE, "在个人 worktree 中规划步骤、加载能力、调用工具", "灵犀 Code 用户进程 · agents · skills · tools"),
        (4.26, "运行支撑", GREEN_LIGHT, GREEN, "事件回传、文件访问、状态恢复、任务调度和审计", "RunEvent SSE · 文件 WS · Redis · PostgreSQL · manager"),
    ]
    for y, label, fill, color, responsibility, modules in layer_specs:
        add_pill(slide, label, 0.55, y + 0.18, 1.20, 0.36, fill=color, color=WHITE, size=10.2)
        add_rect(slide, 1.93, y, 7.03, 0.76, fill=fill, line=color)
        add_text(slide, responsibility, 2.14, y + 0.10, 4.30, 0.28, size=11.2, color=INK, bold=True)
        add_text(slide, modules, 2.14, y + 0.42, 6.50, 0.23, size=9.3, color=color)

    add_rect(slide, 9.24, 1.38, 3.54, 3.64, fill=BLUE_LIGHT, line=BLUE)
    add_text(slide, "一次任务怎样流动", 9.50, 1.63, 3.02, 0.32, size=16, color=BLUE, bold=True, align=PP_ALIGN.CENTER)
    task_steps = ["提交任务", "校验并路由", "Agent 规划与执行", "事件 / 文件回传", "结果与 Diff"]
    for index, item in enumerate(task_steps):
        y = 2.12 + index * 0.54
        add_pill(slide, str(index + 1), 9.62, y, 0.32, 0.30, fill=BLUE, color=WHITE, size=9.2)
        add_text(slide, item, 10.12, y - 0.01, 2.12, 0.31, size=11.2, color=INK, bold=index in {0, 4}, valign=MSO_ANCHOR.MIDDLE)
        if index < len(task_steps) - 1:
            add_text(slide, "↓", 9.70, y + 0.31, 0.18, 0.18, size=10, color=BLUE, bold=True, align=PP_ALIGN.CENTER)

    add_rect(slide, 0.65, 5.38, 12.00, 0.96, fill=RED_PALE, line=RED_LIGHT)
    add_text(slide, "三条硬边界", 0.90, 5.66, 1.20, 0.28, size=11.8, color=RED, bold=True)
    boundaries = ["浏览器不直连灵犀 Code", "业务层不穿透 generated SDK", "文件访问只走 route / ticket / RPC"]
    for index, item in enumerate(boundaries):
        x = 2.30 + index * 3.35
        add_pill(slide, str(index + 1), x, 5.65, 0.30, 0.30, fill=RED, color=WHITE, size=9.2)
        add_text(slide, item, x + 0.40, 5.59, 2.70, 0.40, size=10.6, color=INK, bold=True, valign=MSO_ANCHOR.MIDDLE)
    return slide


def build_agent_skill_combined_slide(prs: Presentation, page: int):
    slide = prs.slides.add_slide(prs.slide_layouts[7])
    add_title(slide, "3.2", "Agent、Skill、Tool、Docs 各司其职", page)
    add_kicker(slide, "四者不是四套并列资产：Agent 做决策，Skill 给方法，Tool 执行动作，Docs 提供事实。")

    roles = [
        (0.55, "Agent", "做决策", ["理解目标与上下文", "决定下一步与协作关系"], ".opencode/agents/*.md", RED),
        (3.67, "Skill", "给方法", ["定义适用条件和步骤", "约束输入、输出与验证"], ".opencode/skills/*/SKILL.md", BLUE),
        (6.79, "Tool", "执行动作", ["读写文件、调用 API", "浏览器、终端、MCP"], "built-in / MCP / custom", PURPLE),
        (9.91, "Docs", "提供事实", ["业务、接口、数据规则", "历史案例与缺陷模式"], "docs/** + references", GREEN),
    ]
    for x, title, verb, body, path, color in roles:
        add_rect(slide, x, 1.44, 2.82, 2.45, fill=WHITE, line=color)
        add_pill(slide, title, x + 0.18, 1.63, 0.82, 0.34, fill=color, color=WHITE, size=10.5)
        add_text(slide, verb, x + 1.14, 1.62, 1.42, 0.34, size=14.5, color=color, bold=True, align=PP_ALIGN.RIGHT)
        add_paragraphs(slide, body, x + 0.18, 2.18, 2.46, 0.82, size=10.8, color=INK, bullet=True, gap=3)
        add_text(slide, path, x + 0.18, 3.34, 2.46, 0.26, size=8.8, color=MUTED, font=MONO, align=PP_ALIGN.CENTER)

    add_text(slide, "放到同一项任务里", 0.65, 4.18, 1.85, 0.32, size=15, color=INK, bold=True)
    add_rect(slide, 0.65, 4.62, 12.00, 0.82, fill=RED_PALE, line=RED_LIGHT)
    design_flow = [
        (0.90, 2.06, "测试设计 Agent", RED),
        (3.35, 3.10, "需求解析 / 测试点 / 边界 Skill", ORANGE),
        (6.92, 2.58, "Tool 读取 docs 与代码", PURPLE),
        (9.98, 2.22, "输出案例契约", GREEN),
    ]
    for index, (x, width, text, color) in enumerate(design_flow):
        add_pill(slide, text, x, 4.85, width, 0.35, fill=color, color=WHITE, size=9.7)
        if index < len(design_flow) - 1:
            next_x = design_flow[index + 1][0]
            add_arrow(slide, x + width + 0.08, 4.93, w=max(0.18, next_x - (x + width) - 0.16), h=0.17, color=color)

    add_rect(slide, 0.65, 5.68, 12.00, 0.82, fill=BLUE_LIGHT, line=BLUE)
    execution_flow = [
        (0.90, 2.06, "测试执行 Agent", BLUE),
        (3.35, 3.10, "API / UI / 数据执行 Skill", PURPLE),
        (6.92, 2.58, "Tool 执行并采证", ORANGE),
        (9.98, 2.22, "输出报告与 Diff", GREEN),
    ]
    for index, (x, width, text, color) in enumerate(execution_flow):
        add_pill(slide, text, x, 5.91, width, 0.35, fill=color, color=WHITE, size=9.7)
        if index < len(execution_flow) - 1:
            next_x = execution_flow[index + 1][0]
            add_arrow(slide, x + width + 0.08, 5.99, w=max(0.18, next_x - (x + width) - 0.16), h=0.17, color=color)
    return slide


def build_docs_combined_slide(prs: Presentation, page: int):
    slide = prs.slides.add_slide(prs.slide_layouts[7])
    add_title(slide, "4.1", "规划：让 docs 成为可复用测试资产", page)
    add_kicker(slide, "先分清“本轮原始过程”“团队共享知识”“可执行方法”，再建立从执行结果到资产的晋级闭环。")
    add_pill(slide, "规划态", 11.72, 0.30, 0.80, 0.30, fill=ORANGE, color=WHITE, size=10)

    tiers = [
        (0.55, "Run / spec", "本轮原始过程", ["日志、截图、临时结论", "个人 / 会话范围，尚未复核"], ORANGE),
        (4.55, "docs/", "团队共享知识", ["业务、接口、数据、案例、缺陷", "复核后进入 Git，带版本与来源"], GREEN),
        (8.55, ".opencode/", "可执行方法", ["agents / skills / rules / templates", "把稳定做法变成下一次可执行步骤"], PURPLE),
    ]
    for x, path, title, body, color in tiers:
        add_rect(slide, x, 1.42, 3.65, 2.14, fill=WHITE, line=color)
        add_pill(slide, path, x + 0.18, 1.62, 1.20, 0.34, fill=color, color=WHITE, size=10.2)
        add_text(slide, title, x + 1.52, 1.60, 1.87, 0.36, size=14.2, color=color, bold=True, align=PP_ALIGN.RIGHT)
        add_paragraphs(slide, body, x + 0.18, 2.25, 3.29, 0.82, size=10.8, color=INK, bullet=True, gap=3)

    add_text(slide, "从一次执行到下一次复用", 0.65, 3.88, 3.45, 0.38, size=15, color=INK, bold=True)
    loop_steps = [
        (0.65, "执行产生证据", "Run / spec", ORANGE),
        (3.08, "人工 / 规则复核", "确认事实与结论", RED),
        (5.51, "晋级共享资产", "案例 / 缺陷入 docs", GREEN),
        (7.94, "固化稳定方法", "必要时更新 Skill", PURPLE),
        (10.37, "下一任务复用", "Agent 读取并执行", BLUE),
    ]
    for index, (x, title, body, color) in enumerate(loop_steps):
        add_rect(slide, x, 4.32, 2.03, 1.18, fill=WHITE, line=color)
        add_text(slide, title, x + 0.12, 4.49, 1.79, 0.28, size=11.2, color=color, bold=True, align=PP_ALIGN.CENTER)
        add_text(slide, body, x + 0.12, 4.93, 1.79, 0.30, size=9.7, color=INK, align=PP_ALIGN.CENTER)
        if index < len(loop_steps) - 1:
            add_arrow(slide, x + 2.08, 4.79, w=0.28, h=0.19, color=color)

    add_rect(slide, 0.65, 5.82, 12.00, 0.78, fill=PANEL, line=LINE)
    add_text(slide, "当前已有", 0.90, 6.05, 0.78, 0.26, size=10.8, color=GREEN, bold=True)
    add_text(slide, "docs Git 发布 / 工作区文件通道 / Hub 引用 / RunEvent / Diff", 1.70, 6.02, 4.48, 0.30, size=9.8, color=INK)
    add_text(slide, "后续补齐", 6.42, 6.05, 0.78, 0.26, size=10.8, color=ORANGE, bold=True)
    add_text(slide, "资产索引与检索 / 案例契约校验 / 证据晋级流程", 7.24, 6.02, 4.62, 0.30, size=9.8, color=INK)
    add_text(slide, "正式目录统一使用项目现有 docs/，不再新建平行 doc/。", 9.36, 6.72, 3.12, 0.22, size=8.6, color=MUTED, align=PP_ALIGN.RIGHT)
    return slide


def build_runtime_slide(prs: Presentation, page: int):
    slide = prs.slides.add_slide(prs.slide_layouts[7])
    add_title(slide, "2.2", "一次 Run 如何流过系统", page)
    add_kicker(slide, "同一条链路同时解决“任务可恢复、事件可回放、文件可落盘、过程可审计”四个问题。")

    steps = [
        ("提交任务", "创建 / 复用 Session，签发 run context", RED),
        ("平台校验", "校验应用成员、workspace、进程绑定", ORANGE),
        ("目标路由", "按 linuxServerId 路由到目标 Java", BLUE),
        ("启动 Run", "AgentRuntimeRegistry 选择 opencode 适配器", PURPLE),
        ("自主执行", "Agent 读取 worktree，加载 Skill，调用 Tool", RED),
        ("事件回传", "RunEvent 写入 Redis；SSE 推送 snapshot + tail", GREEN),
        ("结果收敛", "Diff / 日志 / 截图 / 报告回到工作台", BLUE),
    ]
    x_positions = [0.55, 2.35, 4.15, 5.95, 7.75, 9.55, 11.35]
    for idx, ((title, body, color), x) in enumerate(zip(steps, x_positions), 1):
        add_rect(slide, x, 1.58, 1.45, 2.02, fill=WHITE, line=color)
        add_pill(slide, str(idx), x + 0.13, 1.73, 0.34, 0.34, fill=color, color=WHITE, size=10)
        add_text(slide, title, x + 0.12, 2.18, 1.20, 0.35, size=13.5, color=color, bold=True, align=PP_ALIGN.CENTER)
        add_text(slide, body, x + 0.11, 2.66, 1.23, 0.72, size=10.4, color=INK, align=PP_ALIGN.CENTER)
        if idx < len(steps):
            add_arrow(slide, x + 1.50, 2.45, w=0.28, h=0.22, color=color)

    add_rect(slide, 0.65, 4.04, 12.0, 2.25, fill=PANEL, line=LINE)
    add_text(slide, "两条并行数据通道", 0.88, 4.25, 2.2, 0.34, size=15.5, color=INK, bold=True)
    channels = [
        (3.05, "控制 / 状态通道", "HTTP + RunEvent SSE", "Run 状态、消息 part、question / permission、重连游标", RED),
        (6.22, "文件 / 资产通道", "WebSocket route / ticket / RPC", "工作区与 Agent 配置的目录、读写、上传、组合视图", BLUE),
        (9.39, "持久 / 恢复通道", "Redis + PostgreSQL", "runtime stream / snapshot 与关系型审计投影分离", GREEN),
    ]
    for x, title, tech, body, color in channels:
        add_card(slide, x, 4.28, 2.90, 1.62, title, body, fill=WHITE, line=color, accent=color, body_size=10.8)
        add_pill(slide, tech, x + 0.15, 5.48, 2.60, 0.28, fill=color, color=WHITE, size=9.4)
    return slide


def build_workspace_slide(prs: Presentation, page: int):
    slide = prs.slides.add_slide(prs.slide_layouts[7])
    add_title(slide, "2.3", "工作区与配置：同一份 Git 资产驱动运行", page)
    add_kicker(slide, "灵犀 Code、内置编辑器、文件树和终端都落在当前用户个人 worktree；发布时再按目录白名单投影到应用 feature。")

    add_rect(slide, 0.55, 1.37, 4.28, 4.95, fill=CODE_BG, line=CODE_BG)
    code = [
        "workspace/",
        "├─ docs/                  # 团队共享资产",
        "├─ spec/                  # 本轮规格与过程产物",
        "├─ src/ ...               # 被测代码",
        "└─ .opencode/",
        "   ├─ opencode.jsonc      # 模型 / MCP / references",
        "   ├─ agents/",
        "   │  ├─ test-design.md",
        "   │  └─ test-execution.md",
        "   └─ skills/",
        "      └─ api-test/",
        "         ├─ SKILL.md",
        "         ├─ rules/",
        "         └─ templates/",
    ]
    add_paragraphs(slide, code, 0.82, 1.62, 3.73, 4.45, size=11.3, color=WHITE, font=MONO, gap=2)

    add_card(slide, 5.10, 1.37, 3.55, 1.42, "个人工作现场", ["运行、编辑、终端共享同一目录", "Agent 写入结果可直接形成 Git Diff"], fill=RED_PALE, line=RED, accent=RED, body_size=11.3)
    add_card(slide, 8.92, 1.37, 3.85, 1.42, "发布与同步", ["个人 HEAD → feature 路径投影", "跨用户同步保留本地改动与冲突"], fill=BLUE_LIGHT, line=BLUE, accent=BLUE, body_size=11.3)

    rows = [
        ("docs/**", "成员可写；提交并推送后进入应用 feature", GREEN),
        ("spec/**", "个人规格过程产物；仅本地提交，不发布", ORANGE),
        (".opencode/agents/**", "应用管理员 / 超管维护；热加载", RED),
        (".opencode/skills/**", "应用管理员 / 超管维护；Hub 可发布与引用", PURPLE),
        ("references", "外部资产库只读组合视图；按 alias 固定引用", BLUE),
    ]
    for index, (path, desc, color) in enumerate(rows):
        y = 3.12 + index * 0.64
        add_pill(slide, path, 5.10, y, 2.18, 0.38, fill=color, color=WHITE, size=10.4)
        add_text(slide, desc, 7.48, y + 0.01, 5.1, 0.38, size=11.5, color=INK, valign=MSO_ANCHOR.MIDDLE)
    return slide


def build_fusion_slide(prs: Presentation, page: int):
    slide = prs.slides.add_slide(prs.slide_layouts[7])
    add_title(slide, "3.1", "一项测试任务：从详细设计到执行报告", page)
    add_kicker(slide, "设计和执行不是两套流程：前一阶段输出直接成为后一阶段输入，执行证据再反向修正设计。")

    stages = [
        (0.40, 1.42, 2.00, "任务输入", ["详细设计", "代码变更", "docs 资产", "存量案例"], PANEL, INK, "INPUT"),
        (2.70, 1.42, 2.20, "测试设计 Agent", ["理解范围", "识别路径和风险", "调用设计类 Skill", "生成结构化案例"], RED_PALE, RED, "DESIGN"),
        (5.20, 1.42, 2.62, "统一案例契约", ["case_id / scenario", "steps / test_data", "expected / target", "evidence 要求"], CODE_BG, WHITE, "CONTRACT"),
        (8.12, 1.42, 2.20, "测试执行 Agent", ["按 target 选 Skill", "生成 / 复用脚本", "调用 API / UI Tool", "断言并采集证据"], BLUE_LIGHT, BLUE, "EXECUTE"),
        (10.62, 1.42, 2.30, "任务输出", ["执行状态", "响应 / 截图", "日志 / Diff", "可追溯报告"], GREEN_LIGHT, GREEN, "OUTPUT"),
    ]
    for index, (x, y, w, title, body, fill, color, tag) in enumerate(stages):
        add_rect(slide, x, y, w, 3.98, fill=fill, line=color if fill != CODE_BG else CODE_BG)
        add_pill(slide, tag, x + 0.16, y + 0.18, min(0.98, w - 0.32), 0.30, fill=color if fill != CODE_BG else RED, color=WHITE, size=8.4)
        add_text(slide, title, x + 0.15, y + 0.70, w - 0.30, 0.46, size=14.2, color=color, bold=True, align=PP_ALIGN.CENTER)
        add_paragraphs(slide, body, x + 0.16, y + 1.42, w - 0.32, 1.82, size=10.4, color=WHITE if fill == CODE_BG else INK, bullet=True, gap=4)
        if index < len(stages) - 1:
            next_x = stages[index + 1][0]
            add_arrow(slide, x + w + 0.08, 3.10, w=max(0.18, next_x - (x + w) - 0.16), h=0.23, color=color if fill != CODE_BG else RED)

    add_rect(slide, 0.65, 5.72, 12.00, 0.86, fill=GREEN_LIGHT, line=GREEN)
    add_text(slide, "结果回流", 0.90, 5.99, 0.92, 0.26, size=11.6, color=GREEN, bold=True)
    add_text(slide, "失败归因：脚本问题 / 环境问题 / 产品缺陷", 1.92, 5.94, 3.18, 0.36, size=10.5, color=INK, bold=True, align=PP_ALIGN.CENTER)
    add_arrow(slide, 5.18, 6.02, w=0.36, h=0.18, color=GREEN)
    add_text(slide, "人工复核", 5.66, 5.94, 1.18, 0.36, size=10.5, color=INK, bold=True, align=PP_ALIGN.CENTER)
    add_arrow(slide, 6.95, 6.02, w=0.36, h=0.18, color=GREEN)
    add_text(slide, "更新案例 / docs / Skill，下一次任务直接复用", 7.46, 5.94, 4.22, 0.36, size=10.5, color=GREEN, bold=True, align=PP_ALIGN.CENTER)
    return slide


def build_agent_slide(prs: Presentation, page: int):
    slide = prs.slides.add_slide(prs.slide_layouts[7])
    add_title(slide, "3.2", "Agent 两层模型：平台适配层与任务协作层", page)
    add_kicker(slide, "当前项目里“Agent”有两层含义：Java 平台选择运行时；灵犀 Code 内部再加载业务 Agent 配置并完成多 Agent 协作。")

    add_card(slide, 0.55, 1.45, 5.95, 2.30, "① 平台 Agent Runtime", ["AgentRuntime：稳定能力契约", "AgentRuntimeRegistry：按 agentId 选择实现", "OpencodeAgentRuntime：适配灵犀 Code 会话、Run、事件与 Diff", "ObservedAgentRuntime：统一日志、指标与错误"], fill=PANEL, line=INK, accent=INK, tag="JAVA", body_size=11.7)
    add_card(slide, 6.82, 1.45, 5.95, 2.30, "② 灵犀 Code 业务 Agent", ["配置文件：.opencode/agents/<id>.md", "mode: primary / subagent / all", "主 Agent 持有任务目标与上下文", "子 Agent 按 @ 调用或由主 Agent 分解协作"], fill=RED_PALE, line=RED, accent=RED, tag="CONFIG", body_size=11.7)

    add_text(slide, "建议的测试协作编队", 0.65, 4.07, 2.55, 0.34, size=16, color=INK, bold=True)
    roster = [
        (0.65, "主控 Agent", "拆解任务、选 Skill、分配子任务、收敛结果", RED),
        (3.10, "测试设计 Agent", "产出案例契约与覆盖说明", ORANGE),
        (5.55, "API / UI Agent", "针对执行对象生成脚本并调用工具", BLUE),
        (8.00, "数据 Agent", "准备数据、Mock、前置与清理", PURPLE),
        (10.45, "分析 Agent", "证据聚合、失败归因、报告与反馈", GREEN),
    ]
    for x, title, body, color in roster:
        add_rect(slide, x, 4.52, 2.20, 1.55, fill=WHITE, line=color)
        add_pill(slide, title, x + 0.15, 4.69, 1.90, 0.34, fill=color, color=WHITE, size=10.3)
        add_text(slide, body, x + 0.15, 5.18, 1.90, 0.63, size=10.5, color=INK, align=PP_ALIGN.CENTER)
    return slide


def build_skill_slide(prs: Presentation, page: int):
    slide = prs.slides.add_slide(prs.slide_layouts[7])
    add_title(slide, "3.3", "Skill：可组合、可验证、可版本化的标准工序", page)
    add_kicker(slide, "Agent 决定“谁来做、先做什么”；Skill 定义“这一步怎样稳定地做对”。")

    add_rect(slide, 0.55, 1.42, 4.18, 4.75, fill=CODE_BG, line=CODE_BG)
    skill_tree = [
        ".opencode/skills/api-test/",
        "├─ SKILL.md",
        "│  ├─ 适用条件 / 禁止边界",
        "│  ├─ 输入契约 / 操作步骤",
        "│  └─ 输出与验证命令",
        "├─ rules/",
        "│  ├─ 接口断言规则.md",
        "│  └─ 数据脱敏规则.md",
        "└─ templates/",
        "   ├─ test-case.yaml",
        "   └─ report.md",
    ]
    add_paragraphs(slide, skill_tree, 0.82, 1.72, 3.65, 3.85, size=11.6, color=WHITE, font=MONO, gap=2.4)
    add_pill(slide, "Git 版本化 · Hub 发布/引用 · 空闲时热加载", 0.82, 5.65, 3.65, 0.34, fill=RED, color=WHITE, size=10)

    add_card(slide, 5.03, 1.42, 3.65, 2.08, "设计类 Skills", ["detailed-design-parser", "test-point-miner", "boundary-case-generator", "case-contract-validator"], fill=RED_PALE, line=RED, accent=RED, body_size=11.4)
    add_card(slide, 9.02, 1.42, 3.75, 2.08, "执行类 Skills", ["api-script-generator", "ui-selector-resolver", "test-runner", "evidence-collector / failure-triage"], fill=BLUE_LIGHT, line=BLUE, accent=BLUE, body_size=11.4)

    gates = [
        (5.03, "单一职责", "一个 Skill 只解决一类可重复问题", RED),
        (7.02, "明确契约", "输入、输出、失败条件可检查", ORANGE),
        (9.01, "工具落地", "关键步骤必须有可运行命令或 Tool", BLUE),
        (11.00, "证据闭环", "运行结果能被断言和复核", GREEN),
    ]
    for x, title, body, color in gates:
        add_rect(slide, x, 4.05, 1.77, 1.78, fill=WHITE, line=color)
        add_text(slide, title, x + 0.12, 4.24, 1.53, 0.30, size=12.5, color=color, bold=True, align=PP_ALIGN.CENTER)
        add_text(slide, body, x + 0.14, 4.73, 1.49, 0.72, size=10.3, color=INK, align=PP_ALIGN.CENTER)
    return slide


def build_example_slide(prs: Presentation, page: int):
    slide = prs.slides.add_slide(prs.slide_layouts[7])
    add_title(slide, "3.4", "示例：从详细设计到 API / UI 联合执行", page)
    add_kicker(slide, "同一个 case_id 串起设计、脚本、Run 与证据，避免“案例是一套、自动化又是一套”。")

    inputs = ["详细设计", "接口说明", "页面说明", "代码变更", "历史缺陷"]
    for index, item in enumerate(inputs):
        add_pill(slide, item, 0.55, 1.43 + index * 0.62, 1.38, 0.38, fill=PANEL, color=INK, line=LINE, size=10.5)
    add_arrow(slide, 2.08, 2.55, w=0.48, h=0.30, color=RED)

    add_card(slide, 2.72, 1.42, 2.38, 3.32, "设计 Agent", ["解析业务路径", "生成正向 / 异常 / 边界", "绑定目标对象与证据要求", "输出结构化案例"], fill=RED_PALE, line=RED, accent=RED, body_size=11.2)
    add_arrow(slide, 5.23, 2.55, w=0.48, h=0.30, color=ORANGE)

    add_rect(slide, 5.88, 1.42, 2.02, 3.32, fill=CODE_BG, line=CODE_BG)
    add_text(slide, "case_id", 6.16, 1.68, 1.45, 0.28, size=13, color=WHITE, bold=True, font=MONO, align=PP_ALIGN.CENTER)
    add_text(slide, "PAY-LOGIN-001", 6.05, 2.06, 1.68, 0.32, size=11, color="FFCFD1", bold=True, font=MONO, align=PP_ALIGN.CENTER)
    add_paragraphs(slide, ["target: api + ui", "expected: 锁定码", "evidence: 响应 / 截图 / 日志"], 6.08, 2.55, 1.62, 1.35, size=10.2, color=WHITE, font=MONO, gap=3)
    add_pill(slide, "统一契约", 6.22, 4.12, 1.35, 0.32, fill=RED, color=WHITE, size=10)

    add_arrow(slide, 8.03, 2.55, w=0.48, h=0.30, color=BLUE)
    add_card(slide, 8.68, 1.42, 1.88, 3.32, "API Agent", ["构造请求", "准备数据 / Mock", "执行与断言", "采集响应 / 链路"], fill=BLUE_LIGHT, line=BLUE, accent=BLUE, body_size=10.8)
    add_card(slide, 10.86, 1.42, 1.92, 3.32, "UI Agent", ["解析步骤", "元素定位", "等待 / 重试", "截图与交互证据"], fill=PURPLE_LIGHT, line=PURPLE, accent=PURPLE, body_size=10.8)

    add_rect(slide, 2.72, 5.12, 10.06, 1.10, fill=GREEN_LIGHT, line=GREEN)
    add_text(slide, "Run 结果收敛", 2.98, 5.35, 1.45, 0.30, size=14, color=GREEN, bold=True)
    add_text(slide, "case_id + runId + asset commit + evidence path", 4.58, 5.29, 4.10, 0.36, size=12, color=INK, bold=True, font=MONO, align=PP_ALIGN.CENTER)
    add_text(slide, "→ 结果分析 Agent 归因 → 人工复核 → 缺陷 / 回归资产", 8.75, 5.29, 3.68, 0.42, size=11.1, color=INK, align=PP_ALIGN.CENTER)
    return slide


def build_docs_structure_slide(prs: Presentation, page: int):
    slide = prs.slides.add_slide(prs.slide_layouts[7])
    add_title(slide, "4.1", "规划：资产统一进入 docs/", page)
    add_kicker(slide, "沿用当前项目已存在的 docs/** 发布边界，不新建平行 doc/；原始过程证据与已复核知识分层保存。")
    add_pill(slide, "规划态", 11.72, 0.30, 0.80, 0.30, fill=ORANGE, color=WHITE, size=10)

    add_rect(slide, 0.55, 1.38, 4.20, 4.95, fill=CODE_BG, line=CODE_BG)
    tree = [
        "docs/",
        "├─ business/       # 业务规则 / 流程",
        "├─ interfaces/     # 接口 / 字段 / 错误码",
        "├─ data/           # 实体 / 字典 / 样例",
        "├─ ui/             # 页面 / 路由 / 组件",
        "├─ testing/",
        "│  ├─ guides/      # 测试指引",
        "│  ├─ cases/       # 复用案例",
        "│  ├─ defects/     # 缺陷模式",
        "│  └─ evidence/    # 精选证据索引",
        "└─ _index.yaml     # 资产清单",
    ]
    add_paragraphs(slide, tree, 0.82, 1.70, 3.65, 3.95, size=11.3, color=WHITE, font=MONO, gap=2.4)
    add_pill(slide, "原始日志 / 截图仍由 Run 或 spec/ 承载", 0.82, 5.72, 3.65, 0.34, fill=ORANGE, color=WHITE, size=9.8)

    add_card(slide, 5.05, 1.38, 3.62, 2.18, "每份资产的最小元数据", ["asset_id / type / app / version", "owner / updated_at / source_commit", "related_api / case_id / tags", "sensitivity / access_scope / freshness"], fill=PANEL, line=INK, accent=INK, body_size=11.2)
    add_card(slide, 8.95, 1.38, 3.82, 2.18, "治理门槛", ["Git 评审后才能进入共享 docs/", "过期、冲突、低置信度资产显式标记", "敏感数据只保存规则与脱敏样例", "跨库资产用 references 只读引用"], fill=RED_PALE, line=RED, accent=RED, body_size=11.2)

    add_text(slide, "资产分层原则", 5.08, 3.92, 2.05, 0.32, size=15.5, color=INK, bold=True)
    tiers = [
        (5.08, "Run / spec", "原始过程、未复核结论", ORANGE),
        (7.66, "docs/testing", "已复核、可复用测试资产", GREEN),
        (10.24, ".opencode/", "Agent / Skill 的行为资产", PURPLE),
    ]
    for x, title, body, color in tiers:
        add_rect(slide, x, 4.42, 2.30, 1.34, fill=WHITE, line=color)
        add_pill(slide, title, x + 0.17, 4.61, 1.96, 0.34, fill=color, color=WHITE, size=10.3)
        add_text(slide, body, x + 0.17, 5.08, 1.96, 0.42, size=10.5, color=INK, align=PP_ALIGN.CENTER)
    return slide


def build_docs_loop_slide(prs: Presentation, page: int):
    slide = prs.slides.add_slide(prs.slide_layouts[7])
    add_title(slide, "4.2", "规划：docs 如何进入测试闭环", page)
    add_kicker(slide, "目标是“文档可被定位、结论可被追溯、经验可被复用”，而不是把文件堆进目录。")
    add_pill(slide, "规划态", 11.72, 0.30, 0.80, 0.30, fill=ORANGE, color=WHITE, size=10)

    steps = [
        (1, "治理入库", "文档转 Markdown\n补元数据与版本", RED),
        (2, "上下文选择", "当前 worktree + # 显式引用\n外部库走 references", ORANGE),
        (3, "设计消费", "Agent 读取 docs / spec / code\nSkill 生成案例契约", PURPLE),
        (4, "执行消费", "API / UI Agent 复用契约\n工具执行并采集证据", BLUE),
        (5, "结果复核", "caseId + runId + commit\n人工确认结论", GREEN),
        (6, "资产回流", "复用案例 / 缺陷模式入 docs\n方法更新为 Skill", RED),
    ]
    xs = [0.55, 2.63, 4.71, 6.79, 8.87, 10.95]
    for item, x in zip(steps, xs):
        add_step(slide, x, 1.56, 1.74, *item)
    for x in xs[:-1]:
        add_arrow(slide, x + 1.77, 2.12, w=0.26, h=0.20, color=RED)

    add_rect(slide, 0.65, 3.45, 12.00, 2.62, fill=PANEL, line=LINE)
    add_text(slide, "三条必须打通的追溯链", 0.92, 3.72, 2.45, 0.34, size=16, color=INK, bold=True)
    traces = [
        (3.38, "需求 → 案例", "asset_id / requirement_id / case_id", RED),
        (6.34, "案例 → 执行", "case_id / script_path / runId", BLUE),
        (9.30, "结果 → 经验", "runId / evidence / defect / asset_commit", GREEN),
    ]
    for x, title, keys, color in traces:
        add_rect(slide, x, 3.72, 2.70, 1.25, fill=WHITE, line=color)
        add_text(slide, title, x + 0.15, 3.91, 2.40, 0.28, size=13, color=color, bold=True, align=PP_ALIGN.CENTER)
        add_text(slide, keys, x + 0.14, 4.40, 2.42, 0.35, size=9.8, color=INK, font=MONO, align=PP_ALIGN.CENTER)

    add_text(slide, "当前可直接复用", 0.92, 5.24, 1.55, 0.30, size=11.5, color=GREEN, bold=True)
    add_text(slide, "docs/** Git 发布、工作区文件通道、Agent/Skill 配置、Hub 引用、RunEvent 与 Diff", 2.42, 5.22, 5.34, 0.36, size=10.8, color=INK)
    add_text(slide, "后续需补齐", 8.00, 5.24, 1.15, 0.30, size=11.5, color=ORANGE, bold=True)
    add_text(slide, "资产 manifest / 检索、案例契约校验、证据晋级工作流", 9.12, 5.22, 3.20, 0.36, size=10.8, color=INK)
    return slide


def update_retained_slides(cover, agenda, evolution) -> None:
    title = find_text_shape(cover, "智能测试专题汇报")
    if title is not None:
        set_shape_text(title, "智能测试技术专题汇报")

    # 原目录是栏目罗列，改为四个连续问题，让听众先看到整条因果主线。
    old_agenda_labels = {
        "整体情况概述",
        "功能测试\n智能测试设计 智能测试执行",
        "非功能测试\n智能性能测试 智能安全测试",
        "后续规划",
    }
    for shape in list(agenda.shapes):
        if old_agenda_labels.intersection(shape_texts(shape)):
            remove_shape(shape)

    add_pill(agenda, "一条主线", 5.38, 1.13, 1.05, 0.34, fill=RED, color=WHITE, size=10.4)
    agenda_items = [
        (1, "为什么换底座", "Dify 固定流程难以承接长程、变化多的测试任务"),
        (2, "新底座解决什么", "灵犀 Code 让 Agent 在真实工作区持续规划并执行"),
        (3, "设计与执行怎样串起来", "同一份结构化案例贯穿设计、脚本、执行与证据"),
        (4, "能力怎样越用越强", "Agent 组织角色，Skill 固化方法，docs 沉淀知识"),
    ]
    for index, title, body in agenda_items:
        y = 1.60 + (index - 1) * 1.22
        add_pill(agenda, str(index), 5.42, y, 0.42, 0.42, fill=RED, color=WHITE, size=12)
        add_text(agenda, title, 6.08, y - 0.03, 2.86, 0.34, size=16.5, color=INK, bold=True)
        add_text(agenda, body, 6.08, y + 0.38, 5.70, 0.34, size=11.5, color=MUTED)

    evolution_updates = {
        "智能体阶段": "灵犀 Code 阶段",
        "规格驱动 + 自主式 Agent": "规格驱动 + 自主式 Agent",
        "当前测试智能体已从 “工具建设方供给能力、测试人员使用” 演进为：以规格驱动为方法论、自主式 Agent 为核心底座，让测试人员更多参与智能体能力的共建。":
            "当前测试智能体已从固定工作流演进为：以规格驱动为方法、灵犀 Code 为执行底座、Agent 与 Skill 为能力载体，测试人员可以直接参与能力共建。",
    }
    for original, replacement in evolution_updates.items():
        shape = find_text_shape(evolution, original)
        if shape is not None:
            set_shape_text(shape, replacement)


def build(source: Path, output: Path) -> None:
    prs = Presentation(source)
    cover, agenda, evolution, thanks = delete_unwanted_original_slides(prs, {0, 1, 2, 26})
    update_retained_slides(cover, agenda, evolution)

    new_slides = [
        build_transition_slide(prs, 4),
        build_architecture_slide(prs, 5),
        build_fusion_slide(prs, 6),
        build_agent_skill_combined_slide(prs, 7),
        build_docs_combined_slide(prs, 8),
    ]
    reorder_slides(prs, [cover, agenda, evolution, *new_slides, thanks])
    output.parent.mkdir(parents=True, exist_ok=True)
    prs.save(output)


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    build(args.source.expanduser().resolve(), args.output.expanduser().resolve())


if __name__ == "__main__":
    main()
