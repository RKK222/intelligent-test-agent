from __future__ import annotations

import io
import json
import sys
import tempfile
import unittest
from pathlib import Path
from unittest import mock

SKILL_ROOT = Path(__file__).resolve().parents[1]
SCRIPTS = SKILL_ROOT / "scripts"
sys.path.insert(0, str(SCRIPTS))

import locate_request_markdown as locator
import normalize_request_payload as normalizer
import query_interface_contract as query
import sync_request_structure as sync_module
import update_markdown_request as updater


WRAPPED_STRUCT = [
    {
        "name": "argRoot",
        "paramClass": "java.util.Map",
        "children": [
            {"name": "keep", "paramClass": "java.lang.String", "children": []},
            {"name": "missing", "paramClass": "java.lang.String", "children": []},
            {
                "name": "detail",
                "paramClass": "java.util.Map",
                "children": [
                    {"name": "code", "paramClass": "java.lang.String", "children": []},
                ],
            },
            {
                "name": "items",
                "paramClass": "java.util.List<Object>",
                "children": [
                    {"name": "id", "paramClass": "java.lang.String", "children": []},
                    {
                        "name": "tags",
                        "paramClass": "java.util.List<java.lang.String>",
                        "children": [],
                    },
                ],
            },
        ],
    }
]


def markdown(interface_name: str = "DemoApi", payload: dict | None = None) -> str:
    payload = {"old": 1} if payload is None else payload
    return (
        "# 接口案例\n\n"
        f"| 接口英文名 | `{interface_name}` |\n"
        "| --- | --- |\n\n"
        "## 2. 数据准备\n\n保持不变。\n\n"
        "## 3. 请求报文\n\n"
        "```json\n"
        + json.dumps(payload, ensure_ascii=False, indent=2)
        + "\n```\n\n"
        "## 4. 断言\n\n断言保持不变。\n"
    )


class FakeResponse(io.BytesIO):
    def __init__(self, value: object, status: int = 200):
        body = json.dumps(value, ensure_ascii=False).encode("utf-8")
        super().__init__(body)
        self.status = status
        self.headers = {"Content-Length": str(len(body))}

    def getcode(self) -> int:
        return self.status

    def __enter__(self):
        return self

    def __exit__(self, exc_type, exc, tb):
        self.close()
        return False


class WorkspaceIdentityTests(unittest.TestCase):
    def test_personal_worktree_path(self):
        app, version = query.parse_workspace_identity(
            r"C:\Users\tester\personalworktree\20260818\team\F-BASE\workspace\04-测试"
        )
        self.assertEqual((app, version), ("F-BASE", "2026年8月"))

    def test_appworkspace_logical_path(self):
        app, version = query.parse_workspace_identity(
            "appworkspace:20260707/gcms/F-GCMS/workspace/04-测试/REQ"
        )
        self.assertEqual((app, version), ("F-GCMS", "2026年7月"))

    def test_same_date_repeated_is_allowed(self):
        app, version = query.parse_workspace_identity(
            "personalworktree:20260818/archive/20260818/F-BASE/workspace"
        )
        self.assertEqual((app, version), ("F-BASE", "2026年8月"))

    def test_multiple_different_dates_are_rejected(self):
        with self.assertRaises(query.ContractError):
            query.parse_workspace_identity("20260818/20260901/F-BASE/workspace")

    def test_missing_workspace_segment_is_rejected(self):
        with self.assertRaises(query.ContractError):
            query.parse_workspace_identity("20260818/F-BASE/not-workspace")

    def test_invalid_date_segment_is_rejected(self):
        with self.assertRaises(query.ContractError):
            query.parse_workspace_identity("20261301/F-BASE/workspace")

    def test_invalid_interface_name_is_rejected(self):
        for value in ("", "中文名", "bad name", "1StartsWithNumber"):
            with self.subTest(value=value), self.assertRaises(query.ContractError):
                query.validate_interface_name(value)


