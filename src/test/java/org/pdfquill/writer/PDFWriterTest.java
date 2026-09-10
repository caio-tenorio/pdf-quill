package org.pdfquill.writer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.pdfquill.paper.PaperType;
import org.pdfquill.settings.PageLayout;
import org.pdfquill.settings.font.FontType;

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

    private static final class RecordingStripper extends PDFTextStripper {
        private final java.util.List<Float> yPositions = new java.util.ArrayList<>();

        private RecordingStripper() throws IOException {
            super();
        }

        @Override
        protected void writeString(String text, java.util.List<org.apache.pdfbox.text.TextPosition> textPositions) throws IOException {
            if (!text.trim().isEmpty() && !textPositions.isEmpty()) {
                yPositions.add(textPositions.get(0).getY());
            }
            super.writeString(text, textPositions);
        }

        java.util.List<Float> getYPositions() {
            return yPositions;
        }
    }
}
