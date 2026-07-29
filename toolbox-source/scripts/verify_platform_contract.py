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
IT_PLATFORM_VERSION = "2024.10.22-7ca5933-platform.2"
IT_PLATFORM_IMAGE = f"test-agent/it-tools:{IT_PLATFORM_VERSION}"
OMNI_PLATFORM_VERSION = "0.6.0-platform.1"
OMNI_PLATFORM_IMAGE = f"test-agent/omni-tools:{OMNI_PLATFORM_VERSION}"


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

    for app, public_prefix in (
        (it, "/toolbox/apps/it-tools/"),
        (omni, "/toolbox/apps/omni-tools/"),
    ):
        nginx = (app / "nginx.conf").read_text(encoding="utf-8")
        dockerfile = (app / "Dockerfile").read_text(encoding="utf-8")
        require(
            "Content-Security-Policy" in nginx
            and "script-src 'self' 'unsafe-eval' 'wasm-unsafe-eval' blob:" in nginx
            and "connect-src 'self' blob: data:" in nginx,
            f"CSP 缺失或未包含离线运行时所需最小能力: {app.name}",
        )
        require(dockerfile.count("@sha256:") == 2, f"基础镜像 digest 未完整锁定: {app.name}")
        require(
            f"location ^~ {public_prefix}" in nginx
            and f"rewrite ^{public_prefix}(.*)$ /$1 last;" in nginx,
            f"容器 Nginx 缺少公开子路径直连映射: {app.name}",
        )
        require((app / "LICENSE").is_file() and (app / "UPSTREAM.md").is_file(), f"许可证证据缺失: {app.name}")


def verify_it_tools_chinese_release(root: Path) -> None:
    """阻止跳过中文审计或误发旧派生版本。"""
    it = root / "toolbox-source/it-tools"
    package = json.loads((it / "package.json").read_text(encoding="utf-8"))
    scripts = package.get("scripts", {})
    audit_command = scripts.get("audit:zh-ui", "")
    build_command = scripts.get("build", "")
    expected_audit = (
        "node scripts/audit-zh-ui-batch-a.mjs && vitest --environment jsdom run "
        "src/router.test.ts src/ui/shared-i18n.test.ts src/plugins/i18n.plugin.test.ts"
    )
    require(audit_command == expected_audit, "IT-Tools 中文审计命令缺失或漂移")
    require(build_command.startswith("pnpm audit:zh-ui && "), "IT-Tools 生产构建未优先执行中文审计")
    require((it / "scripts/audit-zh-ui-batch-a.mjs").is_file(), "IT-Tools 中文审计脚本缺失")

    i18n_plugin = (it / "src/plugins/i18n.plugin.ts").read_text(encoding="utf-8")
    require("locale: 'zh'" in i18n_plugin, "IT-Tools 默认语言未固定为中文")
    require("fallbackLocale: 'zh'" in i18n_plugin, "IT-Tools 回退语言未固定为中文")

    upstream = (it / "UPSTREAM.md").read_text(encoding="utf-8")
    modifications = (it / "PLATFORM_MODIFICATIONS.md").read_text(encoding="utf-8")
    readme = (it / "README.md").read_text(encoding="utf-8")
    require(IT_PLATFORM_VERSION in upstream and IT_PLATFORM_IMAGE in upstream, "IT-Tools 上游证据仍指向旧派生版本")
    require("85 条保留路由" in modifications and "中文界面审计" in modifications, "IT-Tools 平台中文化修改说明不完整")
    require(IT_PLATFORM_IMAGE in readme and "禁止使用" in readme and "latest" in readme, "IT-Tools 对应源码缺少派生构建警示")


def verify_deployment_image_versions(root: Path) -> None:
    """镜像版本不能只是默认值：发布、部署、诊断和示例必须共同锁定。"""
    files = {
        "package-release.sh": root / "deploy/internal/package-release.sh",
        "toolbox-docker.sh": root / "deploy/internal/toolbox-docker.sh",
        "diagnose-toolbox.sh": root / "deploy/internal/diagnose-toolbox.sh",
        "toolbox.env.example": root / "deploy/internal/toolbox.env.example",
        "toolbox.md": root / "docs/deployment/toolbox.md",
    }
    contents = {name: path.read_text(encoding="utf-8") for name, path in files.items()}
    for name, content in contents.items():
        require(IT_PLATFORM_IMAGE in content, f"{name} 未锁定 IT-Tools platform.2")
        require(OMNI_PLATFORM_IMAGE in content, f"{name} 未锁定 OmniTools platform.1")

    for name in ("package-release.sh", "toolbox-docker.sh", "diagnose-toolbox.sh"):
        content = contents[name]
        require(
            'require_platform_image "TEST_AGENT_TOOLBOX_IT_TOOLS_IMAGE"' in content
            and 'require_platform_image "TEST_AGENT_TOOLBOX_OMNI_TOOLS_IMAGE"' in content,
            f"{name} 未阻止旧 tag/latest 覆盖平台版本",
        )
    require(
        "verify_container_image test-agent-it-tools" in contents["diagnose-toolbox.sh"]
        and "verify_container_image test-agent-omni-tools" in contents["diagnose-toolbox.sh"],
        "工具诊断未核对容器实际镜像引用",
    )
    require(
        "--tmpfs /run:rw,noexec,nosuid,size=1m" in contents["toolbox-docker.sh"]
        and "--tmpfs /var/run:" not in contents["toolbox-docker.sh"],
        "工具箱必须为 Alpine 真实 /run 目录提供 tmpfs，以兼容企业 Docker 18.09",
    )
    require(
        f"test-agent_it-tools_{IT_PLATFORM_VERSION}-linux-amd64.tar" in contents["toolbox.env.example"]
        and f"test-agent_omni-tools_{OMNI_PLATFORM_VERSION}-linux-amd64.tar" in contents["toolbox.env.example"],
        "离线 tar 名与平台镜像版本不一致",
    )


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
    verify_it_tools_chinese_release(root)
    verify_deployment_image_versions(root)
    verify_build_outputs(root)
    print("工具盒子 193 项离线目录、派生路由、许可证与资源校验通过")


if __name__ == "__main__":
    main()
