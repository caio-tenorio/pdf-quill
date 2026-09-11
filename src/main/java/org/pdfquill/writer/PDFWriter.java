package org.pdfquill.writer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.PDPageTree;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
import org.pdfquill.formatter.ContentFormatter;
import org.pdfquill.settings.font.FontUtils;
import org.pdfquill.settings.font.FontType;
import org.pdfquill.settings.Alignment;
import org.pdfquill.settings.AlignmentUtils;
import org.pdfquill.settings.PageLayout;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Manages the PDF document lifecycle, providing a cursor-like interface for writing content.
 * It handles page creation, content streams, and final document processing.
 */
public class PDFWriter {
    private final PDDocument document;
    private final ByteArrayOutputStream os;
    private final PDRectangle pageSize;
    private final PageLayout pageLayout;

    private PDPage currentPage;
    private PDPageContentStream contentStream;
    private final TextCursor textCursor;
    private final List<Float> pageWrittenHeights;

    /**
     * Creates a writer responsible for generating a PDF according to the supplied layout.
     *
     * @param pageLayout layout describing page dimensions and metrics
     */
    public PDFWriter(PageLayout pageLayout) {
        this.pageLayout = pageLayout;
        this.os = new ByteArrayOutputStream();
        this.document = new PDDocument();
        this.pageSize = new PDRectangle(pageLayout.getPageWidth(), pageLayout.getPageHeight());
        this.currentPage = null;
        this.contentStream = null;
        this.textCursor = new TextCursor();
        this.pageWrittenHeights = new ArrayList<>();
    }

    private void incrementWrittenHeight() {
        this.textCursor.advance(this.pageLayout.getLineHeight());
    }

    private void incrementWrittenHeight(float height) {
        this.textCursor.advance(height);
    }

    private float getCurrentY() {
        return this.textCursor.getCurrentY();
    }

    private void ensurePage() throws IOException {
        if (document.getNumberOfPages() == 0) {
            addNewPage();
        }
    }

    /**
     * Writes a single line of text to the document, automatically handling pagination.
     * Uses the layout's configured default alignment.
     *
     * @param line The text line to be written.
     * @throws IOException if writing to the content stream fails.
     */
    public void writeLine(String line, FontType fontType) throws IOException {
        writeLine(line, fontType, this.pageLayout.getAlignment());
    }

    /**
     * Writes a single line of text to the document, automatically handling pagination.
     *
     * @param line      The text line to be written.
     * @param fontType  font variant to render the line with.
     * @param alignment alignment to apply to this line; falls back to the layout's default when {@code null}.
     * @throws IOException if writing to the content stream fails.
     */
    public void writeLine(String line, FontType fontType, Alignment alignment) throws IOException {
        incrementWrittenHeight();
        addNewPageIfNeeded();
        float lineY = getCurrentY();
        float lineX = computeLineX(line, fontType, alignment);
        addTextLine(line, lineX, lineY, fontType);
    }

    private float computeLineX(String line, FontType fontType, Alignment alignment) throws IOException {
        Alignment effective = alignment != null ? alignment : this.pageLayout.getAlignment();
        if (effective == Alignment.LEFT || line == null || line.isEmpty()) {
            return this.pageLayout.getStartX();
        }

        PDType1Font font = this.pageLayout.getFontSettings().getFontByFontType(fontType);
        int fontSize = this.pageLayout.getFontSettings().getFontSize();
        float textWidth = FontUtils.getTextWidth(line, font, fontSize);

        return AlignmentUtils.resolveX(effective, this.pageLayout.getStartX(), this.pageLayout.getMaxLineWidth(), textWidth);
    }

    /**
     * Writes an image to the document, centering it and handling pagination.
     *
     * @param image       The image to write.
     * @param imageWidth  The desired width of the image in points.
     * @param imageHeight The desired height of the image in points.
     * @throws IOException if writing to the content stream fails.
     */
    public void writeImage(BufferedImage image, float imageWidth, float imageHeight) throws IOException {
        PDImageXObject pdImage = LosslessFactory.createFromImage(this.document, image);
        addNewPageIfNeeded(imageHeight);
        incrementWrittenHeight();
        float lineY = (getCurrentY()) - imageHeight;
        float imageStartX = this.pageLayout.getStartX() + (this.pageLayout.getMaxLineWidth() - imageWidth) / 2;

        this.textCursor.closeTextObject();
        contentStream.drawImage(pdImage, imageStartX, lineY, imageWidth, imageHeight);
        incrementWrittenHeight(imageHeight);
    }

