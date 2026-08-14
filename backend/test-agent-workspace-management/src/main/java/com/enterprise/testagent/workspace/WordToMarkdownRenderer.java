package com.enterprise.testagent.workspace;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.model.PicturesTable;
import org.apache.poi.hwpf.model.StyleDescription;
import org.apache.poi.hwpf.usermodel.CharacterRun;
import org.apache.poi.hwpf.usermodel.Paragraph;
import org.apache.poi.hwpf.usermodel.Picture;
import org.apache.poi.hwpf.usermodel.Range;
import org.apache.poi.xwpf.usermodel.Borders;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.IRunElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHyperlink;
import org.apache.poi.xwpf.usermodel.XWPFHyperlinkRun;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFPicture;
import org.apache.poi.xwpf.usermodel.XWPFPictureData;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;

/**
 * 将 Word 的结构语义渲染为 Markdown，并把内嵌图片提取为相对附件。
 * DOCX 按正文元素顺序保留标题、Run 样式、列表、链接、表格与图片；旧 DOC 受格式模型限制尽力保留标题、列表和基础字符样式。
 */
final class WordToMarkdownRenderer {

    private static final Pattern HEADING_PATTERN = Pattern.compile(
            "(?:heading|标题)\\s*([1-6一二三四五六])", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final int LEGACY_BULLET_NUMBER_FORMAT = 23;
    private static final Charset GB18030 = Charset.forName("GB18030");
    private static final String LEGACY_MOJIBAKE_HINTS = "鏃増鏍囬绮椾綋銆鍒犻櫎楄〃锟鈥";

    private WordToMarkdownRenderer() {
    }

    /** 按 DOCX 正文元素原始顺序渲染，表格不会再被集中移动到文末。 */
    static RenderedWord render(XWPFDocument document, String attachmentDirectory) {
        return new DocxRenderer(document, requiredAttachmentDirectory(attachmentDirectory)).render();
    }

    /** 旧 DOC 不具备完整 OOXML 结构，按 HWPF 可读取的段落、字符样式、列表和图片做尽力转换。 */
    static RenderedWord render(HWPFDocument document, String attachmentDirectory) {
        return new DocRenderer(document, requiredAttachmentDirectory(attachmentDirectory)).render();
    }

    private static String requiredAttachmentDirectory(String value) {
        String normalized = value == null ? "" : value.trim().replace('\\', '/');
        if (normalized.isBlank()
                || normalized.startsWith("/")
                || normalized.endsWith("/")
                || normalized.contains("../")
                || normalized.contains("/..")) {
            throw new IllegalArgumentException("attachmentDirectory must be a safe relative path");
        }
        return normalized;
    }

    private static int headingLevel(String... candidates) {
        for (String candidate : candidates) {
            if (candidate == null || candidate.isBlank()) continue;
            Matcher matcher = HEADING_PATTERN.matcher(candidate);
            if (!matcher.find()) continue;
            return switch (matcher.group(1)) {
                case "一" -> 1;
                case "二" -> 2;
                case "三" -> 3;
                case "四" -> 4;
                case "五" -> 5;
                case "六" -> 6;
                default -> Integer.parseInt(matcher.group(1));
            };
        }
        return 0;
    }

    private static String formattedText(String raw, boolean bold, boolean italic, boolean strike) {
        String value = escapeMarkdownText(raw);
        if (value.isBlank()) return value;
        if (bold) value = "**" + value + "**";
        if (italic) value = "*" + value + "*";
        if (strike) value = "~~" + value + "~~";
        return value;
    }

    private static String escapeMarkdownText(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String normalized = raw.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
        StringBuilder escaped = new StringBuilder(normalized.length());
        for (int index = 0; index < normalized.length(); index++) {
            char current = normalized.charAt(index);
            if (current == '\n') {
                escaped.append("  \n");
            } else {
                if ("\\`*_{}[]<>|~".indexOf(current) >= 0) escaped.append('\\');
                escaped.append(current);
            }
        }
        return escaped.toString();
    }

    private static String safeMarkdownLink(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String candidate = raw.trim();
        if (candidate.startsWith("#")) return candidate.replace(" ", "%20").replace(")", "%29");
        try {
            URI uri = URI.create(candidate);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http")
                    || scheme.equalsIgnoreCase("https")
                    || scheme.equalsIgnoreCase("mailto"))) {
                return null;
            }
            return candidate.replace(" ", "%20").replace(")", "%29").replace("|", "%7C");
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static String normalizedMarkdown(String value) {
        String normalized = value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n');
        return normalized.strip() + "\n";
    }

    private static String safeExtension(String suggested) {
        String normalized = suggested == null ? "" : suggested.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return normalized.isBlank() || normalized.length() > 8 ? "bin" : normalized;
    }

    private static String safeAltText(String description, int index) {
        String value = description == null || description.isBlank() ? "图片 " + index : description.strip();
        return value.replace("\\", "\\\\").replace("[", "\\[").replace("]", "\\]")
                .replace("\r", " ").replace("\n", " ");
    }

    /** 渲染结果中的附件路径始终相对于 Markdown 文件所在目录。 */
    record RenderedWord(String markdown, List<Attachment> attachments) {
        RenderedWord {
            markdown = normalizedMarkdown(markdown);
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
        }
    }

    /** 附件内容做防御性复制，避免 Word 文档关闭后或调用方修改数组影响写盘。 */
    record Attachment(String relativePath, byte[] content) {
        Attachment {
            relativePath = Objects.requireNonNull(relativePath, "relativePath must not be null");
            content = content == null ? new byte[0] : content.clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }
    }

    private static final class DocxRenderer {

        private final XWPFDocument document;
        private final String attachmentDirectory;
        private final List<Attachment> attachments = new ArrayList<>();
        private int imageIndex;

        private DocxRenderer(XWPFDocument document, String attachmentDirectory) {
            this.document = Objects.requireNonNull(document, "document must not be null");
            this.attachmentDirectory = attachmentDirectory;
        }

        private RenderedWord render() {
            StringBuilder markdown = new StringBuilder();
            for (IBodyElement element : document.getBodyElements()) {
                switch (element.getElementType()) {
                    case PARAGRAPH -> appendParagraph(markdown, (XWPFParagraph) element);
                    case TABLE -> appendTable(markdown, (XWPFTable) element);
                    default -> {
                        // 内容控件没有稳定的跨版本结构接口，不能用不受控 XML 字符串代替正文。
                    }
                }
            }
            return new RenderedWord(markdown.toString(), attachments);
        }

        private void appendParagraph(StringBuilder markdown, XWPFParagraph paragraph) {
            String content = renderParagraphInline(paragraph).strip();
            boolean divider = paragraph.isPageBreak() || hasHorizontalBorder(paragraph);
            if (paragraph.isPageBreak()) markdown.append("---\n\n");

            int heading = paragraphHeadingLevel(paragraph);
            if (!content.isBlank()) {
                if (heading > 0) {
                    markdown.append("#".repeat(heading)).append(' ').append(content).append("\n\n");
                } else if (paragraph.getNumID() != null) {
                    int level = paragraph.getNumIlvl() == null ? 0 : paragraph.getNumIlvl().intValue();
                    markdown.append("  ".repeat(Math.max(0, Math.min(level, 8))))
                            .append(isOrderedList(paragraph) ? "1. " : "- ")
                            .append(content)
                            .append('\n');
                } else {
                    markdown.append(content).append("\n\n");
                }
            } else if (!divider) {
                markdown.append('\n');
            }
            if (!paragraph.isPageBreak() && hasHorizontalBorder(paragraph)) markdown.append("---\n\n");
        }

        private String paragraphStyleName(XWPFParagraph paragraph) {
            if (document.getStyles() == null || paragraph.getStyle() == null) return null;
            XWPFStyle style = document.getStyles().getStyle(paragraph.getStyle());
            return style == null ? null : style.getName();
        }

        /** 除样式名外同时识别 Word 大纲级别，兼容企业模板的自定义标题样式名。 */
        private int paragraphHeadingLevel(XWPFParagraph paragraph) {
            int styled = headingLevel(paragraph.getStyle(), paragraphStyleName(paragraph));
            if (styled > 0) return styled;
            if (paragraph.getCTP().getPPr() == null || !paragraph.getCTP().getPPr().isSetOutlineLvl()) return 0;
            int outline = paragraph.getCTP().getPPr().getOutlineLvl().getVal().intValue();
            return outline >= 0 && outline < 6 ? outline + 1 : 0;
        }

        private boolean isOrderedList(XWPFParagraph paragraph) {
            String format = paragraph.getNumFmt();
            return format == null || !"bullet".equalsIgnoreCase(format);
        }

        private boolean hasHorizontalBorder(XWPFParagraph paragraph) {
            Borders border = paragraph.getBorderBottom();
            return border != null && border != Borders.NONE && border != Borders.NIL;
        }

        private String renderParagraphInline(XWPFParagraph paragraph) {
            StringBuilder inline = new StringBuilder();
            for (IRunElement element : paragraph.getIRuns()) {
                if (!(element instanceof XWPFRun run) || run.isVanish()) continue;
                String text = formattedText(
                        run.text(),
                        run.isBold() || run.isComplexScriptBold(),
                        run.isItalic() || run.isComplexScriptItalic(),
                        run.isStrikeThrough() || run.isStrike() || run.isDoubleStrikeThrough());
                if (run instanceof XWPFHyperlinkRun hyperlinkRun) {
                    String link = hyperlinkUrl(hyperlinkRun);
                    if (link != null && !text.isBlank()) text = "[" + text + "](" + link + ")";
                }
                inline.append(text);
                for (XWPFPicture picture : run.getEmbeddedPictures()) {
                    inline.append(renderPicture(picture));
                }
            }
            return inline.toString();
        }

        private String hyperlinkUrl(XWPFHyperlinkRun run) {
            XWPFHyperlink hyperlink = run.getHyperlink(document);
            if (hyperlink != null) {
                String safe = safeMarkdownLink(hyperlink.getURL());
                if (safe != null) return safe;
            }
            return safeMarkdownLink(run.getAnchor() == null ? null : "#" + run.getAnchor());
        }

        private String renderPicture(XWPFPicture picture) {
            XWPFPictureData data = picture.getPictureData();
            if (data == null) return "";
            int index = ++imageIndex;
            String relativePath = attachmentDirectory + "/image-%03d.%s"
                    .formatted(index, safeExtension(data.suggestFileExtension()));
            attachments.add(new Attachment(relativePath, data.getData()));
            return "![" + safeAltText(picture.getDescription(), index) + "](" + relativePath + ")";
        }

        private void appendTable(StringBuilder markdown, XWPFTable table) {
            List<List<String>> rows = new ArrayList<>();
            int columns = 0;
            for (var row : table.getRows()) {
                List<String> values = row.getTableCells().stream().map(this::renderCell).toList();
                rows.add(values);
                columns = Math.max(columns, values.size());
            }
            if (rows.isEmpty() || columns == 0) return;
            appendTableRow(markdown, rows.getFirst(), columns);
            appendTableRow(markdown, java.util.Collections.nCopies(columns, "---"), columns);
            for (int index = 1; index < rows.size(); index++) appendTableRow(markdown, rows.get(index), columns);
            markdown.append('\n');
        }

        private String renderCell(XWPFTableCell cell) {
            List<String> parts = new ArrayList<>();
            for (IBodyElement element : cell.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    String value = renderParagraphInline(paragraph).strip();
                    if (paragraph.getNumID() != null && !value.isBlank()) {
                        value = (isOrderedList(paragraph) ? "1. " : "- ") + value;
                    }
                    if (!value.isBlank()) parts.add(value);
                } else if (element instanceof XWPFTable nested) {
                    String value = escapeMarkdownText(nested.getText()).strip();
                    if (!value.isBlank()) parts.add(value);
                }
            }
            return String.join("<br>", parts).replace("  \n", "<br>");
        }

        private void appendTableRow(StringBuilder markdown, List<String> values, int columns) {
            markdown.append('|');
            for (int index = 0; index < columns; index++) {
                String value = index < values.size() ? values.get(index) : "";
                markdown.append(' ').append(value.isBlank() ? " " : value).append(" |");
            }
            markdown.append('\n');
        }
    }

