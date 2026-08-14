package com.enterprise.testagent.workspace;

import com.enterprise.testagent.common.error.ErrorCode;
import com.enterprise.testagent.common.error.PlatformException;
import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.poi.hslf.usermodel.HSLFShape;
import org.apache.poi.hslf.usermodel.HSLFSlideShow;
import org.apache.poi.hslf.usermodel.HSLFTextShape;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 将受支持的企业文档内容转换为 UTF-8 Markdown。 */
final class RequirementDocumentConverter {

    private static final Logger LOGGER = LoggerFactory.getLogger(RequirementDocumentConverter.class);
    private static final Charset GB18030 = Charset.forName("GB18030");

    private RequirementDocumentConverter() {
    }

    /** 未知扩展名必须显式失败，禁止把任意二进制误写为 Markdown。 */
    static String toMarkdown(String fileName, byte[] content) {
        return convert(fileName, "application/octet-stream", content, "assets").markdown();
    }

    /**
     * TCDS 历史文档存在扩展名与真实 Word/PowerPoint 容器不一致的情况，转换时兼容两代 Office 容器。
     * 内容类型只用于拒绝登录页或 JSON 错误包络，不信任它来决定目标文件名。
     */
    static String toMarkdown(String fileName, String contentType, byte[] content) {
        return convert(fileName, contentType, content, "assets").markdown();
    }

    /**
     * 转换文档并返回 Markdown 与相对附件；附件目录由已规范化的目标 Markdown 文件名派生，不能来自 TCDS 输入。
     */
    static ConvertedDocument convert(
            String fileName,
            String contentType,
            byte[] content,
            String attachmentDirectory) {
        String extension = extension(fileName);
        String mediaType = mediaType(contentType);
        rejectErrorEnvelope(extension, mediaType, content);
        try {
            return switch (extension) {
                case "md", "markdown", "txt" -> converted(decodeText(content));
                case "doc", "docx" -> word(content, mediaType, attachmentDirectory);
                case "xls", "xlsx" -> converted(workbook(content));
                case "ppt", "pptx" -> converted(powerpoint(content));
                default -> throw new PlatformException(
                        ErrorCode.VALIDATION_ERROR,
                        "不支持的 TCDS 文档格式",
                        java.util.Map.of("extension", extension.isBlank() ? "unknown" : extension));
            };
        } catch (PlatformException exception) {
            throw exception;
        } catch (Exception exception) {
            // 只记录格式、媒体类型和异常类别，禁止记录文件名、URL、签名参数或正文。
            LOGGER.warn(
                    "TCDS document conversion failed: extension={}, contentType={}, cause={}",
                    extension.isBlank() ? "unknown" : extension,
                    mediaType,
                    exception.getClass().getSimpleName());
            throw new PlatformException(
                    ErrorCode.VALIDATION_ERROR,
                    "TCDS 文档内容与文件格式不匹配",
                    Map.of(
                            "extension", extension.isBlank() ? "unknown" : extension,
                            "contentType", mediaType));
        }
    }

    private static ConvertedDocument word(byte[] content, String mediaType, String attachmentDirectory) throws Exception {
        Exception openXmlFailure;
        try {
            return wordOpenXml(content, attachmentDirectory);
        } catch (Exception exception) {
            openXmlFailure = exception;
        }
        try {
            return word97(content, attachmentDirectory);
        } catch (Exception legacyFailure) {
            if (isTextMediaType(mediaType) || looksLikeText(content)) return converted(decodeText(content));
            legacyFailure.addSuppressed(openXmlFailure);
            throw legacyFailure;
        }
    }

    private static String powerpoint(byte[] content) throws Exception {
        Exception openXmlFailure;
        try {
            return powerpointOpenXml(content);
        } catch (Exception exception) {
            openXmlFailure = exception;
        }
        try {
            return powerpoint97(content);
        } catch (Exception legacyFailure) {
            legacyFailure.addSuppressed(openXmlFailure);
            throw legacyFailure;
        }
    }

    private static ConvertedDocument word97(byte[] content, String attachmentDirectory) throws Exception {
        try (HWPFDocument document = new HWPFDocument(new ByteArrayInputStream(content))) {
            return converted(WordToMarkdownRenderer.render(document, attachmentDirectory));
        }
    }

