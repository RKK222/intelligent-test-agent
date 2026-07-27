#!/usr/bin/env python3
"""校验工具目录、派生路由和离线运行资源的发布契约。"""

from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import re
from pathlib import Path


ALLOWED_CATEGORIES = {
    "AUDIO_VIDEO", "DATA", "DATE_TIME", "DEVELOPMENT", "ENCODING", "IMAGE",
    "MATH", "NETWORK", "PDF", "SECURITY", "TEXT", "WEB",
}
IMG_LY_KEYS = {
    "/onnxruntime-web/ort-wasm-simd-threaded.wasm",
    "/onnxruntime-web/ort-wasm-simd-threaded.mjs",
    "/models/isnet_fp16",
}
FORBIDDEN_RUNTIME_ORIGINS = (
    "cdn.jsdelivr.net/npm/@ffmpeg",
    "cdn-wasm.b-cdn.net",
    "tessdata.projectnaptha.com",
    "api.iconify.design",
    "unpkg.com/figlet",
)


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for block in iter(lambda: source.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def require(condition: bool, message: str) -> None:
    if not condition:
        raise ValueError(message)


def verify_catalog(root: Path) -> None:
    catalog_path = root / "backend/test-agent-integration/src/main/resources/toolbox/catalog-v1.json"
    payload = json.loads(catalog_path.read_text(encoding="utf-8"))
    tools = payload["tools"]
    require(len(tools) == 193, f"离线目录项不是 193: {len(tools)}")
    require(len({tool["toolId"] for tool in tools}) == 193, "toolId 不唯一")
    require(len({tool["launchPath"] for tool in tools}) == 193, "launchPath 不唯一")
    for order, tool in enumerate(tools):
        require(tool["catalogOrder"] == order, f"目录顺序不连续: {tool['toolId']}")
        require(bool(re.search(r"[\u3400-\u9fff]", tool["nameZh"])), f"中文名不含汉字: {tool['toolId']}")
        require(bool(tool["nameEn"].strip()), f"缺少英文名: {tool['toolId']}")
        require(bool(re.search(r"[\u3400-\u9fff]", tool["descriptionZh"])), f"中文说明不含汉字: {tool['toolId']}")
        require(tool["category"] in ALLOWED_CATEGORIES, f"非法分类: {tool['toolId']}")
        require(bool(tool["keywords"]), f"缺少关键词: {tool['toolId']}")
        expected_prefix = "/toolbox/apps/it-tools/" if tool["source"] == "IT_TOOLS" else "/toolbox/apps/omni-tools/"
        require(tool["launchPath"].startswith(expected_prefix), f"非法启动路径: {tool['toolId']}")
        require(tool["launchPath"] != expected_prefix.rstrip("/"), f"套件首页进入目录: {tool['toolId']}")
        require("camera" not in tool["toolId"].lower(), f"摄像头工具未剔除: {tool['toolId']}")
        require(tool["toolId"] != "omni-tools.pdf.editor", "外部 SimplePDF 编辑器未剔除")


def verify_runtime_assets(root: Path) -> None:
    omni = root / "toolbox-source/omni-tools"
    expected = {
        "runtime-assets/ghostscript/gs-worker.wasm": "2d093d358b088dfa678ad3edc1ccaddfbd5d42addecb452b4d16e5bd13db0232",
        "runtime-assets/tesseract/lang/eng.traineddata.gz": "ed350f3752f81ee8f38769edc14d92d997dababe23b565c59879372cc46a2468",
        "runtime-assets/tesseract/lang/chi_sim.traineddata.gz": "59388039851e4d1293d729c183fd8c1fa9bbbb959eed996e945024671e68c1d6",
        "runtime-assets/tesseract/lang/chi_tra.traineddata.gz": "67a4357a69810bf1596e801cd95299e073030cacb0899e78d9923d2fa1185704",
    }
    for relative, digest in expected.items():
        path = omni / relative
        require(path.is_file(), f"缺少离线资源: {relative}")
        require(sha256(path) == digest, f"离线资源哈希不一致: {relative}")
        if path.suffix == ".gz":
            with gzip.open(path, "rb") as source:
                require(bool(source.read(8)), f"语言包无法解压: {relative}")

    imgly = omni / "runtime-assets/imgly"
    manifest = json.loads((imgly / "resources.json").read_text(encoding="utf-8"))
    require(set(manifest) == IMG_LY_KEYS, "IMG.LY 资源清单包含缺失或未使用模型")
    for entry in manifest.values():
        total = 0
        for chunk in entry["chunks"]:
            path = imgly / chunk["name"]
            require(path.is_file(), f"缺少 IMG.LY 分片: {path.name}")
            require(sha256(path) == chunk["hash"] == path.name, f"IMG.LY 分片哈希不一致: {path.name}")
            total += path.stat().st_size
        require(total == entry["size"], "IMG.LY 分片总大小不一致")


def verify_source_boundaries(root: Path) -> None:
    it = root / "toolbox-source/it-tools"
    omni = root / "toolbox-source/omni-tools"
    it_router = (it / "src/router.ts").read_text(encoding="utf-8")
    omni_routes = (omni / "src/config/routesConfig.tsx").read_text(encoding="utf-8")
    omni_app = (omni / "src/components/App.tsx").read_text(encoding="utf-8")
    omni_tools = (omni / "src/tools/index.ts").read_text(encoding="utf-8")
    generic_compress = (omni / "src/pages/tools/image/generic/compress/service.ts").read_text(encoding="utf-8")
    png_compress = (omni / "src/pages/tools/image/png/compress-png/index.tsx").read_text(encoding="utf-8")
    image_editor = (omni / "src/pages/tools/image/generic/editor/index.tsx").read_text(encoding="utf-8")
    file_result = (omni / "src/components/result/ToolFileResult.tsx").read_text(encoding="utf-8")
    base_file_input = (omni / "src/components/input/BaseFileInput.tsx").read_text(encoding="utf-8")
    require("camera-recorder" in it_router and "path: '/'" in it_router, "IT-Tools 路由过滤或根路径回退缺失")
    require("Home" not in it_router and "About" not in it_router, "IT-Tools 仍注册上游门户")
    require("Home" not in omni_routes and "Category" not in omni_routes, "OmniTools 仍注册上游门户/分类")
    require("Navbar" not in omni_app, "OmniTools 仍装配上游导航")
    require("path !== 'pdf/editor'" in omni_tools, "OmniTools 仍注册外部 SimplePDF 编辑器路由")
    require("useWebWorker: false" in generic_compress, "通用图片压缩仍会启动 CDN Worker")
    require("useWebWorker: false" in png_compress, "PNG 压缩仍会启动 CDN Worker")
    require("useBackendTranslations={false}" in image_editor, "图片编辑器仍会请求在线翻译服务")
    require("canWriteBinaryClipboard" in file_result, "图片结果仍缺少 HTTP 剪贴板能力保护")
    require("new ClipboardItem" not in base_file_input, "文件输入仍会直接访问二进制剪贴板")

    source_files = list((it / "src").rglob("*.ts")) + list((it / "src").rglob("*.vue"))
    source_files += list((omni / "src").rglob("*.ts")) + list((omni / "src").rglob("*.tsx"))
    runtime_source = "\n".join(path.read_text(encoding="utf-8") for path in source_files)
    for origin in FORBIDDEN_RUNTIME_ORIGINS:
        require(origin not in runtime_source, f"源码仍包含禁止的运行时资源地址: {origin}")

    for app in (it, omni):
        nginx = (app / "nginx.conf").read_text(encoding="utf-8")
        dockerfile = (app / "Dockerfile").read_text(encoding="utf-8")
        require(
            "Content-Security-Policy" in nginx
            and "script-src 'self' 'unsafe-eval' 'wasm-unsafe-eval' blob:" in nginx
            and "connect-src 'self' blob: data:" in nginx,
            f"CSP 缺失或未包含离线运行时所需最小能力: {app.name}",
        )
        require(dockerfile.count("@sha256:") == 2, f"基础镜像 digest 未完整锁定: {app.name}")
        require((app / "LICENSE").is_file() and (app / "UPSTREAM.md").is_file(), f"许可证证据缺失: {app.name}")


def verify_build_outputs(root: Path) -> None:
    it_dist = root / "toolbox-source/it-tools/dist"
    omni_dist = root / "toolbox-source/omni-tools/dist"
    if not it_dist.is_dir() or not omni_dist.is_dir():
        print("构建产物不存在，跳过 dist 校验（源码与资源校验仍已完成）")
        return
    require((it_dist / "fonts/Standard.flf").is_file(), "IT-Tools Figlet 字体未进入构建产物")
    for relative in (
        "runtime/ffmpeg/ffmpeg-core.js",
        "runtime/ffmpeg/ffmpeg-core.wasm",
        "runtime/ghostscript/gs-worker.wasm",
        "runtime/tesseract/worker.min.js",
        "runtime/imgly/resources.json",
    ):
        require((omni_dist / relative).is_file(), f"OmniTools 构建产物缺少 {relative}")
    require("/toolbox/apps/it-tools/" in (it_dist / "index.html").read_text(), "IT-Tools base 丢失")
    require("/toolbox/apps/omni-tools/" in (omni_dist / "index.html").read_text(), "OmniTools base 丢失")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()
    root = args.root.resolve()
    verify_catalog(root)
    verify_runtime_assets(root)
    verify_source_boundaries(root)
    verify_build_outputs(root)
    print("工具盒子 193 项离线目录、派生路由、许可证与资源校验通过")


if __name__ == "__main__":
    main()
