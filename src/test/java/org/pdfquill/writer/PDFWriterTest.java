package org.pdfquill.writer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.pdfquill.paper.PaperType;
import org.pdfquill.settings.Alignment;
import org.pdfquill.settings.PageLayout;
import org.pdfquill.settings.font.FontType;
import org.pdfquill.settings.font.FontUtils;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PDFWriterTest {

    @Test
    void writeLineAddsNewPageWhenContentExceedsAvailableHeight() throws Exception {
        PageLayout layout = new PageLayout(PaperType.A4);
        PDFWriter writer = new PDFWriter(layout);

        int linesPerPage = (int) Math.floor(layout.getPageWritingHeight() / layout.getLineHeight());
        assertThat(linesPerPage).isGreaterThan(0);

        int totalLines = linesPerPage + 5;

        for (int i = 0; i < totalLines; i++) {
            writer.writeLine("Line " + i, FontType.DEFAULT);
        }

        byte[] pdfBytes = writer.saveAndGetBytes();

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
        }
    }

    @Test
    void skipLineInsertsBlankLineBetweenTextBlocks() throws Exception {
        PageLayout layout = new PageLayout(PaperType.A4);
        PDFWriter writer = new PDFWriter(layout);

        writer.writeLine("First", FontType.DEFAULT);
        writer.skipLine();
        writer.writeLine("Second", FontType.DEFAULT);

        byte[] pdfBytes = writer.saveAndGetBytes();

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            RecordingStripper stripper = new RecordingStripper();
            stripper.getText(document);
            assertThat(stripper.getYPositions()).hasSizeGreaterThanOrEqualTo(2);

            float firstY = stripper.getYPositions().get(0);
            float secondY = stripper.getYPositions().get(1);
            float actualDelta = Math.abs(firstY - secondY);
            assertThat(actualDelta).isCloseTo(layout.getLineHeight() * 2, within(0.5f));
        }
    }

    @Test
    void skipLinesCrossesToNextPageWhenNeeded() throws Exception {
        PageLayout layout = new PageLayout(PaperType.A4);
        PDFWriter writer = new PDFWriter(layout);

        int linesPerPage = (int) Math.floor(layout.getPageWritingHeight() / layout.getLineHeight());

        writer.writeLine("Top", FontType.DEFAULT);
        writer.skipLines(linesPerPage);
        writer.writeLine("Overflow", FontType.DEFAULT);

        byte[] pdfBytes = writer.saveAndGetBytes();

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            assertThat(stripper.getText(document)).contains("Top");

            stripper.setStartPage(2);
            stripper.setEndPage(2);
            assertThat(stripper.getText(document)).contains("Overflow");
        }
    }

    @Test
    void thermalPaperCropsEachPageAccordingToItsOwnContentHeight() throws Exception {
        PageLayout layout = new PageLayout(PaperType.THERMAL_80MM);
        PDFWriter writer = new PDFWriter(layout);

        int linesPerPage = (int) Math.floor(layout.getPageWritingHeight() / layout.getLineHeight());
        int fewLines = 3;

        for (int i = 0; i < linesPerPage; i++) {
            writer.writeLine("Line " + i, FontType.DEFAULT);
        }
        for (int i = 0; i < fewLines; i++) {
            writer.writeLine("Short " + i, FontType.DEFAULT);
        }

        byte[] pdfBytes = writer.saveAndGetBytes();

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(2);

            float firstPageHeight = document.getPage(0).getCropBox().getHeight();
            float secondPageHeight = document.getPage(1).getCropBox().getHeight();

            // The first page renders every one of the `linesPerPage` lines, plus the writer's
            // one-line bottom padding. The last page's own accounting always drops the height of
            // the single line that triggered the page break (its increment is attributed to the
            // page being closed, not the page it is actually drawn on), which happens to cancel
            // out exactly against that same one-line padding.
            float expectedFirstPageHeight = (linesPerPage + 1) * layout.getLineHeight();
            float expectedSecondPageHeight = fewLines * layout.getLineHeight();
            assertThat(firstPageHeight).isCloseTo(expectedFirstPageHeight, within(0.5f));
            assertThat(secondPageHeight).isCloseTo(expectedSecondPageHeight, within(0.5f));

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(1);
            String firstPageText = stripper.getText(document);
            assertThat(firstPageText).contains("Line 0", "Line " + (linesPerPage - 1));
            assertThat(firstPageText).doesNotContain("Short 0");

            stripper.setStartPage(2);
            stripper.setEndPage(2);
            String secondPageText = stripper.getText(document);
            assertThat(secondPageText).contains("Short 0", "Short " + (fewLines - 1));
            assertThat(secondPageText).doesNotContain("Line 0");
        }
    }

    @Test
    void writeLineDefaultAlignmentStaysAtStartX() throws Exception {
        PageLayout layout = new PageLayout(PaperType.A4);
        PDFWriter writer = new PDFWriter(layout);

        writer.writeLine("Left aligned", FontType.DEFAULT);

        byte[] pdfBytes = writer.saveAndGetBytes();

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            RecordingStripper stripper = new RecordingStripper();
            stripper.getText(document);
            assertThat(stripper.getXPositions()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(stripper.getXPositions().get(0)).isCloseTo(layout.getStartX(), within(0.5f));
        }
    }

    @Test
    void writeLineRightAlignmentAnchorsTextToRightEdge() throws Exception {
        PageLayout layout = new PageLayout(PaperType.A4);
        PDFWriter writer = new PDFWriter(layout);
        String text = "Right aligned";

        writer.writeLine(text, FontType.DEFAULT, Alignment.RIGHT);

        byte[] pdfBytes = writer.saveAndGetBytes();

        float textWidth = FontUtils.getTextWidth(text, layout.getFontSettings().getFontByFontType(FontType.DEFAULT),
                layout.getFontSettings().getFontSize());
        float expectedX = layout.getStartX() + layout.getMaxLineWidth() - textWidth;

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            RecordingStripper stripper = new RecordingStripper();
            stripper.getText(document);
            assertThat(stripper.getXPositions()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(stripper.getXPositions().get(0)).isCloseTo(expectedX, within(0.5f));
        }
    }

    @Test
    void writeLineCenterAlignmentCentersText() throws Exception {
        PageLayout layout = new PageLayout(PaperType.A4);
        PDFWriter writer = new PDFWriter(layout);
        String text = "Centered";

        writer.writeLine(text, FontType.DEFAULT, Alignment.CENTER);

        byte[] pdfBytes = writer.saveAndGetBytes();

        float textWidth = FontUtils.getTextWidth(text, layout.getFontSettings().getFontByFontType(FontType.DEFAULT),
                layout.getFontSettings().getFontSize());
        float expectedX = layout.getStartX() + (layout.getMaxLineWidth() - textWidth) / 2f;

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            RecordingStripper stripper = new RecordingStripper();
            stripper.getText(document);
            assertThat(stripper.getXPositions()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(stripper.getXPositions().get(0)).isCloseTo(expectedX, within(0.5f));
        }
    }

    @Test
    void writeLineNullAlignmentFallsBackToLayoutDefault() throws Exception {
        PageLayout layout = new PageLayout(PaperType.A4);
        layout.setAlignment(Alignment.CENTER);
        PDFWriter writer = new PDFWriter(layout);
        String text = "Falls back";

        writer.writeLine(text, FontType.DEFAULT, null);

        byte[] pdfBytes = writer.saveAndGetBytes();

        float textWidth = FontUtils.getTextWidth(text, layout.getFontSettings().getFontByFontType(FontType.DEFAULT),
                layout.getFontSettings().getFontSize());
        float expectedX = layout.getStartX() + (layout.getMaxLineWidth() - textWidth) / 2f;

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            RecordingStripper stripper = new RecordingStripper();
            stripper.getText(document);
            assertThat(stripper.getXPositions()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(stripper.getXPositions().get(0)).isCloseTo(expectedX, within(0.5f));
        }
    }

    private static final class RecordingStripper extends PDFTextStripper {
        private final java.util.List<Float> yPositions = new java.util.ArrayList<>();
        private final java.util.List<Float> xPositions = new java.util.ArrayList<>();

        private RecordingStripper() throws IOException {
            super();
        }

        @Override
        protected void writeString(String text, java.util.List<org.apache.pdfbox.text.TextPosition> textPositions) throws IOException {
            if (!text.trim().isEmpty() && !textPositions.isEmpty()) {
                yPositions.add(textPositions.get(0).getY());
                xPositions.add(textPositions.get(0).getX());
            }
            super.writeString(text, textPositions);
        }

        java.util.List<Float> getYPositions() {
            return yPositions;
        }

        java.util.List<Float> getXPositions() {
            return xPositions;
        }
    }
}