    public void writeImage(ByteArrayInputStream imgBytes, int width, int height) throws IOException {
        BufferedImage image = ImageIO.read(imgBytes);
        writeImage(image, width, height);
    }

    /**
     * Emits a visual indicator representing the cut mark typically used for receipts.
     *
     * @throws IOException when drawing the signal fails
     */
    public void writeCutSignal() throws IOException {
        float lineY = getCurrentY();

        this.textCursor.showTextAt(createFullWidthString(" "), this.pageLayout.getStartX(),
                lineY - this.pageLayout.getLineHeight() * 2,
                this.pageLayout.getFontSettings().getDefaultFont(), this.pageLayout.getFontSettings().getFontSize());

        incrementWrittenHeight((this.pageLayout.getLineHeight() * 2) + this.pageLayout.getLineHeight());

        if (this.pageLayout.isNonThermalPaper()) {
            addNewPage();
        }
    }

    private String createFullWidthString(String text) throws IOException {
        float textWidth = this.pageLayout.getFontSettings().getDefaultFont().getStringWidth(text) * this.pageLayout.getFontSettings().getFontSize() / 1000f;
        int repetitions = (int) Math.ceil(this.pageLayout.getMaxLineWidth() / textWidth);
        StringBuilder sb = new StringBuilder(text.length() * repetitions);
        for (int i = 0; i < repetitions; i++) {
            sb.append(text);
        }
        return sb.toString();
    }

    private void addTextLine(String text, float x, float y, FontType fontType) throws IOException {
        addTextLine(text, x, y, this.pageLayout.getFontSettings().getFontByFontType(fontType),
                this.pageLayout.getFontSettings().getFontSize());
    }

    private void addTextLine(String text, float x, float y, PDType1Font font, int fontSize) throws IOException {
        try {
            this.textCursor.showTextAt(text, x, y, font, fontSize);
        } catch (IllegalArgumentException e) {
            this.textCursor.closeTextObject();
        }
    }

    //TODO: This method is using addTextLine which opens and closes text everytime, that's not right
    //TODO: change to open only once and update using newLineAtOffset passing Y as 0 if we need to stay at the same line
    public void writeFromTextLines(TextBuilder textBuilder) throws IOException {
        if (textBuilder == null || textBuilder.getTextList().isEmpty()) {
            return;
        }

        Deque<Text> pendingTexts = new ArrayDeque<>(textBuilder.getTextList());
        List<TextLinePlan> lines = new ArrayList<>();
        LineAccumulator currentLine = new LineAccumulator(this.pageLayout.getStartX());
        float maxLineWidth = this.pageLayout.getMaxLineWidth();

        while (!pendingTexts.isEmpty()) {
            Text current = pendingTexts.pollFirst();
            if (current == null) {
                continue;
            }

            String rawText = current.getText();
            if (rawText == null || rawText.isEmpty()) {
                continue;
            }

            PDType1Font font = current.getFontSetting().getSelectedFont();
            int fontSize = current.getFontSetting().getFontSize();
            float availableWidth = maxLineWidth - currentLine.getWidth();
            float textWidth = FontUtils.getTextWidth(rawText, font, fontSize);

            if (textWidth <= availableWidth) {
                Text chunk = new Text(rawText, current.getFontSetting());
                currentLine.addChunk(chunk, textWidth, fontSize);
                continue;
            }

            if (availableWidth <= 0 || (!currentLine.isEmpty() && FontUtils.getTextWidth(rawText.substring(0, 1), font, fontSize) > availableWidth)) {
                currentLine.flushInto(lines);
                pendingTexts.addFirst(current);
                continue;
            }

            SplitParts split = ContentFormatter.splitText(current, availableWidth);
            if (split.head() != null && !split.head().isEmpty()) {
                float headWidth = FontUtils.getTextWidth(split.head(), font, fontSize);
                Text chunk = new Text(split.head(), current.getFontSetting());
                currentLine.addChunk(chunk, headWidth, fontSize);
            }

            if (split.tail() == null || split.tail().isEmpty()) {
                continue;
            }

            currentLine.flushInto(lines);
            pendingTexts.addFirst(new Text(split.tail(), current.getFontSetting()));
        }

        currentLine.flushInto(lines);

        for (TextLinePlan textLinePlan : lines) {
            float lineHeight = textLinePlan.getMaxFontSize() * pageLayout.getLineSpacing();
            addNewPageIfNeeded(lineHeight);
            float y = getCurrentY() - lineHeight;
            for (Text line : textLinePlan.getTextList()) {
                addTextLine(line.getText(), line.getX(), y,
                        line.getFontSetting().getSelectedFont(), line.getFontSetting().getFontSize());
            }
            incrementWrittenHeight(lineHeight);
        }
    }

