package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.junit.jupiter.api.Test;

class RequirementDocumentConverterTest {

    @Test
    void convertsUtf8Gb18030AndDocxToMarkdown() throws Exception {
        assertThat(RequirementDocumentConverter.toMarkdown("需求.md", "中文正文".getBytes()))
                .isEqualTo("中文正文\n");
        assertThat(RequirementDocumentConverter.toMarkdown(
                "需求.txt", "传统编码".getBytes(Charset.forName("GB18030"))))
                .isEqualTo("传统编码\n");

        byte[] docx;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.createParagraph().createRun().setText("Word 需求");
            document.write(output);
            docx = output.toByteArray();
        }
        assertThat(RequirementDocumentConverter.toMarkdown("需求.docx", docx)).contains("Word 需求");
        assertThat(RequirementDocumentConverter.toMarkdown(
                "历史名称.doc", "application/octet-stream", docx))
                .as("TCDS 历史数据可能以 .doc 名称返回 DOCX 内容")
                .contains("Word 需求");
        assertThat(RequirementDocumentConverter.toMarkdown(
                "纯文本旧文档.doc", "text/plain; charset=UTF-8", "文本需求".getBytes(StandardCharsets.UTF_8)))
                .contains("文本需求");
    }

    @Test
    void rejectsUnknownBinaryFormat() {
        assertThatThrownBy(() -> RequirementDocumentConverter.toMarkdown("secret.bin", new byte[] {1, 2, 3}))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR));
    }

    @Test
    void convertsExcelAndPowerPointToMarkdown() throws Exception {
        byte[] xlsx;
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var row = workbook.createSheet("案例").createRow(0);
            row.createCell(0).setCellValue("编号");
            row.createCell(1).setCellValue("TC-01");
            workbook.write(output);
            xlsx = output.toByteArray();
        }
        assertThat(RequirementDocumentConverter.toMarkdown("案例.xlsx", xlsx))
                .contains("## 案例", "| 编号 | TC-01 |");

        byte[] pptx;
        try (XMLSlideShow slideShow = new XMLSlideShow(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            slideShow.createSlide().createTextBox().setText("方案说明");
            slideShow.write(output);
            pptx = output.toByteArray();
        }
        assertThat(RequirementDocumentConverter.toMarkdown("设计.pptx", pptx))
                .contains("## 幻灯片 1", "方案说明");
        assertThat(RequirementDocumentConverter.toMarkdown(
                "历史名称.ppt", "application/octet-stream", pptx))
                .contains("## 幻灯片 1", "方案说明");
    }

    @Test
    void rejectsSuccessfulHtmlOrJsonErrorEnvelopeInsteadOfWritingItAsMarkdown() {
        assertThatThrownBy(() -> RequirementDocumentConverter.toMarkdown(
                "设计.doc",
                "text/html; charset=UTF-8",
                "<html><body>login</body></html>".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(PlatformException.class, exception -> {
                    assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_API_UNAVAILABLE);
                    assertThat(exception.getMessage()).isEqualTo("TCDS 文档下载内容无效");
                });
        assertThatThrownBy(() -> RequirementDocumentConverter.toMarkdown(
                "设计.doc",
                "application/octet-stream",
                "{\"code\":\"401\",\"error\":\"expired\"}".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOfSatisfying(PlatformException.class, exception ->
                        assertThat(exception.errorCode()).isEqualTo(ErrorCode.EXTERNAL_API_UNAVAILABLE));
    }
}