    private static final class DocRenderer {

        private final HWPFDocument document;
        private final String attachmentDirectory;
        private final PicturesTable pictures;
        private final List<Attachment> attachments = new ArrayList<>();
        private int imageIndex;

        private DocRenderer(HWPFDocument document, String attachmentDirectory) {
            this.document = Objects.requireNonNull(document, "document must not be null");
            this.attachmentDirectory = attachmentDirectory;
            this.pictures = document.getPicturesTable();
        }

        private RenderedWord render() {
            StringBuilder markdown = new StringBuilder();
            Range range = document.getRange();
            for (int index = 0; index < range.numParagraphs(); index++) {
                appendParagraph(markdown, range.getParagraph(index));
            }
            return new RenderedWord(markdown.toString(), attachments);
        }

        private void appendParagraph(StringBuilder markdown, Paragraph paragraph) {
            StringBuilder inline = new StringBuilder();
            boolean divider = paragraph.pageBreakBefore();
            for (int index = 0; index < paragraph.numCharacterRuns(); index++) {
                CharacterRun run = paragraph.getCharacterRun(index);
                if (pictures.hasHorizontalLine(run)) divider = true;
                if (pictures.hasPicture(run) || pictures.hasEscherPicture(run)) {
                    Picture picture = pictures.extractPicture(run, true);
                    if (picture != null) inline.append(renderPicture(picture));
                }
                String text = legacyText(run.text());
                if (!text.isBlank() && !run.isVanished()) {
                    inline.append(formattedText(
                            text,
                            run.isBold(),
                            run.isItalic(),
                            run.isStrikeThrough() || run.isDoubleStrikeThrough()));
                }
            }

            String content = inline.toString().strip();
            int heading = legacyHeadingLevel(paragraph);
            if (paragraph.pageBreakBefore()) markdown.append("---\n\n");
            if (!content.isBlank()) {
                if (heading > 0) {
                    markdown.append("#".repeat(heading)).append(' ').append(content).append("\n\n");
                } else if (paragraph.isInList()) {
                    int level = Math.max(0, Math.min(paragraph.getIlvl(), 8));
                    markdown.append("  ".repeat(level))
                            .append(isOrderedList(paragraph) ? "1. " : "- ")
                            .append(content)
                            .append('\n');
                } else if (startsWithLegacyBullet(content)) {
                    markdown.append("- ").append(content.substring(1).stripLeading()).append('\n');
                } else {
                    markdown.append(content).append("\n\n");
                }
            } else if (!divider) {
                markdown.append('\n');
            }
            if (!paragraph.pageBreakBefore() && divider) markdown.append("---\n\n");
        }