    private static ConvertedDocument wordOpenXml(byte[] content, String attachmentDirectory) throws Exception {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content))) {
            return converted(WordToMarkdownRenderer.render(document, attachmentDirectory));
        }
    }

    private static ConvertedDocument converted(String markdown) {
        return new ConvertedDocument(markdown, List.of());
    }

    private static ConvertedDocument converted(WordToMarkdownRenderer.RenderedWord rendered) {
        return new ConvertedDocument(
                rendered.markdown(),
                rendered.attachments().stream()
                        .map(attachment -> new Attachment(attachment.relativePath(), attachment.content()))
                        .toList());
    }

    private static String workbook(byte[] content) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            DataFormatter formatter = new DataFormatter(Locale.CHINA);
            StringBuilder markdown = new StringBuilder();
            for (Sheet sheet : workbook) {
                markdown.append("## ").append(sheet.getSheetName()).append("\n\n");
                for (Row row : sheet) {
                    List<String> cells = new ArrayList<>();
                    int lastCell = Math.max(0, row.getLastCellNum());
                    for (int index = 0; index < lastCell; index++) {
                        Cell cell = row.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                        cells.add(escapeCell(formatter.formatCellValue(cell)));
                    }
                    if (!cells.isEmpty()) appendLine(markdown, "| " + String.join(" | ", cells) + " |");
                }
                markdown.append('\n');
            }
            return normalize(markdown.toString());
        }
    }

    private static String powerpoint97(byte[] content) throws Exception {
        try (HSLFSlideShow slideShow = new HSLFSlideShow(new ByteArrayInputStream(content))) {
            StringBuilder markdown = new StringBuilder();
            for (int index = 0; index < slideShow.getSlides().size(); index++) {
                markdown.append("## 幻灯片 ").append(index + 1).append("\n\n");
                for (HSLFShape shape : slideShow.getSlides().get(index).getShapes()) {
                    if (shape instanceof HSLFTextShape textShape) appendLine(markdown, textShape.getText());
                }
                markdown.append('\n');
            }
            return normalize(markdown.toString());
        }
    }

    private static String powerpointOpenXml(byte[] content) throws Exception {
        try (XMLSlideShow slideShow = new XMLSlideShow(new ByteArrayInputStream(content))) {
            StringBuilder markdown = new StringBuilder();
            for (int index = 0; index < slideShow.getSlides().size(); index++) {
                markdown.append("## 幻灯片 ").append(index + 1).append("\n\n");
                for (XSLFShape shape : slideShow.getSlides().get(index).getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) appendLine(markdown, textShape.getText());
                }
                markdown.append('\n');
            }
            return normalize(markdown.toString());
        }
    }

    private static String decodeText(byte[] content) throws CharacterCodingException {
        byte[] normalized = content == null ? new byte[0] : content;
        if (normalized.length >= 3
                && normalized[0] == (byte) 0xEF
                && normalized[1] == (byte) 0xBB
                && normalized[2] == (byte) 0xBF) {
            return normalize(new String(normalized, 3, normalized.length - 3, StandardCharsets.UTF_8));
        }
        try {
            return normalize(StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(normalized))
                    .toString());
        } catch (CharacterCodingException exception) {
            return normalize(GB18030.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(normalized))
                    .toString());
        }
    }

    private static void rejectErrorEnvelope(String extension, String mediaType, byte[] content) {
        if (("doc".equals(extension) || "docx".equals(extension)
                || "xls".equals(extension) || "xlsx".equals(extension)
                || "ppt".equals(extension) || "pptx".equals(extension))
                && ("text/html".equals(mediaType)
                || "application/json".equals(mediaType)
                || "application/problem+json".equals(mediaType)
                || hasErrorEnvelopePrefix(content))) {
            throw new PlatformException(
                    ErrorCode.EXTERNAL_API_UNAVAILABLE,
                    "TCDS 文档下载内容无效",
                    Map.of("extension", extension, "contentType", mediaType));
        }
    }

    private static boolean hasErrorEnvelopePrefix(byte[] content) {
        byte[] normalized = content == null ? new byte[0] : content;
        int length = Math.min(normalized.length, 512);
        if (length == 0) return false;
        String prefix;
        try {
            prefix = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(normalized, 0, length))
                    .toString()
                    .stripLeading()
                    .toLowerCase(Locale.ROOT);
        } catch (CharacterCodingException exception) {
            return false;
        }
        return prefix.startsWith("<!doctype html")
                || prefix.startsWith("<html")
                || prefix.startsWith("{\"code\"")
                || prefix.startsWith("{\"error\"");
    }

    private static boolean looksLikeText(byte[] content) {
        byte[] normalized = content == null ? new byte[0] : content;
        if (normalized.length == 0) return true;
        try {
            String decoded = decodeText(normalized);
            long controls = decoded.codePoints()
                    .filter(codePoint -> Character.isISOControl(codePoint)
                            && codePoint != '\n' && codePoint != '\t')
                    .count();
            return controls * 100 <= Math.max(1, decoded.codePointCount(0, decoded.length()));
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    private static boolean isTextMediaType(String mediaType) {
        return mediaType.startsWith("text/") && !"text/html".equals(mediaType);
    }

    private static String mediaType(String raw) {
        if (raw == null || raw.isBlank()) return "application/octet-stream";
        String normalized = raw.split(";", 2)[0].trim().toLowerCase(Locale.ROOT);
        return normalized.matches("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+")
                ? normalized
                : "application/octet-stream";
    }

    private static void appendLine(StringBuilder target, String text) {
        if (text == null || text.isBlank()) {
            target.append('\n');
            return;
        }
        target.append(text.strip()).append("\n\n");
    }

    private static String escapeCell(String value) {
        return value == null ? "" : value.replace("|", "\\|").replace("\r", " ").replace("\n", " ").trim();
    }

    private static String normalize(String value) {
        if (value == null) return "";
        return value.replace("\r\n", "\n").replace('\r', '\n').strip() + "\n";
    }

    private static String extension(String fileName) {
        if (fileName == null) return "";
        int dot = fileName.lastIndexOf('.');
        return dot < 0 || dot == fileName.length() - 1
                ? ""
                : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    /** 转换结果中的附件路径相对于目标 Markdown 所在目录。 */
    record ConvertedDocument(String markdown, List<Attachment> attachments) {
        ConvertedDocument {
            markdown = markdown == null ? "" : markdown;
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    /** 二进制附件内容防御性复制，避免调用方修改已校验内容。 */
    record Attachment(String relativePath, byte[] content) {
        Attachment {
            relativePath = relativePath == null ? "" : relativePath;
            content = content == null ? new byte[0] : content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }
}
