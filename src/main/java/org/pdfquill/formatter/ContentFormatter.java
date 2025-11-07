package org.pdfquill.formatter;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.pdfquill.exceptions.BarcodeGenerationException;
import org.pdfquill.barcode.BarcodeType;
import org.pdfquill.barcode.BarcodeUtils;
import org.pdfquill.settings.font.FontUtils;
import org.pdfquill.writer.SplitParts;
import org.pdfquill.writer.Text;
import org.pdfquill.writer.TextBuilder;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Processes and formats content, like text and barcodes, preparing it for rendering.
 */
public class ContentFormatter {

    public static List<Text> createTextsFromSource(Text text, List<String> lines) throws IOException {
        List<Text> textList = new ArrayList<>();
        for (String line : lines) {
            Text tempText = new Text(line, text.getFontSetting());
            textList.add(tempText);
        }

        return textList;
    }

    /**
     * Generates a barcode (or QR Code) image.
     *
     * @param code        payload to encode
     * @param barcodeType barcode symbology
     * @param height      desired barcode height in pixels (ZXing rendering space)
     * @param width       desired barcode width in pixels (ZXing rendering space)
     * @return A {@link BufferedImage} containing the barcode.
     * @throws BarcodeGenerationException when barcode generation fails
     */
    public static BufferedImage createBarcodeImage(String code, BarcodeType barcodeType, int height, int width) throws BarcodeGenerationException {
        try {
            if (height == 0) height = 350;
            if (width == 0) width = 350;

            Map<EncodeHintType, Object> hintMap = new EnumMap<>(EncodeHintType.class);
            hintMap.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hintMap.put(EncodeHintType.MARGIN, 0);
            hintMap.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.L);

            MultiFormatWriter writer = new MultiFormatWriter();
            BarcodeFormat barcodeFormat = BarcodeUtils.getBarcodeFormat(barcodeType);
            BitMatrix byteMatrix = writer.encode(code, barcodeFormat, width, height, hintMap);

            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            createGraphics(image, byteMatrix, width, height);
            return image;
        } catch (Throwable e) {
            throw new BarcodeGenerationException("Failed to create barcode image", e);
        }
    }

    private static void createGraphics(BufferedImage image, BitMatrix byteMatrix, int width, int height) {
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLACK);

        for (int i = 0; i < width; i++) {
            for (int j = 0; j < height; j++) {
                if (byteMatrix.get(i, j)) {
                    graphics.fillRect(i, j, 1, 1);
                }
            }
        }
        graphics.dispose();
    }

    private static float getTextWidth(String text, PDType1Font font, int fontSize) throws IOException {
        return FontUtils.getTextWidth(text, font, fontSize);
    }

    public static List<Text> formatTextBuilder(TextBuilder textBuilder, float maxWidth) throws IOException {
        List<Text> textList = textBuilder.getTextList();
        List<Text> resultTextList = new ArrayList<>();

        for (Text text : textList) {
            List<String> lines = formatTextToLines(text.getText(), text.getFontSetting().getSelectedFont(),
                    text.getFontSetting().getFontSize(), maxWidth, false);
            if (lines.size() > 1) {
                List<Text> newLines = createTextsFromSource(text, lines);
                resultTextList.addAll(newLines);
            } else  {
                resultTextList.add(text);
            }
        }

        return resultTextList;
    }

    /**
     * Wraps a block of text into multiple lines based on the current layout.
     *
     * @param text text to format
     * @return A list of strings, where each entry is a line.
     * @throws IOException if font metrics cannot be read
     */
    public static List<String> formatTextToLines(String text, PDType1Font font, int fontSize,
                                                 float maxWidth, boolean preserveSpaces) throws IOException {
        List<String> lines = new ArrayList<>();

        if (getTextWidth(text, font, fontSize) <= maxWidth) {
            lines.add(text);
            return lines;
        }

        final int n = text.length();
        float[] widths = new float[n];
        for (int i = 0; i < n; i++) {
            widths[i] = getTextWidth(text.substring(i, i + 1),  font, fontSize);
        }

        float[] prefix = new float[n + 1];
        for (int i = 1; i <= n; i++) {
            prefix[i] = prefix[i - 1] + widths[i - 1];
        }

        int start = 0;

        while (start < n) {
            if (!preserveSpaces)
                while (start < n && Character.isWhitespace(text.charAt(start))) start++;

            if (start >= n) break;

            int lo = start + 1, hi = n, best = start + 1;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                float w = prefix[mid] - prefix[start];
                if (w <= maxWidth) {
                    best = mid;
                    lo = mid + 1;
                } else {
                    hi = mid - 1;
                }
            }

            int end = best;

            int breakIdx = end;
            if (end < n && !Character.isWhitespace(text.charAt(end - 1)) && !Character.isWhitespace(text.charAt(end))) {
                int lastSpace = FontUtils.lastWhitespaceBetween(text, start, end - 1);
                if (lastSpace >= start + 1) {
                    breakIdx = lastSpace;
                }
            }

            if (breakIdx == start) breakIdx = end;

            lines.add(ContentFormatter.stripTrailingWhitespace(text.substring(start, breakIdx)));

            start = breakIdx;
            while (start < n && Character.isWhitespace(text.charAt(start))) start++;
        }

        return lines;
    }

    public static int findWrapIndex(String text, PDType1Font font, int fontSize, float maxWidth) throws IOException {
        if (text.isEmpty() || maxWidth <= 0) {
            return 0;
        }

        if (FontUtils.getTextWidth(text, font, fontSize) <= maxWidth) {
            return text.length();
        }

        float width = 0f;
        int lastFitting = 0;
        for (int i = 0; i < text.length(); i++) {
            float charWidth = FontUtils.getTextWidth(text.substring(i, i + 1), font, fontSize);
            if (width + charWidth > maxWidth) {
                break;
            }
            width += charWidth;
            lastFitting = i + 1;
        }

        if (lastFitting == 0) {
            return 0;
        }

        int breakIdx = lastFitting;
        int lastWhitespace = FontUtils.lastWhitespaceBetween(text, 0, lastFitting - 1);
        if (lastWhitespace >= 0 && lastWhitespace < lastFitting) {
            breakIdx = lastWhitespace + 1;
        }
        return breakIdx;
    }

    public static String stripLeadingWhitespace(String value) {
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) {
            index++;
        }
        return value.substring(index);
    }

    public static String stripTrailingWhitespace(String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    public static SplitParts splitText(Text text, float availableWidth) throws IOException {
        PDType1Font font = text.getFontSetting().getSelectedFont();
        int fontSize = text.getFontSetting().getFontSize();
        String content = text.getText();

        int breakIdx = ContentFormatter.findWrapIndex(content, font, fontSize, availableWidth);
        if (breakIdx <= 0) {
            breakIdx = Math.min(1, content.length());
        }

        if (breakIdx >= content.length()) {
            return new SplitParts(content, null);
        }

        String head = ContentFormatter.stripTrailingWhitespace(content.substring(0, breakIdx));
        String tail = ContentFormatter.stripLeadingWhitespace(content.substring(breakIdx));

        if (head.isEmpty()) {
            return new SplitParts(null, tail);
        }

        return new SplitParts(head, tail);
    }
}
