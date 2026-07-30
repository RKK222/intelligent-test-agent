#!/usr/bin/env python3
"""基于 A6 原稿生成“智能测试技术专题汇报”版本。

保留原稿的封面、目录、测试智能体演进页和结束页，复用原母版；
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
    add_title(slide, "1.2", "Dify → 灵犀 Code：底座更替的本质", page)
    add_kicker(slide, "不是简单更换模型，而是把能力载体从固定工作流升级为“可读写工作区、可调用工具、可持续演进”的工程化 Agent。")

    columns = [
        (0.55, "Dify 工作流阶段", "流程画布", ["节点与顺序预先编排", "适合稳定、边界清晰的单点能力", "测试人员主要消费既有流程"], RED),
        (4.55, "演进触发点", "复杂测试任务", ["需求、代码、接口与页面上下文分散", "设计和执行需要反复规划与工具调用", "资产必须跟随 Git 版本长期演进"], ORANGE),
        (8.55, "灵犀 Code 阶段", "自主式 Agent", ["以个人 worktree 为真实工作现场", "Agent + Skill + Tool 按任务动态组合", "Session / Run / Diff / 事件全程可管控"], BLUE),
    ]
    for x, title, tag, body, color in columns:
        add_card(slide, x, 1.48, 3.65, 3.65, title, body, fill=WHITE, line=color, accent=color, tag=tag)
    add_arrow(slide, 4.22, 3.08, color=ORANGE)
    add_arrow(slide, 8.22, 3.08, color=BLUE)

    add_rect(slide, 0.65, 5.42, 12.05, 1.08, fill=RED_PALE, line=RED_LIGHT)
    items = [
        (0.90, "能力定义", "Prompt → Agent / Skill"),
        (3.85, "执行方式", "固定节点 → 自主规划 + 工具调用"),
        (7.25, "资产形态", "平台配置 → Git 目录与版本"),
        (10.30, "平台治理", "黑盒流程 → RunEvent / Diff / 审计"),
    ]
    for x, title, body in items:
        add_text(slide, title, x, 5.64, 1.75, 0.25, size=12, color=RED, bold=True)
        add_text(slide, body, x, 5.98, 2.45, 0.30, size=11.3, color=INK)
    return slide


def build_architecture_slide(prs: Presentation, page: int):
    slide = prs.slides.add_slide(prs.slide_layouts[7])
    add_title(slide, "2.1", "当前技术架构：平台管控 + Agent Runtime + 灵犀 Code 执行", page)
    add_kicker(slide, "浏览器只访问平台后端；平台负责身份、路由、运行态与资产治理，灵犀 Code（基于 OpenCode）负责 Agent 推理和工具执行。")

    layer_specs = [
        (1.38, "交互层", RED_LIGHT, RED, ["agent-web / frontend-opencode", "backend-api", "event-stream-client"]),
        (2.34, "平台层", PANEL, INK, ["test-agent-api", "workspace / opencode runtime", "AgentRuntimeRegistry"]),
        (3.30, "执行层", PURPLE_LIGHT, PURPLE, ["opencode-client + generated SDK", "灵犀 Code 用户进程", "个人 worktree"]),
        (4.26, "数据层", GREEN_LIGHT, GREEN, ["Redis Stream / snapshot", "PostgreSQL / MyBatis", "manager / XXL-JOB"]),
    ]
    for y, label, fill, color, items in layer_specs:
        add_pill(slide, label, 0.55, y + 0.18, 1.02, 0.36, fill=color, color=WHITE, size=10.6)
        add_rect(slide, 1.76, y, 7.32, 0.76, fill=fill, line=color)
        gap = 6.92 / len(items)
        for index, item in enumerate(items):
            x = 1.95 + index * gap
            add_text(slide, item, x, y + 0.18, gap - 0.12, 0.38, size=10.8, color=color if index == 0 else INK, bold=index == 0, align=PP_ALIGN.CENTER, valign=MSO_ANCHOR.MIDDLE)

    add_card(slide, 9.36, 1.38, 3.40, 3.64, "多服务器运行边界", ["用户绑定稳定 linuxServerId", "入口 Java 路由到目标 Java", "目标 Java 控制本机 manager", "manager 管理用户专属灵犀 Code 进程", "启动 / 停止 / 状态统一走公共服务"], fill=RED_PALE, line=RED, accent=RED, body_size=11.1)

    add_rect(slide, 0.65, 5.38, 12.00, 0.96, fill=BLUE_LIGHT, line=BLUE)
    runtime = ["提交 Run", "鉴权与工作区校验", "路由目标 Java", "Agent / Skill / Tool 执行", "RunEvent SSE + Diff 回传"]
    for index, item in enumerate(runtime):
        x = 0.88 + index * 2.35
        add_pill(slide, str(index + 1), x, 5.66, 0.30, 0.30, fill=BLUE, color=WHITE, size=9.4)
        add_text(slide, item, x + 0.38, 5.58, 1.75, 0.44, size=10.3, color=INK, valign=MSO_ANCHOR.MIDDLE)
        if index < len(runtime) - 1:
            add_arrow(slide, x + 2.12, 5.70, w=0.20, h=0.16, color=BLUE)
    return slide


def build_agent_skill_combined_slide(prs: Presentation, page: int):
    slide = prs.slides.add_slide(prs.slide_layouts[7])
    add_title(slide, "3.2", "Agent 与 Skill：职责分工与装配方式", page)
    add_kicker(slide, "Agent 负责目标、上下文与协作；Skill 负责某一步的稳定工序。两者都由 Git 管理，并在灵犀 Code 运行时加载。")

    add_card(slide, 0.55, 1.42, 5.82, 2.24, "Agent：两层模型", ["平台层：AgentRuntimeRegistry 选择 opencode 适配器", "任务层：.opencode/agents/<id>.md", "mode: primary / subagent / all", "主控 Agent 拆解任务，@ 子 Agent 分工并收敛结果"], fill=RED_PALE, line=RED, accent=RED, tag="WHO", body_size=11.3)
    add_card(slide, 6.70, 1.42, 6.08, 2.24, "Skill：标准工序", ["目录：.opencode/skills/<id>/SKILL.md", "配套 rules/ 与 templates/", "定义适用条件、输入输出、步骤、失败边界、验证命令", "通过 Agent & Skill Hub 发布、固定版本引用并热加载"], fill=BLUE_LIGHT, line=BLUE, accent=BLUE, tag="HOW", body_size=11.3)

    add_text(slide, "测试协作编队", 0.65, 3.98, 1.65, 0.32, size=15, color=INK, bold=True)
    agents = [
        (0.65, "主控", "任务拆解 / 收敛", RED),
        (2.45, "测试设计", "案例契约 / 覆盖", ORANGE),
        (4.25, "API / UI", "脚本生成 / 执行", BLUE),
        (6.05, "数据", "造数 / Mock", PURPLE),
        (7.85, "结果分析", "证据 / 归因", GREEN),
    ]
    for x, title, body, color in agents:
        add_rect(slide, x, 4.42, 1.55, 1.32, fill=WHITE, line=color)
        add_pill(slide, title, x + 0.13, 4.59, 1.29, 0.32, fill=color, color=WHITE, size=9.6)
        add_text(slide, body, x + 0.12, 5.05, 1.31, 0.38, size=9.8, color=INK, align=PP_ALIGN.CENTER)

    add_rect(slide, 9.68, 3.94, 3.10, 2.10, fill=CODE_BG, line=CODE_BG)
    add_text(slide, "Skill 例", 9.94, 4.15, 2.58, 0.28, size=13.5, color=WHITE, bold=True, align=PP_ALIGN.CENTER)
    add_paragraphs(slide, ["设计：边界场景生成", "执行：API 脚本生成", "执行：UI 元素定位", "闭环：证据采集 / 失败归因"], 9.92, 4.61, 2.62, 1.14, size=10.3, color=WHITE, gap=3)

    add_text(slide, "主控 Agent", 0.72, 6.13, 1.18, 0.26, size=10.8, color=RED, bold=True)
    add_arrow(slide, 1.91, 6.15, w=0.28, h=0.18, color=RED)
    add_text(slide, "选择子 Agent", 2.22, 6.13, 1.24, 0.26, size=10.8, color=INK, bold=True)
    add_arrow(slide, 3.48, 6.15, w=0.28, h=0.18, color=RED)
    add_text(slide, "按需加载 Skill", 3.79, 6.13, 1.36, 0.26, size=10.8, color=BLUE, bold=True)
    add_arrow(slide, 5.18, 6.15, w=0.28, h=0.18, color=BLUE)
    add_text(slide, "调用 Tool", 5.50, 6.13, 1.02, 0.26, size=10.8, color=INK, bold=True)
    add_arrow(slide, 6.54, 6.15, w=0.28, h=0.18, color=BLUE)
    add_text(slide, "输出证据与 Diff", 6.85, 6.13, 1.58, 0.26, size=10.8, color=GREEN, bold=True)
    return slide


def build_docs_combined_slide(prs: Presentation, page: int):
    slide = prs.slides.add_slide(prs.slide_layouts[7])
    add_title(slide, "4.1", "规划：docs/ 资产如何进入测试闭环", page)
    add_kicker(slide, "沿用当前 docs/** 的共享发布边界：原始过程留在 Run / spec，复核后的知识进入 docs，方法论沉淀为 Agent / Skill。")
    add_pill(slide, "规划态", 11.72, 0.30, 0.80, 0.30, fill=ORANGE, color=WHITE, size=10)

    add_rect(slide, 0.55, 1.38, 4.05, 4.90, fill=CODE_BG, line=CODE_BG)
    tree = [
        "docs/",
        "├─ business/      # 业务规则 / 流程",
        "├─ interfaces/    # 接口 / 字段 / 错误码",
        "├─ data/          # 实体 / 字典 / 样例",
        "├─ ui/            # 页面 / 路由 / 组件",
        "├─ testing/",
        "│  ├─ guides/     # 测试指引",
        "│  ├─ cases/      # 复用案例",
        "│  └─ defects/    # 缺陷模式",
        "└─ _index.yaml    # 元数据索引",
    ]
    add_paragraphs(slide, tree, 0.82, 1.69, 3.52, 3.55, size=10.9, color=WHITE, font=MONO, gap=2.4)
    add_pill(slide, "asset_id · version · owner · source_commit · sensitivity", 0.82, 5.46, 3.52, 0.34, fill=RED, color=WHITE, size=8.8)
    add_text(slide, "不新建平行 doc/；统一使用项目现有 docs/。", 0.82, 5.91, 3.52, 0.26, size=10.2, color="FFCFD1", align=PP_ALIGN.CENTER)

    flow = [
        (4.92, 1.42, "1  治理入库", "文档转 Markdown\n补元数据、版本与权限", RED),
        (8.93, 1.42, "2  上下文选择", "当前 worktree + 显式引用\n外部资产走 references", ORANGE),
        (4.92, 3.36, "3  设计与执行消费", "设计 Agent 生成案例契约\n执行 Agent 调工具并采证", BLUE),
        (8.93, 3.36, "4  复核与回流", "caseId + runId + commit 追溯\n案例入 docs，方法入 Skill", GREEN),
    ]
    for x, y, title, body, color in flow:
        add_card(slide, x, y, 3.50, 1.55, title, body, fill=WHITE, line=color, accent=color, body_size=11.2)
    add_arrow(slide, 8.52, 2.02, w=0.30, h=0.20, color=ORANGE)
    add_arrow(slide, 8.52, 3.96, w=0.30, h=0.20, color=GREEN)

    add_rect(slide, 4.92, 5.38, 7.51, 0.90, fill=PANEL, line=LINE)
    add_text(slide, "当前复用", 5.17, 5.62, 0.82, 0.26, size=10.8, color=GREEN, bold=True)
    add_text(slide, "docs Git 发布 / 文件通道 / Hub 引用 / RunEvent / Diff", 6.03, 5.60, 2.95, 0.30, size=9.9, color=INK)
    add_text(slide, "后续补齐", 9.20, 5.62, 0.82, 0.26, size=10.8, color=ORANGE, bold=True)
    add_text(slide, "资产索引检索 / 契约校验 / 证据晋级", 10.06, 5.60, 2.05, 0.30, size=9.9, color=INK)
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
    add_title(slide, "3.1", "测试设计与执行：共享一份可执行契约", page)
    add_kicker(slide, "融合点不是“把两个流程拼在一起”，而是让设计输出天然成为执行输入，并让执行证据反向修正设计。")

    add_card(slide, 0.55, 1.52, 3.18, 4.58, "测试设计 Agent", ["理解详细设计、接口与代码变更", "识别功能点、路径、边界与风险", "调用设计类 Skill 补齐覆盖", "生成结构化 Test Case Contract"], fill=RED_PALE, line=RED, accent=RED, tag="PLAN", body_size=12)
    add_card(slide, 9.60, 1.52, 3.18, 4.58, "测试执行 Agent", ["解析统一案例契约", "按 API / UI / 数据场景选择工具", "生成或复用脚本并执行", "收集日志、截图、响应与断言证据"], fill=BLUE_LIGHT, line=BLUE, accent=BLUE, tag="ACT", body_size=12)

    add_rect(slide, 4.05, 1.42, 5.20, 3.35, fill=CODE_BG, line=CODE_BG)
    add_text(slide, "Test Case Contract", 4.35, 1.68, 4.62, 0.34, size=17, color=WHITE, bold=True, font=MONO, align=PP_ALIGN.CENTER)
    contract = [
        "case_id: PAY-LOGIN-001",
        "scenario: 密码连续错误后锁定",
        "preconditions: [用户已注册]",
        "steps: [登录×N, 再次登录]",
        "test_data: {user, password}",
        "expected: [锁定码, 审计日志]",
        "target: [api, ui]",
        "evidence: [response, screenshot, log]",
    ]
    add_paragraphs(slide, contract, 4.50, 2.15, 4.20, 2.27, size=10.7, color=WHITE, font=MONO, gap=1.4)
    add_arrow(slide, 3.77, 3.00, color=RED)
    add_arrow(slide, 9.28, 3.00, color=BLUE)

    add_rect(slide, 4.05, 5.08, 5.20, 1.25, fill=GREEN_LIGHT, line=GREEN)
    add_text(slide, "Observe → Verify → Learn", 4.33, 5.27, 4.64, 0.32, size=15, color=GREEN, bold=True, align=PP_ALIGN.CENTER)
    add_text(slide, "执行失败不是终点：区分脚本问题 / 环境问题 / 产品缺陷，并把已复核结论回写为案例、规则或资产。", 4.30, 5.68, 4.70, 0.42, size=10.8, color=INK, align=PP_ALIGN.CENTER)
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

    agenda_updates = {
        "整体情况概述": "底座演进",
        "功能测试\n智能测试设计 智能测试执行": "当前架构\n平台、运行态与事件",
        "非功能测试\n智能性能测试 智能安全测试": "设计 × 执行\nAgent / Skill 技术细节",
        "后续规划": "资产融合",
    }
    for original, replacement in agenda_updates.items():
        shape = find_text_shape(agenda, original)
        if shape is not None:
            set_shape_text(shape, replacement)

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
