import importlib.util
import json
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SKILL_ROOT = Path(__file__).resolve().parents[1]
SCRIPTS_ROOT = SKILL_ROOT / "scripts"
sys.path.insert(0, str(SCRIPTS_ROOT))


def load_module(name: str, path: Path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


renderer = load_module("render_api_script", SCRIPTS_ROOT / "render_api_script.py")
verifier = load_module("verify_rendered_script", SCRIPTS_ROOT / "verify_rendered_script.py")


def sample_values():
    return {
        "caseName": "成功案例",
        "testType": "功能测试",
        "transactionType": "正常交易",
        "testPoint": "验证成功返回",
        "designMethod": "等价类",
        "coverageDimension": "请求与落库",
        "caseDescription": "完整模板渲染回归",
        "apiId": "101011111",
        "apiName": "DemoApi",
        "apiUrl": "/demo/api",
        "payloadCodeBlockLanguage": "json",
        "requestPayload": '{\n  "ACCOUNT": "123",\n  "EMPTY": ""\n}',
        "dependencies": [
            {
                "kind": "sql",
                "databaseName": "testdb",
                "sql": "DELETE FROM demo WHERE id = '1';\nINSERT INTO demo(id) VALUES ('1');",
            },
            {
                "kind": "table",
                "tableName": "demo_table",
                "columns": ["id", "status"],
                "rows": [["1", "A", "新增"]],
            },
        ],
        "responseAssertions": [
            {
                "fieldPath": "$.code",
                "expectedValue": "0",
                "assertionMethod": "equals",
                "assertDescription": "返回成功",
            }
        ],
        "databaseAssertions": [
            {
                "tableName": "demo_table",
                "sql": "SELECT status FROM demo_table WHERE id = '1';",
                "assertions": [
                    {
                        "fieldName": "status",
                        "expectedValue": "A",
                        "assertionMethod": "equals",
                        "assertDescription": "状态正确",
                    }
                ],
            }
        ],
    }


class TemplateRendererTest(unittest.TestCase):
    def create_run(self):
        directory = tempfile.TemporaryDirectory()
        output_target = Path(directory.name) / "042-测试执行"
        output_target.mkdir()
        return directory, output_target, sample_values()

    def assert_no_intermediate_artifacts(self, output_target: Path):
        self.assertFalse((output_target / ".reference-work").exists())
        self.assertFalse((output_target.parent / ".tmp").exists())
        self.assertFalse((output_target / "clean-up.json").exists())
        self.assertFalse((output_target / "cleanup.json").exists())

    def test_renders_every_chapter_from_current_template(self):
        directory, output_target, values = self.create_run()
        self.addCleanup(directory.cleanup)
        output = output_target / "成功案例-接口自动化脚本.md"
        manifest = renderer.render_to_output(values, output, output_target)
        content = output.read_text(encoding="utf-8")
        expected_order = [
            "## 1. 案例信息",
            "## 2. 接口信息",
            "## 3. 请求报文",
            "## 4. 应用内依赖数据",
            "## 5. 断言",
            "### 5.1 返回报文断言",
            "### 5.2 数据库断言",
        ]
        self.assertEqual(
            sorted(content.index(item) for item in expected_order),
            [content.index(item) for item in expected_order],
        )
        self.assertIn("| JSON Path / XPath / 字段路径 | 预期值 | 断言方式 | 说明 |", content)
        self.assertTrue(verifier.validate(values, output, output_target, manifest).passed)
        self.assertEqual(list(output_target.iterdir()), [output])
        self.assert_no_intermediate_artifacts(output_target)

    def test_renderer_rejects_field_outside_req_param_struct(self):
        values = sample_values()
        values["requestPayload"] = '{"ACCOUNT":"123","EMPTY":"","LEGACY_ONLY":"x"}'
        values["canonicalReqParamStruct"] = [
            {
                "name": "ApiRequest",
                "children": [
                    {"name": "ACCOUNT", "children": []},
                    {"name": "EMPTY", "children": []},
                ],
            }
        ]
        with self.assertRaisesRegex(ValueError, "unexpectedAdditionalPaths"):
            renderer.render_document(values, SKILL_ROOT / "templates")

    def test_renderer_allows_only_case_declared_missing_field(self):
        values = sample_values()
        values["requestPayload"] = '{"ACCOUNT":"123"}'
        values["canonicalReqParamStruct"] = [
            {
                "name": "ApiRequest",
                "children": [
                    {"name": "ACCOUNT", "children": []},
                    {"name": "EMPTY", "children": []},
                ],
            }
        ]
        values["expectedMissingPaths"] = ["ApiRequest.EMPTY"]
        content = renderer.render_document(values, SKILL_ROOT / "templates")
        self.assertIn('{"ACCOUNT":"123"}', content)

    def test_renderer_rejects_legacy_additional_field_allowlist(self):
        values = sample_values()
        values["canonicalReqParamStruct"] = [
            {"name": "ApiRequest", "children": [{"name": "ACCOUNT", "children": []}]}
        ]
        values["expectedAdditionalPaths"] = ["LEGACY_ONLY"]
        with self.assertRaisesRegex(ValueError, "不再允许 expectedAdditionalPaths"):
            renderer.render_document(values, SKILL_ROOT / "templates")

    def test_verifier_rejects_extra_field_in_persisted_markdown(self):
        directory, output_target, values = self.create_run()
        self.addCleanup(directory.cleanup)
        values["canonicalReqParamStruct"] = [
            {
                "name": "ApiRequest",
                "children": [
                    {"name": "ACCOUNT", "children": []},
                    {"name": "EMPTY", "children": []},
                ],
            }
        ]
        output = output_target / "成功案例-接口自动化脚本.md"
        manifest = renderer.render_to_output(values, output, output_target)
        tampered = output.read_text(encoding="utf-8").replace(
            '"EMPTY": ""', '"EMPTY": "",\n  "LEGACY_ONLY": "x"'
        )
        output.write_text(tampered, encoding="utf-8")
        manifest["outputSha256"] = renderer.sha256_file(output)

        result = verifier.validate(values, output, output_target, manifest)

        self.assertFalse(result.passed)
        self.assertIn("rendered_output_mismatch", result.issues)
        self.assertIn("rendered_request_payload_invalid", result.issues)

    def test_command_line_renderer_and_verifier_use_stdin_stdout(self):
        directory, output_target, values = self.create_run()
        self.addCleanup(directory.cleanup)
        output = output_target / "成功案例-接口自动化脚本.md"
        child_environment = dict(os.environ)
        child_environment["PYTHONUTF8"] = "1"
        rendered = subprocess.run(
            [
                sys.executable,
                "-B",
                str(SCRIPTS_ROOT / "render_api_script.py"),
                "--input",
                "-",
                "--output",
                str(output),
                "--output-target",
                str(output_target),
            ],
            input=json.dumps(values, ensure_ascii=False),
            check=True,
            capture_output=True,
            text=True,
            encoding="utf-8",
            env=child_environment,
        )
        manifest = json.loads(rendered.stdout)
        completed = subprocess.run(
            [
                sys.executable,
                "-B",
                str(SCRIPTS_ROOT / "verify_rendered_script.py"),
                "--input",
                "-",
                "--output",
                str(output),
                "--output-target",
                str(output_target),
            ],
            input=json.dumps({"values": values, "manifest": manifest}, ensure_ascii=False),
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
            env=child_environment,
        )
        self.assertEqual(completed.returncode, 0, msg=completed.stderr)
        self.assertTrue(json.loads(completed.stdout)["passed"])
        self.assertEqual(list(output_target.iterdir()), [output])
        self.assert_no_intermediate_artifacts(output_target)

    def test_output_edit_removing_xpath_fails_verification(self):
        directory, output_target, values = self.create_run()
        self.addCleanup(directory.cleanup)
        output = output_target / "成功案例-接口自动化脚本.md"
        manifest = renderer.render_to_output(values, output, output_target)
        output.write_text(
            output.read_text(encoding="utf-8").replace(
                "JSON Path / XPath / 字段路径", "JSON Path / 字段路径"
            ),
            encoding="utf-8",
        )
        result = verifier.validate(values, output, output_target, manifest)
        self.assertFalse(result.passed)
        self.assertIn("output_hash_mismatch", result.issues)
        self.assertIn("rendered_output_mismatch", result.issues)

    def test_template_header_change_is_followed_without_renderer_change(self):
        with tempfile.TemporaryDirectory() as directory:
            copied_skill = Path(directory) / "generate-api-automation-markdown"
            shutil.copytree(SKILL_ROOT, copied_skill)
            copied_renderer = load_module(
                "copied_render_api_script", copied_skill / "scripts" / "render_api_script.py"
            )
            template = copied_skill / "templates" / "api-script-template.md"
            template_text = template.read_text(encoding="utf-8")
            template.write_text(
                template_text.replace("## 2. 接口信息", "## 2. 当前接口信息").replace(
                    "JSON Path", "Current Template Path"
                ),
                encoding="utf-8",
            )
            table_partial = copied_skill / "templates" / "partials" / "dependency-table-section.md"
            table_partial.write_text(
                table_partial.read_text(encoding="utf-8").replace("说明", "用途"),
                encoding="utf-8",
            )
            content = copied_renderer.render_document(sample_values(), copied_skill / "templates")
            self.assertIn("## 2. 当前接口信息", content)
            self.assertIn("Current Template Path / XPath / 字段路径", content)
            self.assertIn("| id | status | 用途 |", content)
            self.assertNotIn("| JSON Path / XPath / 字段路径 |", content)

    def test_unknown_template_placeholder_fails_rendering(self):
        with tempfile.TemporaryDirectory() as directory:
            copied_skill = Path(directory) / "generate-api-automation-markdown"
            shutil.copytree(SKILL_ROOT, copied_skill)
            template = copied_skill / "templates" / "api-script-template.md"
            template.write_text(
                template.read_text(encoding="utf-8") + "\n{{unknownFixedField}}\n",
                encoding="utf-8",
            )
            with self.assertRaises(ValueError):
                renderer.render_document(sample_values(), copied_skill / "templates")

    def test_manifest_rejects_template_values_and_output_hash_mismatch(self):
        directory, output_target, values = self.create_run()
        self.addCleanup(directory.cleanup)
        output = output_target / "成功案例-接口自动化脚本.md"
        manifest = renderer.render_to_output(values, output, output_target)
        manifest["templateHashes"] = {}
        manifest["valuesSha256"] = "0" * 64
        manifest["outputSha256"] = "f" * 64
        result = verifier.validate(values, output, output_target, manifest)
        self.assertFalse(result.passed)
        self.assertIn("template_hashes_mismatch", result.issues)
        self.assertIn("values_hash_mismatch", result.issues)
        self.assertIn("output_hash_mismatch", result.issues)

    def test_template_edit_invalidates_existing_manifest(self):
        with tempfile.TemporaryDirectory() as directory:
            copied_skill = Path(directory) / "generate-api-automation-markdown"
            shutil.copytree(SKILL_ROOT, copied_skill)
            copied_renderer = load_module(
                "stale_render_api_script", copied_skill / "scripts" / "render_api_script.py"
            )
            copied_verifier = load_module(
                "stale_verify_rendered_script", copied_skill / "scripts" / "verify_rendered_script.py"
            )
            output_target = Path(directory) / "042-测试执行"
            output_target.mkdir()
            values = sample_values()
            output = output_target / "成功案例-接口自动化脚本.md"
            manifest = copied_renderer.render_to_output(values, output, output_target)
            template = copied_skill / "templates" / "api-script-template.md"
            template.write_text(
                template.read_text(encoding="utf-8").replace("## 1. 案例信息", "## 1. 新案例信息"),
                encoding="utf-8",
            )
            result = copied_verifier.validate(values, output, output_target, manifest)
            self.assertFalse(result.passed)
            self.assertIn("template_hashes_mismatch", result.issues)
            self.assertIn("rendered_output_mismatch", result.issues)

    def test_preserves_long_sql_and_runtime_braces(self):
        values = sample_values()
        long_sql = "BEGIN\n" + "UPDATE demo SET status = 'A';\n" * 300 + "END;"
        values["dependencies"][0]["sql"] = long_sql
        values["requestPayload"] = '{\n  "runtime": "{{currentDate}}"\n}'
        content = renderer.render_document(values, SKILL_ROOT / "templates")
        self.assertIn(long_sql, content)
        self.assertIn('"runtime": "{{currentDate}}"', content)


if __name__ == "__main__":
    unittest.main()
