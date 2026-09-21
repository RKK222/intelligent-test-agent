#!/usr/bin/env python3
"""Extract .xlsx and legacy BIFF8 .xls workbooks into deterministic JSON/TSV/Markdown.

This script intentionally uses only Python's standard library. It is designed for
agent workflows where Excel must be converted to text before semantic analysis.

Usage:
  python scripts/extract_excel.py FILE [FILE ...] --output-dir work/excel
  python scripts/extract_excel.py --input-dir ./assets --output-dir work/excel

Exit codes:
  0: all workbooks parsed
  2: one or more workbooks failed (successful workbooks are still written)
  64: invalid CLI usage
"""
from __future__ import annotations

import argparse
import csv
import datetime as dt
import hashlib
import json
import math
import re
import struct
import sys
import traceback
import zipfile
from dataclasses import dataclass
from pathlib import Path
from typing import Any, Iterator, Sequence
from xml.etree import ElementTree as ET

SCRIPT_VERSION = "3.0.0"
SUPPORTED_EXTENSIONS = {".xlsx", ".xlsm", ".xltx", ".xltm", ".xls"}
MAX_DENSE_ROWS = 10_000
MAX_DENSE_COLUMNS = 512
MAX_DENSE_CELLS = 1_000_000

BUILTIN_NUMBER_FORMATS = {
    0: "General",
    1: "0",
    2: "0.00",
    3: "#,##0",
    4: "#,##0.00",
    9: "0%",
    10: "0.00%",
    11: "0.00E+00",
    14: "m/d/yy",
    15: "d-mmm-yy",
    16: "d-mmm",
    17: "mmm-yy",
    18: "h:mm AM/PM",
    19: "h:mm:ss AM/PM",
    20: "h:mm",
    21: "h:mm:ss",
    22: "m/d/yy h:mm",
    45: "mm:ss",
    46: "[h]:mm:ss",
    47: "mmss.0",
    49: "@",
}


class ExcelExtractError(RuntimeError):
    pass


def u16(data: bytes, offset: int = 0) -> int:
    return struct.unpack_from("<H", data, offset)[0]


def i16(data: bytes, offset: int = 0) -> int:
    return struct.unpack_from("<h", data, offset)[0]


def u32(data: bytes, offset: int = 0) -> int:
    return struct.unpack_from("<I", data, offset)[0]


def i32(data: bytes, offset: int = 0) -> int:
    return struct.unpack_from("<i", data, offset)[0]


def f64(data: bytes, offset: int = 0) -> float:
    return struct.unpack_from("<d", data, offset)[0]


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def col_to_index(col: str) -> int:
    value = 0
    for ch in col.upper():
        if "A" <= ch <= "Z":
            value = value * 26 + (ord(ch) - 64)
    return value


def index_to_col(index: int) -> str:
    if index <= 0:
        return ""
    out = []
    while index:
        index, rem = divmod(index - 1, 26)
        out.append(chr(65 + rem))
    return "".join(reversed(out))


def normalize_scalar(value: Any) -> Any:
    if isinstance(value, float):
        if math.isnan(value) or math.isinf(value):
            return str(value)
        if value == int(value):
            return int(value)
    return value


def _format_code_for_detection(number_format: str) -> str:
    code = re.sub(r'"[^"]*"', "", number_format)
    code = re.sub(r"\\.", "", code)
    code = re.sub(r"\[[^\]]*\]", "", code)
    return code.lower()


def is_date_number_format(number_format: str) -> bool:
    code = _format_code_for_detection(number_format)
    return bool(re.search(r"(^|[^a-z])[ymdhs]+([^a-z]|$)", code))


def excel_serial_datetime(value: float | int, date_1904: bool) -> dt.datetime:
    base = dt.datetime(1904, 1, 1) if date_1904 else dt.datetime(1899, 12, 30)
    return base + dt.timedelta(days=float(value))


def format_excel_value(value: Any, number_format: str, date_1904: bool = False) -> str:
    if value is None:
        return ""
    if not isinstance(value, (int, float)) or isinstance(value, bool):
        return as_text(value)
    if is_date_number_format(number_format):
        parsed = excel_serial_datetime(value, date_1904)
        code = _format_code_for_detection(number_format)
        has_date = any(token in code for token in ("y", "d"))
        has_time = any(token in code for token in ("h", "s"))
        if has_date and has_time:
            return parsed.strftime("%Y-%m-%d %H:%M:%S")
        if has_time:
            return parsed.strftime("%H:%M:%S")
        return parsed.strftime("%Y-%m-%d")
    simple = re.sub(r'"[^"]*"', "", number_format).strip()
    if re.fullmatch(r"0+", simple) and float(value).is_integer():
        return str(int(value)).zfill(len(simple))
    decimal_match = re.fullmatch(r"[#,]*0\.([0]+)", simple)
    if decimal_match:
        return f"{value:.{len(decimal_match.group(1))}f}"
    if "%" in simple:
        decimals = len(re.search(r"\.([0]+)", simple).group(1)) if re.search(r"\.([0]+)", simple) else 0
        return f"{value * 100:.{decimals}f}%"
    if "E" in simple.upper():
        decimals = len(re.search(r"\.([0]+)", simple).group(1)) if re.search(r"\.([0]+)", simple) else 0
        return f"{value:.{decimals}E}"
    return as_text(normalize_scalar(value))


def as_text(value: Any) -> str:
    if value is None:
        return ""
    if isinstance(value, bool):
        return "TRUE" if value else "FALSE"
    return str(value).replace("\r\n", "\n").replace("\r", "\n")


def safe_name(name: str) -> str:
    name = re.sub(r"[\\/:*?\"<>|\x00-\x1f]", "_", name).strip().strip(".")
    return name or "Sheet"


