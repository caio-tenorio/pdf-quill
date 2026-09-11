package org.pdfquill;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.pdfquill.exceptions.PDFGenerationException;
import org.pdfquill.paper.PaperType;
import org.pdfquill.settings.font.FontSettings;
import org.pdfquill.settings.font.FontType;
import org.pdfquill.settings.font.FontUtils;
import org.pdfquill.settings.Alignment;
import org.pdfquill.settings.PageLayout;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class PDFQuillTest {

    @Test
    void getBase64PDFBytesIsIdempotentAfterClose() throws Exception {
        PDFQuill quill = new PDFQuill();
        quill.printLine("Sample line");

        String first = quill.getBase64PDFBytes();
        String second = quill.getBase64PDFBytes();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void getPDFFileCreatesReusableTempFile() throws Exception {
        PDFQuill quill = new PDFQuill();
        quill.printLine("File export");

        File pdfFile = quill.getPDFFile();
        assertThat(pdfFile)
                .exists()
                .hasExtension("pdf");

        byte[] fileBytes = Files.readAllBytes(pdfFile.toPath());
        String base64 = quill.getBase64PDFBytes();
        assertThat(fileBytes).isEqualTo(Base64.getDecoder().decode(base64));

        File secondCall = quill.getPDFFile();
        assertThat(secondCall).isEqualTo(pdfFile);
    }

    @Test
    void getPDFBytesReturnsCopyAndMatchesBase64() throws Exception {
        PDFQuill quill = new PDFQuill();
        quill.printLine("Bytes test");

        byte[] bytes = quill.getPDFBytes();
        byte[] original = bytes.clone();

        String base64 = quill.getBase64PDFBytes();
        assertThat(bytes).isEqualTo(Base64.getDecoder().decode(base64));

        if (bytes.length > 0) {
            bytes[0] ^= 0xFF;
        }

        byte[] secondCall = quill.getPDFBytes();
        assertThat(secondCall).isEqualTo(original);
    }

    @Test
    void writePDFWritesToProvidedPath() throws Exception {
        PDFQuill quill = new PDFQuill();
        quill.printLine("Custom path");

        Path directory = Files.createTempDirectory("pdf-quill-tests");
        Path destination = directory.resolve("custom-output.pdf");

        try {
            Path written = quill.writePDF(destination);
            assertThat(written).isEqualTo(destination);
            assertThat(Files.exists(destination)).isTrue();

            byte[] fileBytes = Files.readAllBytes(destination);
            assertThat(fileBytes).isEqualTo(quill.getPDFBytes());

            File cached = quill.getPDFFile();
            assertThat(cached.toPath()).isEqualTo(destination);
        } finally {
            Files.deleteIfExists(destination);
            Files.deleteIfExists(directory);
        }
    }

    @Test
    void builderRejectsNullPaperType() {
        PDFQuill.Builder builder = PDFQuill.builder();

        assertThatThrownBy(() -> builder.withPaperType(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("paperType");
    }

    @Test
    void builderRejectsNegativeMargins() {
        PDFQuill.Builder builder = PDFQuill.builder();

        assertThatThrownBy(() -> builder.withMargins(-1f, 1f, 1f, 1f))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("marginLeft");
    }

    @Test
    void updateFontSettingsAppliesNewDefaults() throws Exception {
        PDFQuill quill = new PDFQuill();
        FontSettings settings = new FontSettings();
        settings.setFontSize(16);
        settings.setDefaultFont(settings.getFontByFontType(FontType.DEFAULT));

        quill.updateFontSettings(settings);
        quill.printLine("Custom font size");

        String base64 = quill.getBase64PDFBytes();
        byte[] bytes = Base64.getDecoder().decode(base64);
        try (PDDocument document = PDDocument.load(bytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void printLineWithCustomBuilderWritesPdf() throws IOException, PDFGenerationException {
        FontSettings fontSettings = new FontSettings();
        fontSettings.setFontSize(10);

        PDFQuill quill = PDFQuill.builder()
                .withPaperType(PaperType.A5)
                .withFontSettings(fontSettings)
                .build();

        quill.printLine("Line one");
        quill.printLine("Line two", FontType.BOLD);

        String base64 = quill.getBase64PDFBytes();
        byte[] pdfBytes = Base64.getDecoder().decode(base64);

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void skipLinesViaFacadeProducesBlankSpace() throws Exception {
        PDFQuill quill = new PDFQuill();

        quill.printLine("Alpha");
        quill.skipLines(2);
        quill.printLine("Omega");

        String base64 = quill.getBase64PDFBytes();
        byte[] pdfBytes = Base64.getDecoder().decode(base64);

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            RecordingStripper stripper = new RecordingStripper();
            stripper.getText(document);
            assertThat(stripper.getYPositions()).hasSizeGreaterThanOrEqualTo(2);
            float alphaY = stripper.getYPositions().get(0);
            float omegaY = stripper.getYPositions().get(1);
            PageLayout expectedLayout = new PageLayout(PaperType.A4);
            assertThat(Math.abs(alphaY - omegaY)).isCloseTo(expectedLayout.getLineHeight() * 3, within(0.5f));
        }
    }

    @Test
    void printLineDefaultAlignmentIsLeftUnchanged() throws Exception {
        PDFQuill quill = new PDFQuill();
        PageLayout expectedLayout = new PageLayout(PaperType.A4);

        quill.printLine("Left by default");

        byte[] pdfBytes = Base64.getDecoder().decode(quill.getBase64PDFBytes());

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            RecordingStripper stripper = new RecordingStripper();
            stripper.getText(document);
            assertThat(stripper.getXPositions()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(stripper.getXPositions().get(0)).isCloseTo(expectedLayout.getStartX(), within(0.5f));
        }
    }

    @Test
    void printLineWithExplicitRightAlignmentOverride() throws Exception {
        PDFQuill quill = new PDFQuill();
        PageLayout expectedLayout = new PageLayout(PaperType.A4);
        String text = "Right override";

        quill.printLine(text, Alignment.RIGHT);

        byte[] pdfBytes = Base64.getDecoder().decode(quill.getBase64PDFBytes());
        float textWidth = FontUtils.getTextWidth(text, expectedLayout.getFontSettings().getFontByFontType(FontType.DEFAULT),
                expectedLayout.getFontSettings().getFontSize());
        float expectedX = expectedLayout.getStartX() + expectedLayout.getMaxLineWidth() - textWidth;

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            RecordingStripper stripper = new RecordingStripper();
            stripper.getText(document);
            assertThat(stripper.getXPositions()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(stripper.getXPositions().get(0)).isCloseTo(expectedX, within(0.5f));
        }
    }

    @Test
    void printLineWithExplicitCenterAlignmentOverride() throws Exception {
        PDFQuill quill = new PDFQuill();
        PageLayout expectedLayout = new PageLayout(PaperType.A4);
        String text = "Center override";

        quill.printLine(text, Alignment.CENTER);

        byte[] pdfBytes = Base64.getDecoder().decode(quill.getBase64PDFBytes());
        float textWidth = FontUtils.getTextWidth(text, expectedLayout.getFontSettings().getFontByFontType(FontType.DEFAULT),
                expectedLayout.getFontSettings().getFontSize());
        float expectedX = expectedLayout.getStartX() + (expectedLayout.getMaxLineWidth() - textWidth) / 2f;

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            RecordingStripper stripper = new RecordingStripper();
            stripper.getText(document);
            assertThat(stripper.getXPositions()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(stripper.getXPositions().get(0)).isCloseTo(expectedX, within(0.5f));
        }
    }

    @Test
    void printLineWithFontTypeAndAlignmentOverrideUsesCorrectFontMetrics() throws Exception {
        PDFQuill quill = new PDFQuill();
        PageLayout expectedLayout = new PageLayout(PaperType.A4);
        String text = "Bold right override";

        quill.printLine(text, FontType.BOLD, Alignment.RIGHT);

        byte[] pdfBytes = Base64.getDecoder().decode(quill.getBase64PDFBytes());
        float textWidth = FontUtils.getTextWidth(text, expectedLayout.getFontSettings().getFontByFontType(FontType.BOLD),
                expectedLayout.getFontSettings().getFontSize());
        float expectedX = expectedLayout.getStartX() + expectedLayout.getMaxLineWidth() - textWidth;

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            RecordingStripper stripper = new RecordingStripper();
            stripper.getText(document);
            assertThat(stripper.getXPositions()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(stripper.getXPositions().get(0)).isCloseTo(expectedX, within(0.5f));
        }
    }

    @Test
    void builderWithAlignmentAppliesGlobalDefaultWithoutPerCallOverride() throws Exception {
        PageLayout expectedLayout = new PageLayout(PaperType.A4);
        String text = "Implicit center";
        PDFQuill quill = PDFQuill.builder()
                .withAlignment(Alignment.CENTER)
                .build();

        quill.printLine(text);

        byte[] pdfBytes = Base64.getDecoder().decode(quill.getBase64PDFBytes());
        float textWidth = FontUtils.getTextWidth(text, expectedLayout.getFontSettings().getFontByFontType(FontType.DEFAULT),
                expectedLayout.getFontSettings().getFontSize());
        float expectedX = expectedLayout.getStartX() + (expectedLayout.getMaxLineWidth() - textWidth) / 2f;

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            RecordingStripper stripper = new RecordingStripper();
            stripper.getText(document);
            assertThat(stripper.getXPositions()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(stripper.getXPositions().get(0)).isCloseTo(expectedX, within(0.5f));
        }
    }

    @Test
    void updateAlignmentChangesDefaultForSubsequentPrintLineCalls() throws Exception {
        PDFQuill quill = new PDFQuill();
        PageLayout expectedLayout = new PageLayout(PaperType.A4);
        String text = "Updated to right";

        quill.updateAlignment(Alignment.RIGHT);
        quill.printLine(text);

        byte[] pdfBytes = Base64.getDecoder().decode(quill.getBase64PDFBytes());
        float textWidth = FontUtils.getTextWidth(text, expectedLayout.getFontSettings().getFontByFontType(FontType.DEFAULT),
                expectedLayout.getFontSettings().getFontSize());
        float expectedX = expectedLayout.getStartX() + expectedLayout.getMaxLineWidth() - textWidth;

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            RecordingStripper stripper = new RecordingStripper();
            stripper.getText(document);
            assertThat(stripper.getXPositions()).hasSizeGreaterThanOrEqualTo(1);
            assertThat(stripper.getXPositions().get(0)).isCloseTo(expectedX, within(0.5f));
        }
    }

    @Test
    void builderRejectsNullAlignment() {
        PDFQuill.Builder builder = PDFQuill.builder();

        assertThatThrownBy(() -> builder.withAlignment(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("alignment");
    }

    @Test
    void printLineWrappedMultiLineTextAlignsEachLineIndependently() throws Exception {
        PDFQuill quill = PDFQuill.builder()
                .withAlignment(Alignment.RIGHT)
                .build();

        // Forces at least two wrapped lines of different lengths on A4 paper.
        String longText = "Alpha Bravo Charlie Delta Echo Foxtrot Golf Hotel India Juliett Kilo Lima Mike November";
        quill.printLine(longText);

        byte[] pdfBytes = Base64.getDecoder().decode(quill.getBase64PDFBytes());

        try (PDDocument document = PDDocument.load(pdfBytes)) {
            RecordingStripper stripper = new RecordingStripper();
            stripper.getText(document);
            assertThat(stripper.getXPositions()).hasSizeGreaterThanOrEqualTo(2);
            assertThat(stripper.getLineTexts()).hasSameSizeAs(stripper.getXPositions());

            PageLayout expectedLayout = new PageLayout(PaperType.A4);
            float expectedRightEdge = expectedLayout.getStartX() + expectedLayout.getMaxLineWidth();

            java.util.Set<Float> distinctX = new java.util.HashSet<>();
            for (int i = 0; i < stripper.getLineTexts().size(); i++) {
                String line = stripper.getLineTexts().get(i);
                float x = stripper.getXPositions().get(i);
                distinctX.add(x);
                float textWidth = FontUtils.getTextWidth(line, expectedLayout.getFontSettings().getFontByFontType(FontType.DEFAULT),
                        expectedLayout.getFontSettings().getFontSize());
                // Every wrapped line, regardless of its own length, must independently
                // land on the same right edge under RIGHT alignment.
                assertThat(x + textWidth).isCloseTo(expectedRightEdge, within(1.5f));
            }
            // Lines have different lengths, so a correct per-line computation produces
            // different X starting points (a single block-level shift would not).
            assertThat(distinctX.size()).isGreaterThanOrEqualTo(2);
        }
    }

    private static final class RecordingStripper extends PDFTextStripper {
        private final java.util.List<Float> yPositions = new java.util.ArrayList<>();
        private final java.util.List<Float> xPositions = new java.util.ArrayList<>();
        private final java.util.List<String> lineTexts = new java.util.ArrayList<>();

        private RecordingStripper() throws IOException {
            super();
        }

        @Override
        protected void writeString(String text, java.util.List<org.apache.pdfbox.text.TextPosition> textPositions) throws IOException {
            if (!text.trim().isEmpty() && !textPositions.isEmpty()) {
                yPositions.add(textPositions.get(0).getY());
                xPositions.add(textPositions.get(0).getX());
                lineTexts.add(text.trim());
            }
            super.writeString(text, textPositions);
        }

        java.util.List<Float> getYPositions() {
            return yPositions;
        }

        java.util.List<Float> getXPositions() {
            return xPositions;
        }

        java.util.List<String> getLineTexts() {
            return lineTexts;
        }
    }
}
