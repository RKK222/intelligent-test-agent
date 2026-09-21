import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SKILL_ROOT = Path(__file__).resolve().parents[1]


def load_module(name: str, relative_path: str):
    spec = importlib.util.spec_from_file_location(name, SKILL_ROOT / relative_path)
    module = importlib.util.module_from_spec(spec)
    assert spec and spec.loader
    sys.modules[name] = module
    spec.loader.exec_module(module)
    return module


ranker = load_module("rank_reference_scripts", "scripts/rank_reference_scripts.py")
validator = load_module("validate_generated_payload", "scripts/validate_generated_payload.py")
data_preparation_validator = load_module(
    "validate_data_preparations", "scripts/validate_data_preparations.py"
)
contribution_validator = load_module(
    "validate_reference_contributions", "scripts/validate_reference_contributions.py"
)
temp_cleanup_validator = load_module(
    "validate_temp_cleanup", "scripts/validate_temp_cleanup.py"
)


class ReferenceRankerTest(unittest.TestCase):
    def test_filters_nit_groups_tc_and_keeps_unrelated_order(self):
        payload = {
            "code": 0,
            "data": [
                {"scriptType": "OTHER", "scriptId": "ignored"},
                {"scriptType": "NIT", "scriptId": "NIT,com.demo.FirstTC##FirstTC##001"},
                {"scriptType": "nit", "scriptId": "Nit,com.demo.SecondTC##SecondTC##002"},
                {"scriptType": "NIT", "scriptId": "com.demo.Api##invoke_1.0##成功案例"},
            ],
        }
        result = ranker.classify(payload, ["完全无关的设计"], ["999"], ["/work/20260101/app"])
        self.assertEqual([item["tcName"] for item in result["tcGroups"]], ["FirstTC", "SecondTC"])
        self.assertEqual(result["integratedTop10"], ["com.demo.Api##invoke_1.0##成功案例"])
        self.assertEqual(result["requiredReferenceKinds"], ["TC", "INTEGRATED"])
        self.assertEqual(result["derivedVersion"], "2026年1月")

    def test_case_relevance_sorts_integrated_scripts(self):
        payload = {
            "code": 0,
            "data": [
                {"scriptType": "NIT", "scriptId": "com.demo.Api##invoke##普通成功"},
                {"scriptType": "NIT", "scriptId": "com.demo.Api##invoke##账号长度超限失败"},
            ],
        }
        result = ranker.classify(payload, ["账号字段长度超限"], ["Api"], [])
        self.assertEqual(result["integratedTop10"][0], "com.demo.Api##invoke##账号长度超限失败")

    def test_tc_file_uses_last_tc_segment_as_file_name(self):
        candidate = ranker.tc_file_candidates("com.demo.FirstTC.archive.SecondTC.trailing")
        self.assertEqual(candidate["java"], "com/demo/FirstTC/archive/SecondTC.java")
        self.assertEqual(candidate["xlsGlob"], "com/demo/FirstTC/archive/SecondTC_*.xls")
        self.assertEqual(candidate["recursiveJavaGlob"], "**/SecondTC.java")
        self.assertEqual(candidate["recursiveXlsGlob"], "**/SecondTC_*.xls")
        self.assertEqual(candidate["baseName"], "SecondTC")

    def test_version_uses_first_available_context_by_priority(self):
        version = ranker.derive_version(["metadata: 20260315", "/fallback/20260101/workspace"])
        self.assertEqual(version, "2026年3月")