# ---------------- XLSX parser ----------------

_NS_MAIN = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"
_NS_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
_NS_PKG_REL = "http://schemas.openxmlformats.org/package/2006/relationships"


def qn(ns: str, tag: str) -> str:
    return f"{{{ns}}}{tag}"


def _xlsx_shared_strings(zf: zipfile.ZipFile) -> list[str]:
    path = "xl/sharedStrings.xml"
    if path not in zf.namelist():
        return []
    root = ET.fromstring(zf.read(path))
    strings: list[str] = []
    for si in root.findall(qn(_NS_MAIN, "si")):
        parts = []
        direct = si.find(qn(_NS_MAIN, "t"))
        if direct is not None:
            parts.append(direct.text or "")
        for run in si.findall(qn(_NS_MAIN, "r")):
            t = run.find(qn(_NS_MAIN, "t"))
            if t is not None:
                parts.append(t.text or "")
        strings.append("".join(parts))
    return strings


def _xlsx_workbook_meta(zf: zipfile.ZipFile) -> list[dict[str, Any]]:
    wb_root = ET.fromstring(zf.read("xl/workbook.xml"))
    rel_root = ET.fromstring(zf.read("xl/_rels/workbook.xml.rels"))
    rels: dict[str, str] = {}
    for rel in rel_root.findall(qn(_NS_PKG_REL, "Relationship")):
        rels[rel.attrib["Id"]] = rel.attrib["Target"]
    sheets = []
    sheets_node = wb_root.find(qn(_NS_MAIN, "sheets"))
    if sheets_node is None:
        return sheets
    for idx, sh in enumerate(sheets_node.findall(qn(_NS_MAIN, "sheet")), start=1):
        rid = sh.attrib.get(qn(_NS_REL, "id"), "")
        target = rels.get(rid, "")
        if target.startswith("/"):
            xml_path = target.lstrip("/")
        else:
            xml_path = str(Path("xl") / target).replace("\\", "/")
        xml_path = re.sub(r"(^|/)\./", r"\1", xml_path)
        while "/../" in xml_path:
            parts = []
            for p in xml_path.split("/"):
                if p == ".." and parts:
                    parts.pop()
                elif p not in ("", "."):
                    parts.append(p)
            xml_path = "/".join(parts)
        sheets.append({
            "index": idx,
            "name": sh.attrib.get("name", f"Sheet{idx}"),
            "state": sh.attrib.get("state", "visible"),
            "xml_path": xml_path,
        })
    return sheets


def _xlsx_date_1904(zf: zipfile.ZipFile) -> bool:
    root = ET.fromstring(zf.read("xl/workbook.xml"))
    props = root.find(qn(_NS_MAIN, "workbookPr"))
    if props is None:
        return False
    return props.attrib.get("date1904") in {"1", "true", "True"}


def _xlsx_style_formats(zf: zipfile.ZipFile) -> list[str]:
    if "xl/styles.xml" not in zf.namelist():
        return []
    root = ET.fromstring(zf.read("xl/styles.xml"))
    custom = dict(BUILTIN_NUMBER_FORMATS)
    num_formats = root.find(qn(_NS_MAIN, "numFmts"))
    if num_formats is not None:
        for item in num_formats.findall(qn(_NS_MAIN, "numFmt")):
            try:
                custom[int(item.attrib["numFmtId"])] = item.attrib.get("formatCode", "General")
            except (KeyError, ValueError):
                continue
    formats: list[str] = []
    cell_xfs = root.find(qn(_NS_MAIN, "cellXfs"))
    if cell_xfs is None:
        return formats
    for xf in cell_xfs.findall(qn(_NS_MAIN, "xf")):
        try:
            fmt_id = int(xf.attrib.get("numFmtId", "0"))
        except ValueError:
            fmt_id = 0
        formats.append(custom.get(fmt_id, f"builtin:{fmt_id}"))
    return formats


def _xlsx_cell_value(cell: ET.Element, shared: Sequence[str]) -> tuple[Any, str, str | None]:
    ctype = cell.attrib.get("t", "n")
    formula_node = cell.find(qn(_NS_MAIN, "f"))
    formula = formula_node.text if formula_node is not None else None
    v_node = cell.find(qn(_NS_MAIN, "v"))
    value_text = v_node.text if v_node is not None else None
    if ctype == "inlineStr":
        is_node = cell.find(qn(_NS_MAIN, "is"))
        if is_node is None:
            return "", "string", formula
        parts = []
        t = is_node.find(qn(_NS_MAIN, "t"))
        if t is not None:
            parts.append(t.text or "")
        for run in is_node.findall(qn(_NS_MAIN, "r")):
            rt = run.find(qn(_NS_MAIN, "t"))
            if rt is not None:
                parts.append(rt.text or "")
        return "".join(parts), "string", formula
    if value_text is None:
        return None, "blank", formula
    if ctype == "s":
        try:
            return shared[int(value_text)], "string", formula
        except (ValueError, IndexError):
            return value_text, "string", formula
    if ctype == "b":
        return value_text == "1", "boolean", formula
    if ctype in {"str", "e"}:
        return value_text, "formula_string" if ctype == "str" else "error", formula
    try:
        value = float(value_text)
        return normalize_scalar(value), "number", formula
    except ValueError:
        return value_text, "string", formula


