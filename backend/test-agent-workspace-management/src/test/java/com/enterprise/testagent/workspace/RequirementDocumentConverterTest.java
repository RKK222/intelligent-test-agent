package com.enterprise.testagent.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.XWPFAbstractNum;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTAbstractNum;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STNumberFormat;
import org.junit.jupiter.api.Test;

class RequirementDocumentConverterTest {

    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

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

    @Test
    void rendersDocxStructureFormattingTablesLinksListsAndImages() throws Exception {
        byte[] docx;
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var heading = document.createParagraph();
            heading.setStyle("Heading2");
            heading.createRun().setText("接口设计");
            var outlineHeading = document.createParagraph();
            outlineHeading.getCTP().addNewPPr().addNewOutlineLvl().setVal(BigInteger.ZERO);
            outlineHeading.createRun().setText("自定义大纲标题");

            var formatted = document.createParagraph();
            var bold = formatted.createRun();
            bold.setBold(true);
            bold.setText("粗体");
            formatted.createRun().setText("、");
            var italic = formatted.createRun();
            italic.setItalic(true);
            italic.setText("斜体");
            formatted.createRun().setText("、");
            var strike = formatted.createRun();
            strike.setStrikeThrough(true);
            strike.setText("删除");
            formatted.createRun().setText("，详见");
            formatted.createHyperlinkRun("https://example.internal/spec").setText("接口规范");
            formatted.createRun().setText("，");
            formatted.createHyperlinkRun("javascript:alert(1)").setText("不安全链接");
            var lineBreak = formatted.createRun();
            lineBreak.setText("第一行");
            lineBreak.addBreak();
            lineBreak.setText("第二行", 1);

            CTAbstractNum abstractNum = CTAbstractNum.Factory.newInstance();
            abstractNum.setAbstractNumId(BigInteger.ZERO);
            var bulletLevel = abstractNum.addNewLvl();
            bulletLevel.setIlvl(BigInteger.ZERO);
            bulletLevel.addNewNumFmt().setVal(STNumberFormat.BULLET);
            var level = abstractNum.addNewLvl();
            level.setIlvl(BigInteger.ONE);
            level.addNewNumFmt().setVal(STNumberFormat.DECIMAL);
            BigInteger abstractId = document.createNumbering().addAbstractNum(new XWPFAbstractNum(abstractNum));
            BigInteger numId = document.getNumbering().addNum(abstractId);
            var list = document.createParagraph();
            list.setNumID(numId);
            list.setNumILvl(BigInteger.ONE);
            list.createRun().setText("嵌套步骤");
            var bullet = document.createParagraph();
            bullet.setNumID(numId);
            bullet.setNumILvl(BigInteger.ZERO);
            bullet.createRun().setText("无序检查项");

            var table = document.createTable(2, 2);
            table.getRow(0).getCell(0).setText("字段");
            table.getRow(0).getCell(1).setText("说明");
            table.getRow(1).getCell(0).setText("name");
            table.getRow(1).getCell(1).setText("名称");

            var pictureParagraph = document.createParagraph();
            pictureParagraph.createRun().addPicture(
                    new ByteArrayInputStream(ONE_PIXEL_PNG),
                    org.apache.poi.xwpf.usermodel.Document.PICTURE_TYPE_PNG,
                    "diagram.png",
                    Units.toEMU(1),
                    Units.toEMU(1));

            document.createParagraph().setPageBreak(true);
            document.write(output);
            docx = output.toByteArray();
        }

        var converted = RequirementDocumentConverter.convert(
                "设计.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx, "设计.assets");

        assertThat(converted.markdown())
                .contains("## 接口设计")
                .contains("# 自定义大纲标题")
                .contains("**粗体**", "*斜体*", "~~删除~~")
                .contains("[接口规范](https://example.internal/spec)")
                .contains("不安全链接")
                .doesNotContain("javascript:")
                .contains("第一行  \n第二行")
                .contains("  1. 嵌套步骤")
                .contains("- 无序检查项")
                .contains("| 字段 | 说明 |", "| --- | --- |", "| name | 名称 |")
                .contains("![diagram.png](设计.assets/image-001.png)")
                .contains("---");
        assertThat(converted.attachments()).singleElement().satisfies(attachment -> {
            assertThat(attachment.relativePath()).isEqualTo("设计.assets/image-001.png");
            assertThat(attachment.content()).isEqualTo(ONE_PIXEL_PNG);
        });
    }
}