class PayloadValidatorTest(unittest.TestCase):
    def test_omits_arbitrary_first_level_schema_root_from_payload(self):
        struct = [
            {
                "name": "ApiRequest",
                "children": [
                    {"name": "ACCOUNT", "children": []},
                    {"name": "EMPTY", "children": []},
                ],
            }
        ]
        direct = validator.validate(
            {
                "payload": {"ACCOUNT": "1234", "EMPTY": ""},
                "reqParamStruct": struct,
            },
            ["ApiRequest.ACCOUNT=4:chars"],
        )
        self.assertTrue(direct.passed)

        rooted = validator.validate(
            {
                "payload": {"ApiRequest": {"ACCOUNT": "1234", "EMPTY": ""}},
                "reqParamStruct": struct,
            },
            [],
        )
        self.assertFalse(rooted.passed)
        self.assertIn("ApiRequest", rooted.extra_paths)
        self.assertIn("ACCOUNT", rooted.missing_paths)

    def test_validates_nested_xml_and_exact_length(self):
        document = {
            "payload": {"ABSP": "<a><b>1234</b></a>", "EMPTY": ""},
            "reqParamStruct": [
                {
                    "name": "ROOT",
                    "children": [
                        {
                            "name": "ABSP",
                            "children": [
                                {"name": "a", "children": [{"name": "b", "children": []}]}
                            ],
                        },
                        {"name": "EMPTY", "children": []},
                    ],
                }
            ],
        }
        result = validator.validate(document, ["ABSP.a.b=4:chars", "EMPTY=0:chars"])
        self.assertTrue(result.passed)

    def test_rejects_missing_extra_and_null_fields(self):
        document = {
            "payload": {"A": None, "EXTRA": "x"},
            "reqParamStruct": [{"name": "ROOT", "children": [{"name": "A", "children": []}, {"name": "B", "children": []}]}],
        }
        result = validator.validate(document, [])
        self.assertFalse(result.passed)
        self.assertIn("A", result.null_paths)
        self.assertIn("B", result.missing_paths)
        self.assertIn("B", result.unexpected_missing_paths)
        self.assertIn("EXTRA", result.extra_paths)

    def test_allows_only_case_declared_missing_field(self):
        document = {
            "payload": {"A": "kept", "C": ""},
            "reqParamStruct": [
                {
                    "name": "ROOT",
                    "children": [
                        {"name": "A", "children": []},
                        {"name": "B", "children": []},
                        {"name": "C", "children": []},
                    ],
                }
            ],
            "expectedMissingPaths": ["ROOT.B"],
        }
        result = validator.validate(document, [])
        self.assertTrue(result.passed)
        self.assertEqual(result.allowed_missing_paths, ["B"])
        self.assertEqual(result.unexpected_missing_paths, [])

    def test_rejects_unplanned_missing_field_in_missing_field_case(self):
        document = {
            "payload": {"A": "kept"},
            "reqParamStruct": [
                {
                    "name": "ROOT",
                    "children": [
                        {"name": "A", "children": []},
                        {"name": "B", "children": []},
                        {"name": "C", "children": []},
                    ],
                }
            ],
            "expectedMissingPaths": ["B"],
        }
        result = validator.validate(document, [])
        self.assertFalse(result.passed)
        self.assertEqual(result.allowed_missing_paths, ["B"])
        self.assertEqual(result.unexpected_missing_paths, ["C"])

    def test_rejects_expected_missing_field_when_payload_still_contains_it(self):
        document = {
            "payload": {"A": "kept", "B": "must-be-omitted"},
            "reqParamStruct": [
                {
                    "name": "ROOT",
                    "children": [
                        {"name": "A", "children": []},
                        {"name": "B", "children": []},
                    ],
                }
            ],
            "expectedMissingPaths": ["B"],
        }
        result = validator.validate(document, [])
        self.assertFalse(result.passed)
        self.assertEqual(result.expected_missing_but_present_paths, ["B"])

    def test_rejects_unknown_expected_missing_field(self):
        document = {
            "payload": {"A": "kept"},
            "reqParamStruct": [
                {"name": "ROOT", "children": [{"name": "A", "children": []}]}
            ],
            "expectedMissingPaths": ["NOT_IN_SCHEMA"],
        }
        result = validator.validate(document, [])
        self.assertFalse(result.passed)
        self.assertEqual(result.unknown_expected_missing_paths, ["NOT_IN_SCHEMA"])

    def test_requires_missing_field_in_every_array_item(self):
        struct = [
            {
                "name": "ROOT",
                "children": [
                    {
                        "name": "ITEMS",
                        "children": [
                            {"name": "VALUE", "children": []},
                            {"name": "OPTIONAL", "children": []},
                        ],
                    }
                ],
            }
        ]
        result = validator.validate(
            {
                "payload": {
                    "ITEMS": [
                        {"VALUE": "one"},
                        {"VALUE": "two", "OPTIONAL": "still-present"},
                    ]
                },
                "reqParamStruct": struct,
                "expectedMissingPaths": ["ITEMS.OPTIONAL"],
            },
            [],
        )
        self.assertFalse(result.passed)
        self.assertEqual(result.allowed_missing_paths, ["ITEMS[0].OPTIONAL"])
        self.assertEqual(result.expected_missing_but_present_paths, ["ITEMS.OPTIONAL"])

    def test_command_line_accepts_only_declared_missing_field(self):
        document = {
            "payload": {"A": "kept", "EMPTY": ""},
            "reqParamStruct": [
                {
                    "name": "ROOT",
                    "children": [
                        {"name": "A", "children": []},
                        {"name": "B", "children": []},
                        {"name": "EMPTY", "children": []},
                    ],
                }
            ],
        }
        command = [
            sys.executable,
            "-B",
            str(SKILL_ROOT / "scripts" / "validate_generated_payload.py"),
            "--input",
            "-",
            "--expected-missing-field",
            "ROOT.B",
        ]
        completed = subprocess.run(
            command,
            input=json.dumps(document, ensure_ascii=False),
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
        self.assertEqual(completed.returncode, 0, completed.stderr)
        output = json.loads(completed.stdout)
        self.assertTrue(output["passed"])
        self.assertEqual(output["allowedMissingPaths"], ["B"])
        self.assertEqual(output["unexpectedMissingPaths"], [])

    def test_allows_only_case_declared_additional_field(self):
        document = {
            "payload": {"A": "kept", "EXTRA": ""},
            "reqParamStruct": [
                {"name": "ROOT", "children": [{"name": "A", "children": []}]}
            ],
            "expectedAdditionalPaths": ["ROOT.EXTRA"],
        }
        result = validator.validate(document, [])
        self.assertTrue(result.passed)
        self.assertEqual(result.extra_paths, ["EXTRA"])
        self.assertEqual(result.allowed_additional_paths, ["EXTRA"])
        self.assertEqual(result.unexpected_additional_paths, [])

    def test_rejects_null_for_declared_additional_field(self):
        document = {
            "payload": {"A": "kept", "EXTRA": None},
            "reqParamStruct": [
                {"name": "ROOT", "children": [{"name": "A", "children": []}]}
            ],
            "expectedAdditionalPaths": ["EXTRA"],
        }
        result = validator.validate(document, [])
        self.assertFalse(result.passed)
        self.assertEqual(result.allowed_additional_paths, ["EXTRA"])
        self.assertEqual(result.null_paths, ["EXTRA"])

    def test_rejects_declared_additional_field_when_payload_omits_it(self):
        document = {
            "payload": {"A": "kept"},
            "reqParamStruct": [
                {"name": "ROOT", "children": [{"name": "A", "children": []}]}
            ],
            "expectedAdditionalPaths": ["EXTRA"],
        }
        result = validator.validate(document, [])
        self.assertFalse(result.passed)
        self.assertEqual(result.expected_additional_but_missing_paths, ["EXTRA"])

    def test_rejects_undeclared_extra_alongside_declared_additional_field(self):
        document = {
            "payload": {"A": "kept", "EXTRA": "allowed", "SURPRISE": "not-allowed"},
            "reqParamStruct": [
                {"name": "ROOT", "children": [{"name": "A", "children": []}]}
            ],
            "expectedAdditionalPaths": ["EXTRA"],
        }
        result = validator.validate(document, [])
        self.assertFalse(result.passed)
        self.assertEqual(result.allowed_additional_paths, ["EXTRA"])
        self.assertEqual(result.unexpected_additional_paths, ["SURPRISE"])

    def test_rejects_schema_field_declared_as_additional(self):
        document = {
            "payload": {"A": "kept"},
            "reqParamStruct": [
                {"name": "ROOT", "children": [{"name": "A", "children": []}]}
            ],
            "expectedAdditionalPaths": ["ROOT.A"],
        }
        result = validator.validate(document, [])
        self.assertFalse(result.passed)
        self.assertEqual(result.invalid_expected_additional_paths, ["A"])

    def test_rejects_additional_child_under_non_schema_parent(self):
        document = {
            "payload": {"A": "kept", "NEW": {"CHILD": "case-value"}},
            "reqParamStruct": [
                {"name": "ROOT", "children": [{"name": "A", "children": []}]}
            ],
            "expectedAdditionalPaths": ["NEW.CHILD"],
        }
        result = validator.validate(document, [])
        self.assertFalse(result.passed)
        self.assertEqual(result.invalid_expected_additional_paths, ["NEW.CHILD"])
        self.assertEqual(result.unexpected_additional_paths, ["NEW"])

    def test_requires_additional_field_in_every_array_item(self):
        struct = [
            {
                "name": "ROOT",
                "children": [
                    {
                        "name": "ITEMS",
                        "children": [{"name": "VALUE", "children": []}],
                    }
                ],
            }
        ]
        result = validator.validate(
            {
                "payload": {
                    "ITEMS": [
                        {"VALUE": "one", "EXTRA": "present"},
                        {"VALUE": "two"},
                    ]
                },
                "reqParamStruct": struct,
                "expectedAdditionalPaths": ["ITEMS.EXTRA"],
            },
            [],
        )
        self.assertFalse(result.passed)
        self.assertEqual(result.allowed_additional_paths, ["ITEMS[0].EXTRA"])
        self.assertEqual(
            result.expected_additional_but_missing_paths,
            ["ITEMS[1].EXTRA"],
        )

    def test_missing_and_additional_exceptions_can_apply_together(self):
        document = {
            "payload": {"A": "kept", "EXTRA": "case-value"},
            "reqParamStruct": [
                {
                    "name": "ROOT",
                    "children": [
                        {"name": "A", "children": []},
                        {"name": "B", "children": []},
                    ],
                }
            ],
            "expectedMissingPaths": ["ROOT.B"],
            "expectedAdditionalPaths": ["ROOT.EXTRA"],
        }
        result = validator.validate(document, [])
        self.assertTrue(result.passed)
        self.assertEqual(result.allowed_missing_paths, ["B"])
        self.assertEqual(result.allowed_additional_paths, ["EXTRA"])

    def test_command_line_accepts_only_declared_additional_field(self):
        document = {
            "payload": {"A": "kept", "EXTRA": "case-value"},
            "reqParamStruct": [
                {
                    "name": "ROOT",
                    "children": [{"name": "A", "children": []}],
                }
            ],
        }
        command = [
            sys.executable,
            "-B",
            str(SKILL_ROOT / "scripts" / "validate_generated_payload.py"),
            "--input",
            "-",
            "--expected-additional-field",
            "ROOT.EXTRA",
        ]
        completed = subprocess.run(
            command,
            input=json.dumps(document, ensure_ascii=False),
            check=False,
            capture_output=True,
            text=True,
            encoding="utf-8",
        )
        self.assertEqual(completed.returncode, 0, completed.stderr)
        output = json.loads(completed.stdout)
        self.assertTrue(output["passed"])
        self.assertEqual(output["allowedAdditionalPaths"], ["EXTRA"])
        self.assertEqual(output["unexpectedAdditionalPaths"], [])

    def test_rejects_nested_container_where_schema_declares_leaf(self):
        document = {
            "payload": {"A": {"EXTRA": "x"}},
            "reqParamStruct": [{"name": "ROOT", "children": [{"name": "A", "children": []}]}],
        }
        result = validator.validate(document, [])
        self.assertFalse(result.passed)
        self.assertIn("A", result.invalid_containers)

    def test_rejects_empty_array_and_checks_each_present_item(self):
        struct = [
            {
                "name": "ROOT",
                "children": [{"name": "ITEMS", "children": [{"name": "VALUE", "children": []}]}],
            }
        ]
        empty_result = validator.validate({"payload": {"ITEMS": []}, "reqParamStruct": struct}, [])
        self.assertFalse(empty_result.passed)
        self.assertIn("ITEMS", empty_result.invalid_containers)
        result = validator.validate(
            {"payload": {"ITEMS": [{"VALUE": "ok", "EXTRA": "x"}]}, "reqParamStruct": struct},
            [],
        )
        self.assertFalse(result.passed)
        self.assertIn("ITEMS[0].EXTRA", result.extra_paths)


class DataPreparationValidatorTest(unittest.TestCase):
    def test_preserves_long_sql_without_simplification(self):
        long_sql = "SELECT\n  a,\n  b\nFROM test_table\nWHERE id = '1'\n" + "AND status = 'A'\n" * 200
        item = {
            "stepType": 0,
            "executeTiming": "0",
            "caseIdList": "all",
            "dataType": "sql",
            "executeSql": long_sql,
        }
        result = data_preparation_validator.validate(
            {"referenceDataPreparations": [item], "generatedDataPreparations": [item.copy()]}
        )
        self.assertTrue(result.passed)

    def test_rejects_truncated_or_summarized_sql(self):
        reference = [{"dataType": "sql", "executeSql": "DELETE FROM a;\nINSERT INTO a VALUES (1);"}]
        generated = [{"dataType": "sql", "executeSql": "DELETE FROM a;\n..."}]
        result = data_preparation_validator.validate(
            {"referenceDataPreparations": reference, "generatedDataPreparations": generated}
        )
        self.assertFalse(result.passed)
        self.assertIn("dataPreparations[0].executeSql", result.changed_paths)

    def test_rejects_missing_and_reordered_actions(self):
        first = {"stepType": 0, "dataType": "sql", "executeSql": "INSERT INTO a VALUES (1)"}
        second = {"stepType": 1, "dataType": "sql", "executeSql": "DELETE FROM a WHERE id = 1"}
        missing = data_preparation_validator.validate(
            {"referenceDataPreparations": [first, second], "generatedDataPreparations": [first]}
        )
        reordered = data_preparation_validator.validate(
            {"referenceDataPreparations": [first, second], "generatedDataPreparations": [second, first]}
        )
        self.assertFalse(missing.passed)
        self.assertEqual(missing.missing_indexes, [1])
        self.assertFalse(reordered.passed)
        self.assertTrue(reordered.changed_paths)


class ReferenceContributionValidatorTest(unittest.TestCase):
    def contribution_document(self):
        return {
            "requiredReferenceKinds": ["TC", "INTEGRATED"],
            "sourceContent": {
                "TC": {"request": True, "dataPreparations": True, "assertions": True},
                "INTEGRATED": {"request": True, "dataPreparations": True, "assertions": True},
            },
            "sourceContributions": {
                dimension: {
                    "TC": {"status": "ADOPTED", "evidence": [f"{dimension}.tc"]},
                    "INTEGRATED": {
                        "status": "COMPLEMENTED",
                        "evidence": [f"{dimension}.integrated"],
                    },
                }
                for dimension in ("request", "dataPreparations", "assertions")
            },
        }

    def test_accepts_both_sources_in_all_available_dimensions(self):
        result = contribution_validator.validate(self.contribution_document())
        self.assertTrue(result.passed)

    def test_rejects_available_integrated_request_being_ignored(self):
        document = self.contribution_document()
        document["sourceContributions"]["request"]["INTEGRATED"] = {
            "status": "NO_CONTENT",
            "evidence": [],
        }
        result = contribution_validator.validate(document)
        self.assertFalse(result.passed)
        self.assertIn(
            "sourceContributions.request.INTEGRATED.status:not_referenced", result.issues
        )

    def test_rejects_one_source_with_no_active_contribution(self):
        document = self.contribution_document()
        for dimension in ("request", "dataPreparations", "assertions"):
            document["sourceContent"]["INTEGRATED"][dimension] = False
            document["sourceContributions"][dimension]["INTEGRATED"] = {
                "status": "NO_CONTENT",
                "evidence": [],
            }
        result = contribution_validator.validate(document)
        self.assertFalse(result.passed)
        self.assertIn("INTEGRATED:no_active_contribution", result.issues)


class TemporaryWorkspaceCleanupValidatorTest(unittest.TestCase):
    def create_output_target(self, directory: str) -> Path:
        output_target = Path(directory) / "042-测试执行"
        output_target.mkdir()
        return output_target

    def test_accepts_clean_output_with_baseline_and_generated_files(self):
        with tempfile.TemporaryDirectory() as directory:
            output_target = self.create_output_target(directory)
            (output_target / "existing.md").write_text("baseline", encoding="utf-8")
            (output_target / "generated.md").write_text("generated", encoding="utf-8")
            result = temp_cleanup_validator.validate(
                output_target,
                baseline_entries=["existing.md"],
                generated_files=["generated.md"],
            )
            self.assertTrue(result.passed)
            self.assertEqual(result.residual_paths, [])
            self.assertEqual(result.unexpected_output_entries, [])

    def test_rejects_reference_work_and_cleanup_manifests_at_any_depth(self):
        with tempfile.TemporaryDirectory() as directory:
            output_target = self.create_output_target(directory)
            (output_target / ".reference-work").mkdir()
            nested = output_target / "nested"
            nested.mkdir()
            (nested / "clean-up.json").write_text("{}", encoding="utf-8")
            (nested / "cleanup.json").write_text("{}", encoding="utf-8")
            result = temp_cleanup_validator.validate(output_target)
            self.assertFalse(result.passed)
            self.assertIn(".reference-work", result.residual_paths)
            self.assertIn("nested/clean-up.json", result.residual_paths)
            self.assertIn("nested/cleanup.json", result.residual_paths)

    def test_rejects_sibling_run_directory_and_invalid_inside_042_location(self):
        with tempfile.TemporaryDirectory() as directory:
            output_target = self.create_output_target(directory)
            temporary_run = Path(directory) / ".tmp" / "api-automation-run-1"
            temporary_run.mkdir(parents=True)
            result = temp_cleanup_validator.validate(
                output_target,
                temporary_run_directory=temporary_run,
            )
            self.assertFalse(result.passed)
            self.assertIn(".tmp/api-automation-run-1", result.residual_paths)
            with self.assertRaises(ValueError):
                temp_cleanup_validator.validate(
                    output_target,
                    temporary_run_directory=output_target / ".tmp" / "api-automation-run-1",
                )

    def test_rejects_empty_temporary_root_created_by_current_run(self):
        with tempfile.TemporaryDirectory() as directory:
            output_target = self.create_output_target(directory)
            temporary_root = Path(directory) / ".tmp"
            temporary_root.mkdir()
            temporary_run = temporary_root / "api-automation-current-run"
            result = temp_cleanup_validator.validate(
                output_target,
                temporary_run_directory=temporary_run,
                temporary_root_created=True,
            )
            self.assertFalse(result.passed)
            self.assertIn(".tmp", result.residual_paths)

    def test_accepts_other_concurrent_run_after_current_run_is_deleted(self):
        with tempfile.TemporaryDirectory() as directory:
            output_target = self.create_output_target(directory)
            temporary_root = Path(directory) / ".tmp"
            other_run = temporary_root / "api-automation-other-run"
            other_run.mkdir(parents=True)
            current_run = temporary_root / "api-automation-current-run"
            result = temp_cleanup_validator.validate(
                output_target,
                temporary_run_directory=current_run,
                temporary_root_created=True,
            )
            self.assertTrue(result.passed)
            self.assertEqual(result.residual_paths, [])
            self.assertTrue(other_run.is_dir())

    def test_rejects_unexpected_output_entry(self):
        with tempfile.TemporaryDirectory() as directory:
            output_target = self.create_output_target(directory)
            (output_target / "generated.md").write_text("generated", encoding="utf-8")
            (output_target / "stray.json").write_text("{}", encoding="utf-8")
            result = temp_cleanup_validator.validate(
                output_target,
                generated_files=["generated.md"],
            )
            self.assertFalse(result.passed)
            self.assertEqual(result.unexpected_output_entries, ["stray.json"])

    def test_command_line_writes_only_stdout(self):
        with tempfile.TemporaryDirectory() as directory:
            output_target = self.create_output_target(directory)
            completed = subprocess.run(
                [
                    sys.executable,
                    "-B",
                    str(SKILL_ROOT / "scripts" / "validate_temp_cleanup.py"),
                    "--output-target",
                    str(output_target),
                ],
                check=False,
                capture_output=True,
                text=True,
                encoding="utf-8",
            )
            self.assertEqual(completed.returncode, 0, completed.stderr)
            self.assertTrue(json.loads(completed.stdout)["passed"])
            self.assertEqual(list(output_target.iterdir()), [])


if __name__ == "__main__":
    unittest.main()
