#!/usr/bin/env python3
"""Regression test for the standard-library Excel extractor."""
from __future__ import annotations

import json
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path


CONTENT_TYPES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
</Types>
"""
ROOT_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>
"""
WORKBOOK = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"
 xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <workbookPr date1904="0"/>
  <sheets>
    <sheet name="Data" sheetId="1" r:id="rId1"/>
    <sheet name="HiddenConfig" sheetId="2" state="hidden" r:id="rId2"/>
  </sheets>
</workbook>
"""
WORKBOOK_RELS = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>
"""
STYLES = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <numFmts count="2">
    <numFmt numFmtId="165" formatCode="000000"/>
    <numFmt numFmtId="166" formatCode="yyyy-mm-dd"/>
  </numFmts>
  <fonts count="1"><font/></fonts>
  <fills count="1"><fill/></fills>
  <borders count="1"><border/></borders>
  <cellStyleXfs count="1"><xf numFmtId="0"/></cellStyleXfs>
  <cellXfs count="3">
    <xf numFmtId="0"/>
    <xf numFmtId="165" applyNumberFormat="1"/>
    <xf numFmtId="166" applyNumberFormat="1"/>
  </cellXfs>
</styleSheet>
"""
SHEET1 = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <cols><col min="2" max="2" hidden="1"/></cols>
  <sheetData>
    <row r="1">
      <c r="A1" t="inlineStr"><is><t>empty</t></is></c>
      <c r="B1" t="inlineStr"><is><t>code</t></is></c>
      <c r="C1" t="inlineStr"><is><t>date</t></is></c>
      <c r="D1" t="inlineStr"><is><t>formula</t></is></c>
    </row>
    <row r="2">
      <c r="A2" t="inlineStr"><is><t></t></is></c>
      <c r="B2" s="1"><v>123</v></c>
      <c r="C2" s="2"><v>45292</v></c>
      <c r="D2" t="str"><f>""</f><v></v></c>
    </row>
    <row r="1048576">
      <c r="XFD1048576" t="inlineStr"><is><t>tail</t></is></c>
    </row>
  </sheetData>
</worksheet>
"""
SHEET2 = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>secret config</t></is></c></row></sheetData>
</worksheet>
"""


def build_fixture(path: Path) -> None:
    with zipfile.ZipFile(path, "w", zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("[Content_Types].xml", CONTENT_TYPES)
        archive.writestr("_rels/.rels", ROOT_RELS)
        archive.writestr("xl/workbook.xml", WORKBOOK)
        archive.writestr("xl/_rels/workbook.xml.rels", WORKBOOK_RELS)
        archive.writestr("xl/styles.xml", STYLES)
        archive.writestr("xl/worksheets/sheet1.xml", SHEET1)
        archive.writestr("xl/worksheets/sheet2.xml", SHEET2)


def main() -> int:
    skill_dir = Path(__file__).resolve().parents[1]
    extractor = skill_dir / "scripts" / "extract_excel.py"
    with tempfile.TemporaryDirectory(prefix="excel-extractor-test-") as tmp:
        root = Path(tmp)
        fixture = root / "fidelity.xlsx"
        output = root / "output"
        build_fixture(fixture)
        result = subprocess.run(
            [sys.executable, str(extractor), str(fixture), "--output-dir", str(output), "--debug"],
            check=False,
            text=True,
            capture_output=True,
        )
        assert result.returncode == 0, result.stderr
        report = json.loads((output / "extract-report.json").read_text(encoding="utf-8"))
        assert report["success_count"] == 1 and report["error_count"] == 0
        workbook_path = Path(report["results"][0]["json"])
        workbook = json.loads(workbook_path.read_text(encoding="utf-8"))
        assert workbook["date_system"] == "1900"
        assert len(workbook["sheets"]) == 2
        data = workbook["sheets"][0]
        assert data["table_mode"] == "sparse"
        assert data["hidden_columns"] == [2]
        cells = {
            cell["ref"]: cell
            for row in data["rows"]
            for cell in row["cells"]
        }
        assert cells["A2"]["cell_state"] == "explicit_empty_string"
        assert cells["A2"]["display_value"] == ""
        assert cells["B2"]["display_value"] == "000123"
        assert cells["B2"]["number_format"] == "000000"
        assert cells["C2"]["display_value"] == "2024-01-01"
        assert cells["D2"]["cell_state"] == "formula_blank"
        assert cells["XFD1048576"]["display_value"] == "tail"
        assert workbook["sheets"][1]["state"] == "hidden"
        tsv = workbook_path.parent / data["tsv_path"]
        assert tsv.read_text(encoding="utf-8").startswith("row\tcolumn\tref\tcell_state")
        failed_output = root / "failed-output"
        failed = subprocess.run(
            [
                sys.executable,
                str(extractor),
                str(root / "missing.xlsx"),
                "--output-dir",
                str(failed_output),
                "--debug",
            ],
            check=False,
            text=True,
            capture_output=True,
        )
        assert failed.returncode == 64
        failed_report = json.loads(
            (failed_output / "extract-report.json").read_text(encoding="utf-8")
        )
        assert failed_report["error_count"] == 1
    print("test_extract_excel.py passed")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