        private String legacyStyleName(Paragraph paragraph) {
            int index = paragraph.getStyleIndex();
            if (index < 0 || index >= document.getStyleSheet().numStyles()) return null;
            StyleDescription style = document.getStyleSheet().getStyleDescription(index);
            return style == null ? null : style.getName();
        }

        private int legacyHeadingLevel(Paragraph paragraph) {
            int styled = headingLevel(legacyStyleName(paragraph));
            if (styled > 0) return styled;
            int maximumHalfPoints = 0;
            boolean hasVisibleText = false;
            boolean allBold = true;
            for (int index = 0; index < paragraph.numCharacterRuns(); index++) {
                CharacterRun run = paragraph.getCharacterRun(index);
                if (legacyText(run.text()).isBlank()) continue;
                hasVisibleText = true;
                allBold &= run.isBold();
                maximumHalfPoints = Math.max(maximumHalfPoints, run.getFontSize());
            }
            if (!hasVisibleText || !allBold) return 0;
            if (maximumHalfPoints >= 44) return 1;
            if (maximumHalfPoints >= 36) return 2;
            if (maximumHalfPoints >= 30) return 3;
            return 0;
        }

        private boolean startsWithLegacyBullet(String content) {
            return content.startsWith("•")
                    || content.startsWith("·")
                    || content.startsWith("●")
                    || content.startsWith("○");
        }