    public void skipLine() throws IOException {
        skipLines(1);
    }

    public void skipLines(int lineCount) throws IOException {
        if (lineCount <= 0) {
            return;
        }

        ensurePage();
        for (int i = 0; i < lineCount; i++) {
            incrementWrittenHeight();
            addNewPageIfNeeded();
        }
    }

    private boolean addNewPageIfNeeded() throws IOException {
        if (document.getNumberOfPages() == 0 || willNewContentExceedPageWritingHeight(this.pageLayout.getLineHeight())) {
            addNewPage();
            return true;
        }
        return false;
    }

    private boolean addNewPageIfNeeded(float height) throws IOException {
        if (document.getNumberOfPages() == 0 || willNewContentExceedPageWritingHeight(height)) {
            addNewPage();
            return true;
        }
        return false;
    }

    private boolean willNewContentExceedPageWritingHeight(float height) {
        return this.textCursor.getWrittenHeight() + height > this.pageLayout.getPageWritingHeight();
    }

    private void addNewPage() throws IOException {
        if (this.contentStream != null) {
            this.pageWrittenHeights.add(this.textCursor.getWrittenHeight());
            this.textCursor.closeTextObject();
            this.contentStream.close();
        }
        this.currentPage = new PDPage(this.pageSize);
        this.document.addPage(this.currentPage);
        this.contentStream = new PDPageContentStream(this.document, this.currentPage);
        this.textCursor.bindToContentStream(this.contentStream, this.pageLayout.getStartX(), this.pageLayout.getStartY());
    }

    public byte[] saveAndGetBytes() throws IOException {
        if (this.contentStream != null) {
            this.pageWrittenHeights.add(this.textCursor.getWrittenHeight());
            this.textCursor.closeTextObject();
            this.contentStream.close();
        }
        if (!isClosed()) {
            this.document.save(this.os);
            this.document.close();
            return cropPDF(this.os.toByteArray());
        }
        return this.os.toByteArray();
    }

    private byte[] cropPDF(byte[] bs) throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream(bs);
        PDDocument document = PDDocument.load(in);
        PDPageTree pages = document.getDocumentCatalog().getPages();
        float lineHeight = this.pageLayout.getLineHeight();

        int pageIndex = 0;
        for (PDPage page : pages) {
            if (this.pageLayout.isThermalPaper()) {
                float writtenHeight = pageIndex < this.pageWrittenHeights.size()
                        ? this.pageWrittenHeights.get(pageIndex)
                        : this.textCursor.getWrittenHeight();
                PDRectangle mediaBox = page.getMediaBox();
                PDRectangle cropBox = new PDRectangle(mediaBox.getLowerLeftX(), this.pageLayout.getPageHeight()
                        - writtenHeight - lineHeight,
                        mediaBox.getUpperRightX() - 3, writtenHeight + lineHeight);

                page.setCropBox(cropBox);
            } else {
                if (isPageBlank(document, page)) {
                    document.removePage(page);
                }
            }
            pageIndex++;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        document.close();

        return out.toByteArray();
    }

    private static boolean isPageBlank(PDDocument document, PDPage page) throws IOException {
        PDFTextStripper textStripper = new PDFTextStripper();
        textStripper.setStartPage(document.getPages().indexOf(page) + 1);
        textStripper.setEndPage(document.getPages().indexOf(page) + 1);

        String pageText = textStripper.getText(document).trim();

        return pageText.isEmpty();
    }

    public void close() throws IOException {
        if (this.contentStream != null) {
            this.textCursor.closeTextObject();
            this.contentStream.close();
        }
        if (!isClosed()) {
            this.document.close();
        }
    }

    public boolean isClosed() {
        return this.document.getDocument().isClosed();
    }
}