class QueryTests(unittest.TestCase):
    def test_mock_http_extracts_only_required_contract(self):
        response_value = {
            "msg": "ok",
            "interfaceInfo": {"reqParamStruct": WRAPPED_STRUCT, "caseList": [{"secret": "ignored"}]},
        }
        with mock.patch.object(query.urllib.request, "urlopen", return_value=FakeResponse(response_value)) as opened:
            result = query.query_contract("appworkspace:20260818/team/F-BASE/workspace", "DemoApi")
        self.assertEqual(result["appName"], "F-BASE")
        self.assertEqual(result["version"], "2026年8月")
        self.assertEqual(result["interfaceEnName"], "DemoApi")
        self.assertEqual(result["reqParamStruct"], WRAPPED_STRUCT)
        self.assertNotIn("caseList", result)
        request = opened.call_args.args[0]
        self.assertEqual(request.full_url, query.API_URL)
        self.assertEqual(request.get_method(), "POST")
        self.assertEqual(
            json.loads(request.data.decode("utf-8")),
            {"appName": "F-BASE", "version": "2026年8月", "interfaceEnName": "DemoApi"},
        )

    def test_empty_structure_is_rejected(self):
        response_value = {"interfaceInfo": {"reqParamStruct": []}}
        with mock.patch.object(query.urllib.request, "urlopen", return_value=FakeResponse(response_value)):
            with self.assertRaises(query.ContractError):
                query.query_contract("20260818/F-BASE/workspace", "DemoApi")


class NormalizeTests(unittest.TestCase):
    def test_wrapper_root_is_unwrapped(self):
        result = normalizer.normalize_payload({}, WRAPPED_STRUCT)
        self.assertEqual(result["wrapperRoot"], "argRoot")
        self.assertNotIn("argRoot", result["normalizedPayload"])
        self.assertIn("keep", result["normalizedPayload"])

    def test_extra_fields_removed_missing_and_null_filled(self):
        source = {
            "keep": "original",
            "missing": None,
            "extra": "remove",
            "detail": {"code": "D", "extraNested": 1},
            "items": [{"id": "I", "tags": ["a"], "extraItem": True}],
        }
        result = normalizer.normalize_payload(source, WRAPPED_STRUCT)
        payload = result["normalizedPayload"]
        self.assertEqual(payload["keep"], "original")
        self.assertEqual(payload["missing"], "")
        self.assertNotIn("extra", payload)
        self.assertNotIn("extraNested", payload["detail"])
        self.assertNotIn("extraItem", payload["items"][0])
        self.assertIn("$.missing", result["nullPaths"])
        self.assertIn("$.extra", result["extraPaths"])
        self.assertTrue(result["passed"])

    def test_missing_leaf_is_empty_string(self):
        result = normalizer.normalize_payload(
            {"keep": "K", "detail": {"code": "C"}, "items": [{"id": "1", "tags": []}]},
            WRAPPED_STRUCT,
        )
        self.assertEqual(result["normalizedPayload"]["missing"], "")
        self.assertIn("$.missing", result["missingPaths"])

    def test_nested_map_and_list_containers_are_repaired(self):
        result = normalizer.normalize_payload(
            {"keep": "K", "missing": "M", "detail": "wrong", "items": "wrong"},
            WRAPPED_STRUCT,
        )
        payload = result["normalizedPayload"]
        self.assertEqual(payload["detail"], {"code": ""})
        self.assertEqual(payload["items"], [{"id": "", "tags": []}])
        self.assertIn("$.detail", result["invalidContainers"])
        self.assertIn("$.items", result["invalidContainers"])

    def test_empty_structured_list_gets_one_element(self):
        source = {"keep": "K", "missing": "M", "detail": {"code": "C"}, "items": []}
        result = normalizer.normalize_payload(source, WRAPPED_STRUCT)
        self.assertEqual(result["normalizedPayload"]["items"], [{"id": "", "tags": []}])

    def test_normalized_payload_passes_independent_validation(self):
        result = normalizer.normalize_payload({"unexpected": 1}, WRAPPED_STRUCT)
        validation = normalizer.validate_payload_structure(result["normalizedPayload"], WRAPPED_STRUCT)
        self.assertTrue(validation["passed"])
        self.assertFalse(result["sourcePassed"])


