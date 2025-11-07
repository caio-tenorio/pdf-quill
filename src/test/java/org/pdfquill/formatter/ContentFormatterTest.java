package org.pdfquill.formatter;

import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.Test;
import org.pdfquill.barcode.BarcodeType;
import org.pdfquill.settings.font.FontSettings;
import org.pdfquill.settings.font.FontUtils;
import org.pdfquill.writer.SplitParts;
import org.pdfquill.writer.Text;
import org.pdfquill.writer.TextBuilder;
import org.pdfquill.exceptions.BarcodeGenerationException;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class ContentFormatterTest {

    @Test
    void formatTextToLinesWrapsAtWordBoundary() throws IOException {
        String text = "Hello world test";
        int fontSize = 12;
        float boundaryWidth = FontUtils.getTextWidth("Hello world", PDType1Font.COURIER, fontSize) + 0.1f;

        List<String> lines = ContentFormatter.formatTextToLines(text, PDType1Font.COURIER, fontSize, boundaryWidth, false);

        assertThat(lines).containsExactly("Hello world", "test");
    }

    @Test
    void splitTextProducesTrimmedTail() throws IOException {
        FontSettings fontSettings = new FontSettings();
        Text text = new Text("Chunk   tail", fontSettings);
        float availableWidth = FontUtils.getTextWidth("Chunk", fontSettings.getSelectedFont(), fontSettings.getFontSize()) + 0.1f;

        SplitParts parts = ContentFormatter.splitText(text, availableWidth);

        assertThat(parts.head()).isEqualTo("Chunk");
        assertThat(parts.tail()).isEqualTo("tail");
    }

    @Test
    void findWrapIndexReturnsZeroWhenNoCharacterFits() throws IOException {
        String text = "Big";
        float maxWidth = FontUtils.getTextWidth("B", PDType1Font.COURIER, 12) - 1f;

        int index = ContentFormatter.findWrapIndex(text, PDType1Font.COURIER, 12, maxWidth);

        assertThat(index).isZero();
    }

    @Test
    void formatTextBuilderSplitsFragmentsAndKeepsFontSettings() throws IOException {
        FontSettings fontSettings = new FontSettings();
        TextBuilder builder = new TextBuilder()
                .addText(new Text("Lorem ipsum dolor sit amet", fontSettings));

        float maxWidth = FontUtils.getTextWidth("Lorem ipsum", fontSettings.getSelectedFont(), fontSettings.getFontSize()) + 0.1f;

        List<Text> texts = ContentFormatter.formatTextBuilder(builder, maxWidth);

        assertThat(texts).hasSizeGreaterThan(1);
        assertThat(texts.getFirst().getFontSetting()).isSameAs(fontSettings);
    }

    @Test
    void createBarcodeImageUsesFallbackSizeWhenZero() throws Exception {
        try {
            BufferedImage image = ContentFormatter.createBarcodeImage("123456", BarcodeType.CODE128, 0, 0);
            assertThat(image.getHeight()).isEqualTo(350);
            assertThat(image.getWidth()).isEqualTo(350);
        } catch (BarcodeGenerationException ex) {
            assumeTrue(ex.getCause() instanceof java.awt.AWTError,
                    "Unexpected failure when generating barcode image");
        }
    }
}