def parse_xlsx(path: Path) -> dict[str, Any]:
    with zipfile.ZipFile(path) as zf:
        if "xl/workbook.xml" not in zf.namelist():
            raise ExcelExtractError("not a valid OOXML workbook: xl/workbook.xml missing")
        shared = _xlsx_shared_strings(zf)
        sheet_meta = _xlsx_workbook_meta(zf)
        date_1904 = _xlsx_date_1904(zf)
        style_formats = _xlsx_style_formats(zf)
        sheets: list[dict[str, Any]] = []
        for meta in sheet_meta:
            if meta["xml_path"] not in zf.namelist():
                raise ExcelExtractError(f"worksheet XML missing: {meta['xml_path']}")
            root = ET.fromstring(zf.read(meta["xml_path"]))
            hidden_rows: list[int] = []
            hidden_cols: list[int] = []
            cols_node = root.find(qn(_NS_MAIN, "cols"))
            if cols_node is not None:
                for col in cols_node.findall(qn(_NS_MAIN, "col")):
                    if col.attrib.get("hidden") in {"1", "true", "True"}:
                        start = int(col.attrib.get("min", "1"))
                        end = int(col.attrib.get("max", str(start)))
                        hidden_cols.extend(range(start, end + 1))
            rows_out: list[dict[str, Any]] = []
            max_row = 0
            max_col = 0
            sheet_data = root.find(qn(_NS_MAIN, "sheetData"))
            if sheet_data is not None:
                for row in sheet_data.findall(qn(_NS_MAIN, "row")):
                    row_num = int(row.attrib.get("r", str(len(rows_out) + 1)))
                    is_hidden = row.attrib.get("hidden") in {"1", "true", "True"}
                    if is_hidden:
                        hidden_rows.append(row_num)
                    cells = []
                    inferred_col = 0
                    for cell in row.findall(qn(_NS_MAIN, "c")):
                        ref = cell.attrib.get("r", "")
                        m = re.match(r"([A-Z]+)(\d+)$", ref)
                        if m:
                            col_idx = col_to_index(m.group(1))
                        else:
                            col_idx = inferred_col + 1
                            ref = f"{index_to_col(col_idx)}{row_num}"
                        inferred_col = col_idx
                        value, value_type, formula = _xlsx_cell_value(cell, shared)
                        style_id = int(cell.attrib.get("s", "0"))
                        number_format = style_formats[style_id] if style_id < len(style_formats) else "General"
                        if value is None and formula is not None:
                            cell_state = "formula_blank"
                        elif value == "" and value_type in {"string", "formula_string"}:
                            cell_state = "explicit_empty_string"
                        elif value is None:
                            cell_state = "explicit_blank"
                        else:
                            cell_state = "value"
                        # Ignore style-only blank cells. They frequently inflate the used
                        # range to Excel's row/column limits without carrying asset data.
                        if cell_state != "explicit_blank":
                            cells.append({
                                "ref": ref,
                                "row": row_num,
                                "col": col_idx,
                                "value": value,
                                "display_value": format_excel_value(value, number_format, date_1904),
                                "type": value_type,
                                "formula": formula,
                                "style_id": style_id,
                                "number_format": number_format,
                                "cell_state": cell_state,
                            })
                            max_col = max(max_col, col_idx)
                    if cells or is_hidden:
                        rows_out.append({"row": row_num, "hidden": is_hidden, "cells": cells})
                        max_row = max(max_row, row_num)
            merges = []
            merge_node = root.find(qn(_NS_MAIN, "mergeCells"))
            if merge_node is not None:
                merges = [mc.attrib.get("ref", "") for mc in merge_node.findall(qn(_NS_MAIN, "mergeCell"))]
            sheets.append({
                "index": meta["index"],
                "name": meta["name"],
                "state": meta["state"],
                "max_row": max_row,
                "max_col": max_col,
                "hidden_rows": sorted(set(hidden_rows)),
                "hidden_columns": sorted(set(hidden_cols)),
                "merged_ranges": merges,
                "rows": rows_out,
            })
    return {
        "format": "xlsx",
        "date_system": "1904" if date_1904 else "1900",
        "sheets": sheets,
    }


# ---------------- CFBF + BIFF8 XLS parser ----------------

FREESECT = 0xFFFFFFFF
ENDOFCHAIN = 0xFFFFFFFE
FATSECT = 0xFFFFFFFD
DIFSECT = 0xFFFFFFFC
MAXREGSECT = 0xFFFFFFFA


@dataclass
class DirectoryEntry:
    name: str
    object_type: int
    start_sector: int
    stream_size: int