class LocateAndUpdateTests(unittest.TestCase):
    def test_exact_unique_locator(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "042-测试执行"
            root.mkdir()
            expected = root / "target.md"
            expected.write_text(markdown("DemoApi"), encoding="utf-8")
            (root / "other.md").write_text(markdown("DemoApiExtra"), encoding="utf-8")
            self.assertEqual(locator.locate_request_markdown(root, "DemoApi"), expected.resolve())

    def test_horizontal_table_locator(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "042-测试执行"
            root.mkdir()
            target = root / "target.md"
            target.write_text(
                "| 接口英文名 | 中文名 |\n| --- | --- |\n| DemoApi | 示例 |\n\n" + markdown("OtherApi"),
                encoding="utf-8",
            )
            self.assertEqual(locator.locate_request_markdown(root, "DemoApi"), target.resolve())

    def test_multiple_exact_matches_are_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "042-测试执行"
            root.mkdir()
            (root / "a.md").write_text(markdown("DemoApi"), encoding="utf-8")
            (root / "b.md").write_text(markdown("DemoApi"), encoding="utf-8")
            with self.assertRaises(locator.LocateError):
                locator.locate_request_markdown(root, "DemoApi")

    def test_atomic_update_changes_only_request_block(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "042-测试执行"
            root.mkdir()
            target = root / "case.md"
            original = markdown("DemoApi", {"old": 1})
            target.write_text(original, encoding="utf-8")
            before_request, after_request = original.split("{\n  \"old\": 1\n}")
            result = updater.update_request_payload(target, {"new": {"value": "x"}})
            updated = target.read_text(encoding="utf-8")
            self.assertTrue(result["changed"])
            self.assertTrue(updated.startswith(before_request))
            self.assertTrue(updated.endswith(after_request))
            _, payload, _ = updater.read_request_payload(target)
            self.assertEqual(payload, {"new": {"value": "x"}})

    def test_target_outside_042_is_rejected(self):
        with tempfile.TemporaryDirectory() as temp:
            target = Path(temp) / "case.md"
            target.write_text(markdown(), encoding="utf-8")
            with self.assertRaises(updater.MarkdownUpdateError):
                updater.read_request_payload(target)


class EndToEndTests(unittest.TestCase):
    def test_mocked_contract_end_to_end_sync(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "042-测试执行"
            root.mkdir()
            target = root / "case.md"
            target.write_text(
                markdown(
                    "DemoApi",
                    {
                        "keep": "preserve",
                        "extra": 1,
                        "detail": {"code": None},
                        "items": [],
                    },
                ),
                encoding="utf-8",
            )
            contract = {
                "appName": "F-BASE",
                "version": "2026年8月",
                "interfaceEnName": "DemoApi",
                "reqParamStruct": WRAPPED_STRUCT,
            }
            with mock.patch.object(sync_module, "query_contract", return_value=contract) as queried:
                result = sync_module.sync_request_structure(
                    "appworkspace:20260818/team/F-BASE/workspace",
                    "DemoApi",
                    execution_root=root,
                )
            self.assertEqual(result["stageStatus"], "COMPLETED")
            self.assertTrue(result["changed"])
            self.assertEqual(result["wrapperRoot"], "argRoot")
            queried.assert_called_once()
            _, persisted, _ = updater.read_request_payload(target)
            self.assertEqual(persisted["keep"], "preserve")
            self.assertEqual(persisted["missing"], "")
            self.assertEqual(persisted["detail"], {"code": ""})
            self.assertEqual(persisted["items"], [{"id": "", "tags": []}])
            self.assertNotIn("extra", persisted)
            self.assertTrue(normalizer.validate_payload_structure(persisted, WRAPPED_STRUCT)["passed"])

    def test_query_failure_does_not_modify_file(self):
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp) / "042-测试执行"
            root.mkdir()
            target = root / "case.md"
            target.write_text(markdown("DemoApi", {"old": 1}), encoding="utf-8")
            before = target.read_bytes()
            with mock.patch.object(sync_module, "query_contract", side_effect=query.ContractError("offline")):
                with self.assertRaises(query.ContractError):
                    sync_module.sync_request_structure(
                        "20260818/F-BASE/workspace", "DemoApi", target_file=target
                    )
            self.assertEqual(target.read_bytes(), before)


if __name__ == "__main__":
    unittest.main()