        private boolean isOrderedList(Paragraph paragraph) {
            try {
                return paragraph.getList().getNumberFormat((char) Math.max(0, paragraph.getIlvl()))
                        != LEGACY_BULLET_NUMBER_FORMAT;
            } catch (RuntimeException exception) {
                return false;
            }
        }

        private String renderPicture(Picture picture) {
            int index = ++imageIndex;
            String relativePath = attachmentDirectory + "/image-%03d.%s"
                    .formatted(index, safeExtension(picture.suggestFileExtension()));
            attachments.add(new Attachment(relativePath, picture.getContent()));
            return "![" + safeAltText(picture.getDescription(), index) + "](" + relativePath + ")";
        }

        private String legacyText(String raw) {
            if (raw == null) return "";
            StringBuilder text = new StringBuilder();
            raw.codePoints().forEach(codePoint -> {
                if (codePoint == '\r' || codePoint == 7 || codePoint == 12) return;
                if (codePoint == 11) text.append('\n');
                else if (!Character.isISOControl(codePoint) || codePoint == '\n' || codePoint == '\t') {
                    text.appendCodePoint(codePoint);
                }
            });
            String cleaned = text.toString();
            if (!cleaned.isEmpty() && "•·●○".indexOf(cleaned.charAt(0)) >= 0) {
                return cleaned.substring(0, 1) + recoverLegacyUtf8Mojibake(cleaned.substring(1));
            }
            return recoverLegacyUtf8Mojibake(cleaned);
        }

        /** 某些旧 DOC 生产器把 UTF-8 字节按 GB18030 写入文本流；仅命中多项乱码特征且可严格反解时恢复。 */
        private String recoverLegacyUtf8Mojibake(String raw) {
            if (raw.isBlank()) return raw;
            long hints = raw.codePoints().filter(codePoint -> LEGACY_MOJIBAKE_HINTS.indexOf(codePoint) >= 0).count();
            if (hints < 2) return raw;
            try {
                String recovered = StandardCharsets.UTF_8.newDecoder()
                        .onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT)
                        .decode(ByteBuffer.wrap(raw.getBytes(GB18030)))
                        .toString();
                boolean containsCjk = recovered.codePoints().anyMatch(codePoint ->
                        Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN);
                return !recovered.equals(raw) && containsCjk ? recovered : raw;
            } catch (CharacterCodingException exception) {
                return raw;
            }
        }
    }
}