class CompoundFile:
    def __init__(self, data: bytes):
        self.data = data
        if data[:8] != bytes.fromhex("D0CF11E0A1B11AE1"):
            raise ExcelExtractError("invalid OLE Compound File signature")
        self.major_version = u16(data, 26)
        byte_order = u16(data, 28)
        if byte_order != 0xFFFE:
            raise ExcelExtractError(f"unsupported OLE byte order: 0x{byte_order:04X}")
        self.sector_size = 1 << u16(data, 30)
        self.mini_sector_size = 1 << u16(data, 32)
        self.num_fat_sectors = u32(data, 44)
        self.first_dir_sector = u32(data, 48)
        self.mini_stream_cutoff = u32(data, 56)
        self.first_mini_fat_sector = u32(data, 60)
        self.num_mini_fat_sectors = u32(data, 64)
        self.first_difat_sector = u32(data, 68)
        self.num_difat_sectors = u32(data, 72)
        self.difat = self._build_difat()
        self.fat = self._build_fat()
        self.directory = self._read_directory()
        roots = [e for e in self.directory if e.object_type == 5]
        if not roots:
            raise ExcelExtractError("OLE Root Entry not found")
        self.root = roots[0]
        self.mini_fat = self._build_mini_fat()
        self.mini_stream = self._read_regular_stream(self.root.start_sector, self.root.stream_size)

    def _sector(self, sector_id: int) -> bytes:
        if sector_id > MAXREGSECT:
            raise ExcelExtractError(f"invalid sector id: 0x{sector_id:08X}")
        offset = (sector_id + 1) * self.sector_size
        end = offset + self.sector_size
        if end > len(self.data):
            raise ExcelExtractError(f"sector {sector_id} exceeds file size")
        return self.data[offset:end]

    def _build_difat(self) -> list[int]:
        difat = [x for x in struct.unpack_from("<109I", self.data, 76) if x not in {FREESECT, ENDOFCHAIN}]
        next_sector = self.first_difat_sector
        entries_per = self.sector_size // 4 - 1
        seen = set()
        for _ in range(self.num_difat_sectors):
            if next_sector in {FREESECT, ENDOFCHAIN}:
                break
            if next_sector in seen:
                raise ExcelExtractError("DIFAT sector cycle detected")
            seen.add(next_sector)
            sector = self._sector(next_sector)
            entries = struct.unpack_from(f"<{entries_per}I", sector, 0)
            difat.extend(x for x in entries if x not in {FREESECT, ENDOFCHAIN})
            next_sector = u32(sector, entries_per * 4)
        return difat[: self.num_fat_sectors]

    def _build_fat(self) -> list[int]:
        fat: list[int] = []
        for sid in self.difat:
            fat.extend(struct.unpack(f"<{self.sector_size // 4}I", self._sector(sid)))
        return fat

    def _chain(self, start_sector: int, fat: Sequence[int], limit: int | None = None) -> list[int]:
        if start_sector in {FREESECT, ENDOFCHAIN}:
            return []
        chain = []
        seen = set()
        sid = start_sector
        max_steps = limit or (len(fat) + 1)
        for _ in range(max_steps):
            if sid in {FREESECT, ENDOFCHAIN}:
                break
            if sid > MAXREGSECT or sid >= len(fat):
                raise ExcelExtractError(f"invalid chain sector: 0x{sid:08X}")
            if sid in seen:
                raise ExcelExtractError("sector chain cycle detected")
            seen.add(sid)
            chain.append(sid)
            sid = fat[sid]
        else:
            raise ExcelExtractError("sector chain exceeds safety limit")
        return chain

    def _read_regular_stream(self, start_sector: int, size: int) -> bytes:
        if size <= 0:
            return b""
        chain = self._chain(start_sector, self.fat)
        data = b"".join(self._sector(sid) for sid in chain)
        return data[:size]

    def _read_directory(self) -> list[DirectoryEntry]:
        data = self._read_regular_stream(self.first_dir_sector, 1 << 31)
        entries: list[DirectoryEntry] = []
        for off in range(0, len(data) - 127, 128):
            raw = data[off: off + 128]
            name_len = u16(raw, 64)
            if name_len < 2 or name_len > 64:
                name = ""
            else:
                name = raw[: name_len - 2].decode("utf-16le", errors="replace")
            obj_type = raw[66]
            start = u32(raw, 116)
            size64 = struct.unpack_from("<Q", raw, 120)[0]
            size = size64 if self.major_version >= 4 else size64 & 0xFFFFFFFF
            if obj_type:
                entries.append(DirectoryEntry(name, obj_type, start, size))
        return entries

    def _build_mini_fat(self) -> list[int]:
        if self.num_mini_fat_sectors == 0 or self.first_mini_fat_sector in {FREESECT, ENDOFCHAIN}:
            return []
        chain = self._chain(self.first_mini_fat_sector, self.fat, self.num_mini_fat_sectors + 2)
        data = b"".join(self._sector(sid) for sid in chain[: self.num_mini_fat_sectors])
        if not data:
            return []
        return list(struct.unpack(f"<{len(data) // 4}I", data[: len(data) // 4 * 4]))

    def read_stream(self, entry: DirectoryEntry) -> bytes:
        if entry.stream_size < self.mini_stream_cutoff and self.mini_fat:
            chain = self._chain(entry.start_sector, self.mini_fat)
            chunks = []
            for sid in chain:
                off = sid * self.mini_sector_size
                chunks.append(self.mini_stream[off: off + self.mini_sector_size])
            return b"".join(chunks)[: entry.stream_size]
        return self._read_regular_stream(entry.start_sector, entry.stream_size)

    def workbook_stream(self) -> bytes:
        for wanted in ("Workbook", "Book"):
            for entry in self.directory:
                if entry.object_type == 2 and entry.name == wanted:
                    return self.read_stream(entry)
        available = ", ".join(e.name for e in self.directory if e.object_type == 2)
        raise ExcelExtractError(f"Workbook stream not found; streams: {available}")


@dataclass
class BiffRecord:
    record_id: int
    data: bytes
    offset: int


def iter_biff_records(stream: bytes, start: int = 0) -> Iterator[BiffRecord]:
    pos = start
    while pos + 4 <= len(stream):
        record_id, length = struct.unpack_from("<HH", stream, pos)
        end = pos + 4 + length
        if end > len(stream):
            raise ExcelExtractError(f"truncated BIFF record 0x{record_id:04X} at {pos}")
        yield BiffRecord(record_id, stream[pos + 4:end], pos)
        pos = end


class SegmentedCursor:
    """Cursor over SST/CONTINUE payloads, preserving segment boundaries."""
    def __init__(self, segments: Sequence[bytes]):
        self.segments = list(segments)
        self.si = 0
        self.pos = 0

    def remaining_in_segment(self) -> int:
        if self.si >= len(self.segments):
            return 0
        return len(self.segments[self.si]) - self.pos

    def at_end(self) -> bool:
        return self.si >= len(self.segments)

    def next_segment(self) -> bool:
        self.si += 1
        self.pos = 0
        return self.si < len(self.segments)

    def read(self, n: int) -> bytes:
        out = bytearray()
        while n > 0:
            if self.si >= len(self.segments):
                raise ExcelExtractError("unexpected end of SST continuation records")
            avail = len(self.segments[self.si]) - self.pos
            if avail == 0:
                self.next_segment()
                continue
            take = min(avail, n)
            out.extend(self.segments[self.si][self.pos:self.pos + take])
            self.pos += take
            n -= take
        return bytes(out)

    def read_u8(self) -> int:
        return self.read(1)[0]

    def read_u16(self) -> int:
        return u16(self.read(2))

    def read_u32(self) -> int:
        return u32(self.read(4))

    def read_chars(self, count: int, is_16bit: bool) -> str:
        pieces: list[str] = []
        remaining = count
        current_16 = is_16bit
        while remaining > 0:
            if self.si >= len(self.segments):
                raise ExcelExtractError("truncated SST character data")
            avail = len(self.segments[self.si]) - self.pos
            width = 2 if current_16 else 1
            chars_here = avail // width
            if chars_here > 0:
                take_chars = min(remaining, chars_here)
                raw = self.read(take_chars * width)
                pieces.append(raw.decode("utf-16le" if current_16 else "latin1", errors="replace"))
                remaining -= take_chars
                if remaining == 0:
                    break
            # BIFF8 CONTINUE starts continued character data with a new option byte.
            if self.remaining_in_segment() > 0:
                # A dangling byte in 16-bit data is invalid; skip defensively.
                self.read(self.remaining_in_segment())
            if not self.next_segment():
                raise ExcelExtractError("missing CONTINUE record for SST string")
            option = self.read_u8()
            current_16 = bool(option & 0x01)
        return "".join(pieces)


def parse_sst(segments: Sequence[bytes]) -> list[str]:
    if not segments:
        return []
    cur = SegmentedCursor(segments)
    _total = cur.read_u32()
    unique = cur.read_u32()
    strings: list[str] = []
    for _ in range(unique):
        cch = cur.read_u16()
        flags = cur.read_u8()
        is_16bit = bool(flags & 0x01)
        has_ext = bool(flags & 0x04)
        has_rich = bool(flags & 0x08)
        run_count = cur.read_u16() if has_rich else 0
        ext_size = cur.read_u32() if has_ext else 0
        text = cur.read_chars(cch, is_16bit)
        if run_count:
            cur.read(run_count * 4)
        if ext_size:
            cur.read(ext_size)
        strings.append(text)
    return strings


def decode_rk(raw: int) -> float | int:
    mult100 = bool(raw & 0x01)
    is_int = bool(raw & 0x02)
    if is_int:
        value: float | int = struct.unpack("<i", struct.pack("<I", raw))[0] >> 2
    else:
        bits = (raw & 0xFFFFFFFC) << 32
        value = struct.unpack("<d", struct.pack("<Q", bits))[0]
    if mult100:
        value = value / 100
    return normalize_scalar(value)


def decode_biff_short_string(data: bytes, offset: int = 0, len_bytes: int = 1) -> tuple[str, int]:
    if len_bytes == 1:
        cch = data[offset]
        pos = offset + 1
    else:
        cch = u16(data, offset)
        pos = offset + 2
    if pos >= len(data):
        return "", pos
    flags = data[pos]
    pos += 1
    is_16 = bool(flags & 0x01)
    byte_len = cch * (2 if is_16 else 1)
    raw = data[pos:pos + byte_len]
    return raw.decode("utf-16le" if is_16 else "latin1", errors="replace"), pos + byte_len


def parse_xls(path: Path) -> dict[str, Any]:
    cfb = CompoundFile(path.read_bytes())
    stream = cfb.workbook_stream()
    records = list(iter_biff_records(stream))

    boundsheets: list[dict[str, Any]] = []
    sst_segments: list[bytes] = []
    format_codes = dict(BUILTIN_NUMBER_FORMATS)
    xf_format_ids: list[int] = []
    date_1904 = False
    collecting_sst = False
    for rec in records:
        if collecting_sst and rec.record_id not in {0x003C, 0x00FC}:
            collecting_sst = False
        if rec.record_id == 0x0085:  # BOUNDSHEET
            if len(rec.data) < 8:
                continue
            offset = u32(rec.data, 0)
            visibility = rec.data[4]
            sheet_type = rec.data[5]
            name, _ = decode_biff_short_string(rec.data, 6, 1)
            boundsheets.append({
                "offset": offset,
                "name": name,
                "state": {0: "visible", 1: "hidden", 2: "veryHidden"}.get(visibility, f"unknown:{visibility}"),
                "sheet_type": sheet_type,
            })
        elif rec.record_id == 0x00FC:  # SST
            sst_segments = [rec.data]
            collecting_sst = True
        elif rec.record_id == 0x003C and collecting_sst:  # CONTINUE
            sst_segments.append(rec.data)
        elif rec.record_id == 0x041E and len(rec.data) >= 5:  # FORMAT
            fmt_id = u16(rec.data, 0)
            try:
                fmt_code, _ = decode_biff_short_string(rec.data, 2, 2)
                format_codes[fmt_id] = fmt_code
            except Exception:
                format_codes[fmt_id] = f"custom:{fmt_id}"
        elif rec.record_id == 0x00E0 and len(rec.data) >= 4:  # XF
            xf_format_ids.append(u16(rec.data, 2))
        elif rec.record_id == 0x0022 and len(rec.data) >= 2:  # DATE1904
            date_1904 = bool(u16(rec.data, 0))
    shared = parse_sst(sst_segments) if sst_segments else []

    sheets: list[dict[str, Any]] = []
    for idx, meta in enumerate(boundsheets, start=1):
        if meta["sheet_type"] != 0:
            continue
        cell_map: dict[tuple[int, int], dict[str, Any]] = {}
        hidden_rows: set[int] = set()
        hidden_cols: set[int] = set()
        merged_ranges: list[str] = []
        pending_formula_cell: tuple[int, int] | None = None
        max_row = 0
        max_col = 0
        for rec in iter_biff_records(stream, meta["offset"]):
            rid, data = rec.record_id, rec.data
            if rid == 0x000A:  # EOF
                break
            if rid == 0x0208 and len(data) >= 16:  # ROW
                row = u16(data, 0) + 1
                options = u16(data, 12)
                if options & 0x0020:
                    hidden_rows.add(row)
            elif rid == 0x007D and len(data) >= 12:  # COLINFO
                first = u16(data, 0) + 1
                last = u16(data, 2) + 1
                options = u16(data, 8)
                if options & 0x0001:
                    hidden_cols.update(range(first, last + 1))
            elif rid == 0x00E5 and len(data) >= 2:  # MERGEDCELLS
                count = u16(data, 0)
                pos = 2
                for _ in range(count):
                    if pos + 8 > len(data):
                        break
                    r1, r2, c1, c2 = struct.unpack_from("<HHHH", data, pos)
                    merged_ranges.append(f"{index_to_col(c1+1)}{r1+1}:{index_to_col(c2+1)}{r2+1}")
                    pos += 8
            elif rid in {0x0203, 0x027E, 0x00FD, 0x0204, 0x0205, 0x0006, 0x00BD}:
                def put(row0: int, col0: int, value: Any, typ: str, xf: int = 0, formula: str | None = None):
                    nonlocal max_row, max_col
                    row, col = row0 + 1, col0 + 1
                    normalized = normalize_scalar(value)
                    # Ignore empty-string records used only for workbook formatting.
                    if normalized is None and formula is None:
                        return
                    if normalized == "" and typ not in {"string", "formula_string"} and formula is None:
                        return
                    fmt_id = xf_format_ids[xf] if xf < len(xf_format_ids) else 0
                    number_format = format_codes.get(fmt_id, f"builtin:{fmt_id}")
                    if normalized is None and formula is not None:
                        cell_state = "formula_blank"
                    elif normalized == "" and typ in {"string", "formula_string"}:
                        cell_state = "explicit_empty_string" if formula is None else "formula_blank"
                    else:
                        cell_state = "value"
                    cell_map[(row, col)] = {
                        "ref": f"{index_to_col(col)}{row}", "row": row, "col": col,
                        "value": normalized,
                        "display_value": format_excel_value(normalized, number_format, date_1904),
                        "type": typ,
                        "formula": formula,
                        "style_id": xf,
                        "number_format": number_format,
                        "cell_state": cell_state,
                    }
                    max_row, max_col = max(max_row, row), max(max_col, col)

                if rid == 0x0203 and len(data) >= 14:  # NUMBER
                    put(u16(data, 0), u16(data, 2), f64(data, 6), "number", u16(data, 4))
                elif rid == 0x027E and len(data) >= 10:  # RK
                    put(u16(data, 0), u16(data, 2), decode_rk(u32(data, 6)), "number", u16(data, 4))
                elif rid == 0x00BD and len(data) >= 6:  # MULRK
                    row0, first_col = u16(data, 0), u16(data, 2)
                    last_col = u16(data, len(data) - 2)
                    pos = 4
                    for col0 in range(first_col, last_col + 1):
                        if pos + 6 > len(data) - 2:
                            break
                        xf, rk = u16(data, pos), u32(data, pos + 2)
                        put(row0, col0, decode_rk(rk), "number", xf)
                        pos += 6
                elif rid == 0x00FD and len(data) >= 10:  # LABELSST
                    sidx = u32(data, 6)
                    value = shared[sidx] if sidx < len(shared) else f"#SST[{sidx}]"
                    put(u16(data, 0), u16(data, 2), value, "string", u16(data, 4))
                elif rid == 0x0204 and len(data) >= 8:  # LABEL (legacy)
                    row0, col0, xf, length = u16(data, 0), u16(data, 2), u16(data, 4), u16(data, 6)
                    raw = data[8:8 + length]
                    try:
                        value = raw.decode("latin1")
                    except Exception:
                        value = raw.hex()
                    put(row0, col0, value, "string", xf)
                elif rid == 0x0205 and len(data) >= 8:  # BOOLERR
                    value_byte, is_error = data[6], data[7]
                    put(u16(data, 0), u16(data, 2), f"#ERR({value_byte})" if is_error else bool(value_byte), "error" if is_error else "boolean", u16(data, 4))
                elif rid == 0x0006 and len(data) >= 14:  # FORMULA
                    row0, col0, xf = u16(data, 0), u16(data, 2), u16(data, 4)
                    result = data[6:14]
                    if result[6:8] == b"\xFF\xFF":
                        special = result[0]
                        if special == 0:
                            value, typ = "", "formula_string"
                            pending_formula_cell = (row0 + 1, col0 + 1)
                        elif special == 1:
                            value, typ = bool(result[2]), "boolean"
                        elif special == 2:
                            value, typ = f"#ERR({result[2]})", "error"
                        else:
                            value, typ = None, "blank"
                    else:
                        value, typ = f64(result), "number"
                    put(row0, col0, value, typ, xf, formula="<BIFF token formula>")
            elif rid == 0x0207 and pending_formula_cell is not None:  # STRING result for formula
                value, _ = decode_biff_short_string(data, 0, 2)
                if pending_formula_cell in cell_map:
                    cell_map[pending_formula_cell]["value"] = value
                    cell_map[pending_formula_cell]["display_value"] = value
                    cell_map[pending_formula_cell]["type"] = "formula_string"
                    cell_map[pending_formula_cell]["cell_state"] = "formula_blank" if value == "" else "value"
                pending_formula_cell = None
        rows_dict: dict[int, list[dict[str, Any]]] = {}
        for (row, _col), cell in sorted(cell_map.items()):
            rows_dict.setdefault(row, []).append(cell)
        rows_out = [
            {"row": row, "hidden": row in hidden_rows, "cells": cells}
            for row, cells in sorted(rows_dict.items())
        ]
        for row in sorted(hidden_rows - set(rows_dict)):
            rows_out.append({"row": row, "hidden": True, "cells": []})
        rows_out.sort(key=lambda r: r["row"])
        sheets.append({
            "index": idx,
            "name": meta["name"] or f"Sheet{idx}",
            "state": meta["state"],
            "max_row": max_row,
            "max_col": max_col,
            "hidden_rows": sorted(hidden_rows),
            "hidden_columns": sorted(hidden_cols),
            "merged_ranges": merged_ranges,
            "rows": rows_out,
        })
    return {
        "format": "xls-biff8",
        "date_system": "1904" if date_1904 else "1900",
        "sheets": sheets,
    }


# ---------------- Output ----------------


def iter_sheet_cells(sheet: dict[str, Any]) -> Iterator[tuple[int, bool, dict[str, Any]]]:
    for row in sheet.get("rows", []):
        for cell in row.get("cells", []):
            yield row["row"], bool(row.get("hidden")), cell


def sheet_content_bounds(sheet: dict[str, Any]) -> tuple[int, int, int]:
    cells = list(iter_sheet_cells(sheet))
    if not cells:
        return 0, 0, 0
    return (
        max(row_num for row_num, _hidden, _cell in cells),
        max(cell["col"] for _row_num, _hidden, cell in cells),
        len(cells),
    )


def choose_table_mode(max_row: int, max_col: int) -> str:
    if (
        max_row <= MAX_DENSE_ROWS
        and max_col <= MAX_DENSE_COLUMNS
        and max_row * max_col <= MAX_DENSE_CELLS
    ):
        return "dense"
    return "sparse"


def detect_header_candidates(sheet: dict[str, Any], limit: int = 30) -> list[dict[str, Any]]:
    candidates = []
    for row in sheet.get("rows", []):
        row_num = int(row["row"])
        if row_num > limit:
            continue
        values = []
        for cell in sorted(row.get("cells", []), key=lambda item: item["col"]):
            value = as_text(cell.get("display_value", cell.get("value"))).strip()
            if value:
                values.append(value)
        if len(values) >= 2:
            unique_ratio = len(set(values)) / len(values)
            candidates.append({
                "row": row_num,
                "non_empty_cells": len(values),
                "unique_ratio": round(unique_ratio, 3),
                "values": values[:20],
            })
    candidates.sort(key=lambda item: (-item["non_empty_cells"], -item["unique_ratio"], item["row"]))
    return candidates[:5]


def write_dense_table(sheet: dict[str, Any], path: Path, max_row: int, max_col: int) -> None:
    by_row: dict[int, dict[int, str]] = {}
    for row_num, _hidden, cell in iter_sheet_cells(sheet):
        by_row.setdefault(row_num, {})[cell["col"]] = as_text(
            cell.get("display_value", cell.get("value"))
        )
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        for row_num in range(1, max_row + 1):
            row_values = by_row.get(row_num, {})
            writer.writerow([row_values.get(col, "") for col in range(1, max_col + 1)])


def write_sparse_table(sheet: dict[str, Any], path: Path) -> None:
    hidden_columns = set(sheet.get("hidden_columns", []))
    with path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.writer(stream, delimiter="\t", lineterminator="\n")
        writer.writerow([
            "row", "column", "ref", "cell_state", "raw_value", "display_value",
            "type", "number_format", "formula", "hidden_row", "hidden_column",
        ])
        for row_num, hidden_row, cell in iter_sheet_cells(sheet):
            writer.writerow([
                row_num,
                cell["col"],
                cell["ref"],
                cell.get("cell_state", "value"),
                as_text(cell.get("value")),
                as_text(cell.get("display_value", cell.get("value"))),
                cell.get("type", ""),
                cell.get("number_format", "General"),
                cell.get("formula") or "",
                "TRUE" if hidden_row else "FALSE",
                "TRUE" if cell["col"] in hidden_columns else "FALSE",
            ])


def write_outputs(source: Path, parsed: dict[str, Any], output_root: Path) -> dict[str, Any]:
    source_sha = sha256_file(source)
    stem_dir = output_root / f"{safe_name(source.name)}-{source_sha[:8]}"
    tables_dir = stem_dir / "tables"
    tables_dir.mkdir(parents=True, exist_ok=True)
    payload = {
        "extractor": {"name": "extract_excel.py", "version": SCRIPT_VERSION},
        "source": {
            "path": str(source.resolve()),
            "name": source.name,
            "size": source.stat().st_size,
            "sha256": source_sha,
        },
        **parsed,
    }
    for sheet in payload.get("sheets", []):
        content_max_row, content_max_col, cell_count = sheet_content_bounds(sheet)
        table_mode = choose_table_mode(content_max_row, content_max_col)
        sheet["content_max_row"] = content_max_row
        sheet["content_max_col"] = content_max_col
        sheet["cell_count"] = cell_count
        sheet["table_mode"] = table_mode
        sheet["header_candidates"] = detect_header_candidates(sheet)
        tsv_path = tables_dir / f"{sheet['index']:02d}-{safe_name(sheet['name'])}.tsv"
        if table_mode == "dense":
            write_dense_table(sheet, tsv_path, content_max_row, content_max_col)
        else:
            write_sparse_table(sheet, tsv_path)
        sheet["tsv_path"] = str(tsv_path.relative_to(stem_dir))
    json_path = stem_dir / "workbook.json"
    json_path.write_text(json.dumps(payload, ensure_ascii=False, indent=2), encoding="utf-8")

    md_lines = [
        f"# Excel 提取结果：{source.name}", "",
        f"- 格式：`{payload['format']}`",
        f"- SHA-256：`{payload['source']['sha256']}`",
        f"- 工作表数量：{len(payload.get('sheets', []))}", "",
    ]
    for sheet in payload.get("sheets", []):
        md_lines.extend([
            f"## {sheet['index']}. {sheet['name']}", "",
            f"- 状态：{sheet['state']}",
            f"- 内容范围：{sheet['content_max_row']} 行 × {sheet['content_max_col']} 列",
            f"- 单元格数量：{sheet['cell_count']}",
            f"- 表格模式：`{sheet['table_mode']}`",
            f"- 隐藏行：{', '.join(map(str, sheet['hidden_rows'])) or '无'}",
            f"- 隐藏列：{', '.join(index_to_col(i) for i in sheet['hidden_columns']) or '无'}",
            f"- TSV：`{sheet['tsv_path']}`", "",
            "### 表头候选", "",
            "| 行号 | 非空列数 | 内容预览 |", "| --- | ---: | --- |",
        ])
        for cand in sheet.get("header_candidates", []):
            preview = " / ".join(v.replace("|", "\\|") for v in cand["values"])
            md_lines.append(f"| {cand['row']} | {cand['non_empty_cells']} | {preview} |")
        md_lines.append("")
    (stem_dir / "summary.md").write_text("\n".join(md_lines), encoding="utf-8")
    return {
        "source": str(source),
        "status": "ok",
        "format": payload["format"],
        "sheet_count": len(payload.get("sheets", [])),
        "output_dir": str(stem_dir),
        "json": str(json_path),
    }


def discover_inputs(paths: Sequence[str], input_dir: str | None) -> list[Path]:
    found: list[Path] = []
    for raw in paths:
        p = Path(raw)
        if p.is_dir():
            found.extend(x for x in p.rglob("*") if x.is_file() and x.suffix.lower() in SUPPORTED_EXTENSIONS)
        elif p.is_file():
            found.append(p)
        else:
            raise ExcelExtractError(f"input does not exist: {p}")
    if input_dir:
        p = Path(input_dir)
        if not p.is_dir():
            raise ExcelExtractError(f"--input-dir is not a directory: {p}")
        found.extend(x for x in p.rglob("*") if x.is_file() and x.suffix.lower() in SUPPORTED_EXTENSIONS)
    unique = []
    seen = set()
    for p in found:
        rp = p.resolve()
        if rp not in seen:
            seen.add(rp)
            unique.append(p)
    return sorted(unique, key=lambda p: str(p))


def parse_one(path: Path) -> dict[str, Any]:
    ext = path.suffix.lower()
    if ext in {".xlsx", ".xlsm", ".xltx", ".xltm"}:
        return parse_xlsx(path)
    if ext == ".xls":
        return parse_xls(path)
    raise ExcelExtractError(f"unsupported extension: {ext}")


def build_parser() -> argparse.ArgumentParser:
    p = argparse.ArgumentParser(description="Extract xls/xlsx to deterministic text assets")
    p.add_argument("files", nargs="*", help="Excel files or directories")
    p.add_argument("--input-dir", help="Recursively scan a directory")
    p.add_argument("--output-dir", required=True, help="Output directory")
    p.add_argument("--debug", action="store_true", help="Include Python tracebacks in error report")
    return p


def write_extract_report(output_root: Path, results: list[dict[str, Any]]) -> None:
    report = {
        "extractor_version": SCRIPT_VERSION,
        "generated_at": dt.datetime.now(dt.timezone.utc).isoformat(),
        "success_count": sum(1 for item in results if item["status"] == "ok"),
        "error_count": sum(1 for item in results if item["status"] == "error"),
        "results": results,
    }
    (output_root / "extract-report.json").write_text(
        json.dumps(report, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def main(argv: Sequence[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    if not args.files and not args.input_dir:
        print("error: provide files/directories or --input-dir", file=sys.stderr)
        return 64
    output_root = Path(args.output_dir)
    output_root.mkdir(parents=True, exist_ok=True)
    try:
        inputs = discover_inputs(args.files, args.input_dir)
    except Exception as exc:
        write_extract_report(output_root, [{
            "source": args.input_dir or ", ".join(args.files),
            "status": "error",
            "error_type": type(exc).__name__,
            "message": str(exc),
            **({"traceback": traceback.format_exc()} if args.debug else {}),
        }])
        print(f"error: {exc}", file=sys.stderr)
        return 64
    if not inputs:
        write_extract_report(output_root, [{
            "source": args.input_dir or ", ".join(args.files),
            "status": "error",
            "error_type": "ExcelExtractError",
            "message": "no supported Excel files found",
        }])
        print("error: no supported Excel files found", file=sys.stderr)
        return 64
    results = []
    failed = False
    for path in inputs:
        try:
            parsed = parse_one(path)
            result = write_outputs(path, parsed, output_root)
            results.append(result)
            print(f"[OK] {path} -> {result['output_dir']}")
        except Exception as exc:
            failed = True
            item = {"source": str(path), "status": "error", "error_type": type(exc).__name__, "message": str(exc)}
            if args.debug:
                item["traceback"] = traceback.format_exc()
            results.append(item)
            print(f"[ERROR] {path}: {exc}", file=sys.stderr)
    write_extract_report(output_root, results)
    return 2 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
