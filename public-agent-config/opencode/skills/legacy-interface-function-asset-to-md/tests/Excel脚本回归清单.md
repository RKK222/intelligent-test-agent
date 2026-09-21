# Excel 脚本回归清单

自动执行：

```bash
python tests/test_extract_excel.py
```

覆盖：

- `.xlsx` 工作表名称、隐藏状态和隐藏列；
- 显式空字符串与公式空值；
- 数字格式化前导零；
- 日期显示值；
- 远端单元格触发稀疏 TSV，避免巨大矩阵；
- `workbook.json` 和 `extract-report.json`。

修改 BIFF8 解析代码时，另用真实 `.xls` 验证：

- 中文 SST 与 CONTINUE；
- 合并单元格；
- 隐藏工作表、行和列；
- NUMBER、RK、LABELSST、FORMULA；
- 多文件中单个失败时其他文件仍输出。
