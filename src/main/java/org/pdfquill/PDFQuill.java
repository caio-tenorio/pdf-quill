package org.pdfquill;

import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.pdfquill.barcode.BarcodeType;
import org.pdfquill.exceptions.BarcodeGenerationException;
import org.pdfquill.exceptions.PDFExportException;
import org.pdfquill.exceptions.PDFGenerationException;
import org.pdfquill.formatter.ContentFormatter;
import org.pdfquill.measurements.MeasurementUtils;
import org.pdfquill.paper.PaperType;
import org.pdfquill.settings.font.FontSettings;
import org.pdfquill.settings.font.FontType;
import org.pdfquill.settings.PageLayout;
import org.pdfquill.settings.permissions.PermissionSettings;
import org.pdfquill.writer.PDFWriter;
import org.pdfquill.writer.TextBuilder;

import javax.xml.bind.DatatypeConverter;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

/**
 * Facade responsible for producing print-ready PDFs using a fluent API.
 */
public class PDFQuill {
    private final PageLayout pageLayout;
    private final PermissionSettings permissionSettings;
    private final PDFWriter pdfWriter;
    private byte[] pdf;
    private File pdfFile;

    /**
     * Creates a printer with default settings (A4 paper, Courier font, default permissions).
     */
    public PDFQuill() {
        this(new Builder());
    }

    private PDFQuill(Builder builder) {
        PermissionSettings basePermissionSettings = builder.permissionSettings != null ? builder.permissionSettings : new PermissionSettings();
        this.permissionSettings = copyPermissionSettings(basePermissionSettings);

        PageLayout layout = builder.pageLayout != null ? new PageLayout(builder.pageLayout) : createDefaultPageLayout(builder.paperType);

        if (builder.paperType != null) {
            layout.setPaperType(builder.paperType);
        }
        if (builder.hasCustomMargins()) {
            float marginLeft = builder.marginLeft != null ? builder.marginLeft : layout.getMarginLeft();
            float marginRight = builder.marginRight != null ? builder.marginRight : layout.getMarginRight();
            float marginTop = builder.marginTop != null ? builder.marginTop : layout.getMarginTop();
            float marginBottom = builder.marginBottom != null ? builder.marginBottom : layout.getMarginBottom();
            layout.setMargins(marginLeft, marginRight, marginTop, marginBottom);
        }
        if (builder.fontSettings != null) {
            layout.setFontSettings(copyFontSettings(builder.fontSettings));
        }
        if (builder.fontSettingsCustomizer != null) {
            builder.fontSettingsCustomizer.accept(layout.getFontSettings());
            layout.recalculate();
        }

        this.pageLayout = layout;

        if (builder.permissionSettingsCustomizer != null) {
            builder.permissionSettingsCustomizer.accept(this.permissionSettings);
        }

        this.pdfWriter = new PDFWriter(this.pageLayout);
    }

    /**
     * @return a new builder for configuring {@link PDFQuill} instances
     */
    public static Builder builder() {
        return new Builder();
    }

    private static PageLayout createDefaultPageLayout(PaperType paperType) {
        PaperType resolvedPaperType = paperType != null ? paperType : PaperType.A4;
        return new PageLayout(resolvedPaperType);
    }

    /**
     * Replaces the active font settings on the underlying layout.
     *
     * @param fontSettings new font configuration to apply
     */
    public void updateFontSettings(FontSettings fontSettings) {
        this.pageLayout.setFontSettings(fontSettings);
    }

    /**
     * Finalises the document (if necessary) and returns the content encoded as Base64.
     *
     * @return Base64 encoded PDF bytes
     * @throws PDFGenerationException when writing the PDF fails
     */
    public String getBase64PDFBytes() throws PDFGenerationException {
        return DatatypeConverter.printBase64Binary(resolvePdfBytes());
    }

    /**
     * Returns the generated PDF as a byte array. The returned array is a copy and can be mutated safely.
     *
     * @return PDF bytes
     * @throws PDFGenerationException when writing the PDF fails
     */
    public byte[] getPDFBytes() throws PDFGenerationException {
        byte[] bytes = resolvePdfBytes();
        byte[] copy = new byte[bytes.length];
        System.arraycopy(bytes, 0, copy, 0, bytes.length);
        return copy;
    }

    /**
     * Returns the generated PDF written to a temporary {@link File}.
     *
     * @return file containing the PDF bytes; deleted on JVM exit
     * @throws PDFExportException when writing the PDF fails
     */
    public File getPDFFile() throws PDFExportException {
        if (this.pdfWriter.isClosed() && this.pdfFile != null && this.pdfFile.exists()) {
            return this.pdfFile;
        }

        try {
            File tempFile = File.createTempFile("pdf-quill-", ".pdf");
            tempFile.deleteOnExit();
            writePDF(tempFile.toPath());
            return tempFile;
        } catch (IOException e) {
            throw new PDFExportException("Failed to create PDF file", e);
        }
    }

    /**
     * Writes the generated PDF into the provided path.
     *
     * @param destination target path for the PDF file
     * @return the same path provided for convenience
     * @throws PDFExportException when writing the PDF fails
     */
    public Path writePDF(Path destination) throws PDFExportException {
        if (destination == null) {
            throw new IllegalArgumentException("destination cannot be null");
        }
        if (Files.exists(destination) && Files.isDirectory(destination)) {
            throw new IllegalArgumentException("destination must be a file path");
        }

        byte[] pdfBytes = resolvePdfBytes();
        try {
            Path parent = destination.getParent();
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.write(destination, pdfBytes);
            this.pdfFile = destination.toFile();
            return destination;
        } catch (IOException e) {
            throw new PDFExportException("Failed to write PDF to destination", e);
        }
    }

    /**
     * Explicitly finalises the document, equivalent to calling {@link #getBase64PDFBytes()}.
     *
     * @throws PDFGenerationException when saving fails
     */
    public void close() throws PDFGenerationException {
        resolvePdfBytes();
    }

    private byte[] resolvePdfBytes() throws PDFGenerationException {
        if (this.pdfWriter.isClosed()) {
            if (this.pdf == null) {
                throw new PDFGenerationException("PDF content is not available after closure");
            }
            return this.pdf;
        }

        try {
            this.pdf = this.pdfWriter.saveAndGetBytes();
        } catch (IOException e) {
            throw new PDFGenerationException("Failed to create PDF", e);
        }
        return this.pdf;
    }

    /**
     * Prints a text block, applying word wrapping and pagination automatically.
     *
     * @param text text to render
     * @return fluent reference to this instance
     * @throws PDFGenerationException when PDF operations fail
     */
    public PDFQuill printLine(String text) throws PDFGenerationException {
        try {
            printLines(text, FontType.DEFAULT);
        } catch (IOException e) {
            throw new PDFGenerationException("Failed to write text to the PDF", e);
        }
        return this;
    }

    /**
     * Inserts a single blank line, advancing the cursor vertically.
     *
     * @return fluent reference to this instance
     * @throws PDFGenerationException when PDF operations fail
     */
    public PDFQuill skipLine() throws PDFGenerationException {
        return skipLines(1);
    }

    /**
     * Inserts {@code lineCount} blank lines, advancing the cursor accordingly.
     *
     * @param lineCount number of lines to skip; values &lt;= 0 are ignored
     * @return fluent reference to this instance
     * @throws PDFGenerationException when PDF operations fail
     */
    public PDFQuill skipLines(int lineCount) throws PDFGenerationException {
        try {
            this.pdfWriter.skipLines(lineCount);
        } catch (IOException e) {
            throw new PDFGenerationException("Failed to skip lines in the PDF", e);
        }
        return this;
    }

    /**
     * Prints a text block, applying word wrapping and pagination automatically.
     *
     * @param text text to render
     * @param fontType type of the font, if its bold, italic, etc
     * @return fluent reference to this instance
     * @throws PDFGenerationException when PDF operations fail
     */
    public PDFQuill printLine(String text, FontType fontType) throws PDFGenerationException {
        try {
            printLines(text, fontType);
        } catch (IOException e) {
            throw new PDFGenerationException("Failed to write text to the PDF", e);
        }
        return this;
    }

    private void printLines(String text, FontType fontType) throws IOException {
        PDType1Font font = this.pageLayout.getFontSettings().getFontByFontType(fontType);
        int fontSize = this.pageLayout.getFontSettings().getFontSize();
        float maxWidth = this.pageLayout.getMaxLineWidth();

        List<String> lines = ContentFormatter.formatTextToLines(text, font, fontSize, maxWidth, false);
        for (String line : lines) {
            this.pdfWriter.writeLine(line, fontType);
        }
    }

    public void writeFromTextBuilder(TextBuilder textBuilder) throws IOException {
        if (textBuilder == null) {
            return;
        }

        this.pdfWriter.writeFromTextLines(textBuilder);
    }

    /**
     * Prints an image using default dimensions (100x100 points).
     *
     * @param imgBytes image stream
     * @return fluent reference to this instance
     * @throws IOException when the image cannot be read
     */
    public PDFQuill printImage(ByteArrayInputStream imgBytes) throws IOException {
        this.pdfWriter.writeImage(imgBytes, 100, 100);
        return this;
    }

    /**
     * Prints an image using the pixel dimensions of the supplied {@link BufferedImage}.
     *
     * @param image image to print
     * @return fluent reference to this instance
     * @throws IOException when the image cannot be encoded
     */
    public PDFQuill printImage(BufferedImage image) throws IOException {
        int width = image.getWidth();
        int height = image.getHeight();
        this.pdfWriter.writeImage(image, width, height);
        return this;
    }

    /**
     * Prints a barcode using default height and width.
     *
     * @param code        payload to encode
     * @param barcodeType symbology to render
     * @return fluent reference to this instance
     * @throws BarcodeGenerationException when barcode generation fails
     */
    public PDFQuill printBarcode(String code, BarcodeType barcodeType) throws BarcodeGenerationException {
        return this.printBarcode(code, barcodeType, 0, 0);
    }

    /**
     * Prints a barcode using explicit dimensions.
     *
     * @param code        payload to encode
     * @param barcodeType symbology to render
     * @param height      desired height in pixels (ZXing rendering space)
     * @param width       desired width in pixels (ZXing rendering space)
     * @return fluent reference to this instance
     * @throws BarcodeGenerationException when barcode generation fails
     */
    public PDFQuill printBarcode(String code, BarcodeType barcodeType, int height, int width) throws BarcodeGenerationException {
        try {
            BufferedImage image = ContentFormatter.createBarcodeImage(code, barcodeType, height, width);

            float imageHeight = BarcodeType.QRCODE.equals(barcodeType) ? MeasurementUtils.mmToPt(48f) : MeasurementUtils.mmToPt(12f);
            float imageWidth = BarcodeType.QRCODE.equals(barcodeType) ? MeasurementUtils.mmToPt(48f) : MeasurementUtils.mmToPt(80f);

            this.pdfWriter.writeImage(image, imageWidth, imageHeight);
        } catch (IOException e) {
            throw new BarcodeGenerationException("Failed to write barcode to the PDF", e);
        }
        return this;
    }

    /**
     * Prints a cut signal, typically used to indicate receipt boundaries.
     *
     * @return fluent reference to this instance
     * @throws PDFGenerationException when drawing fails
     */
    public PDFQuill cutSignal() throws PDFGenerationException {
        try {
            this.pdfWriter.writeCutSignal();
        } catch (IOException e) {
            throw new PDFGenerationException("Failed to create cut mark for the PDF", e);
        }
        return this;
    }

    private static PermissionSettings copyPermissionSettings(PermissionSettings source) {
        PermissionSettings copy = new PermissionSettings();
        copy.setCanPrint(source.isCanPrint());
        copy.setCanModify(source.isCanModify());
        copy.setCanExtractContent(source.isCanExtractContent());
        return copy;
    }

    private static FontSettings copyFontSettings(FontSettings source) {
        FontSettings copy = new FontSettings();
        copy.setFontSize(source.getFontSize());
        copy.setDefaultFont(source.getDefaultFont());
        copy.setBoldFont(source.getBoldFont());
        copy.setItalicFont(source.getItalicFont());
        copy.setBoldItalicFont(source.getBoldItalicFont());
        return copy;
    }

    /**
     * Builder for configuring {@link PDFQuill} instances.
     */
    public static final class Builder {
        private PaperType paperType;
        private boolean preserveSpaces;
        private PermissionSettings permissionSettings;
        private Consumer<PermissionSettings> permissionSettingsCustomizer;
        private PageLayout pageLayout;
        private FontSettings fontSettings;
        private Consumer<FontSettings> fontSettingsCustomizer;
        private Float marginLeft;
        private Float marginRight;
        private Float marginTop;
        private Float marginBottom;

        /**
         * Sets the paper type to be used by the generated document.
         *
         * @param paperType paper format; must not be {@code null}
         * @return this builder
         */
        public Builder withPaperType(PaperType paperType) {
            if (paperType == null) {
                throw new IllegalArgumentException("paperType cannot be null");
            }
            this.paperType = paperType;
            return this;
        }

        /**
         * Controls whether leading spaces should be preserved during word wrapping.
         *
         * @param preserveSpaces flag indicating whether to preserve spaces
         * @return this builder
         */
        public Builder preserveSpaces(boolean preserveSpaces) {
            this.preserveSpaces = preserveSpaces;
            return this;
        }

        /**
         * Supplies an explicit permission settings instance.
         *
         * @param permissionSettings permissions to copy into the final printer
         * @return this builder
         */
        public Builder withPermissionSettings(PermissionSettings permissionSettings) {
            this.permissionSettings = permissionSettings;
            return this;
        }

        /**
         * Allows in-place customisation of the permission settings before construction.
         *
         * @param permissionSettingsCustomizer callback receiving the mutable settings
         * @return this builder
         */
        public Builder configurePermissionSettings(Consumer<PermissionSettings> permissionSettingsCustomizer) {
            this.permissionSettingsCustomizer = permissionSettingsCustomizer;
            return this;
        }

        /**
         * Provides a pre-configured page layout to base this printer on.
         *
         * @param pageLayout layout instance to clone
         * @return this builder
         */
        public Builder withPageLayout(PageLayout pageLayout) {
            this.pageLayout = pageLayout;
            return this;
        }

        /**
         * Sets the default font configuration.
         *
         * @param fontSettings font settings to copy
         * @return this builder
         */
        public Builder withFontSettings(FontSettings fontSettings) {
            this.fontSettings = fontSettings;
            return this;
        }

        /**
         * Allows fine-grained font configuration without replacing the entire settings object.
         *
         * @param fontSettingsCustomizer callback receiving the mutable settings
         * @return this builder
         */
        public Builder configureFontSettings(Consumer<FontSettings> fontSettingsCustomizer) {
            this.fontSettingsCustomizer = fontSettingsCustomizer;
            return this;
        }

        /**
         * Sets all margins at once.
         *
         * @param marginLeft   left margin in points
         * @param marginRight  right margin in points
         * @param marginTop    top margin in points
         * @param marginBottom bottom margin in points
         * @return this builder
         */
        public Builder withMargins(float marginLeft, float marginRight, float marginTop, float marginBottom) {
            validateMargin(marginLeft, "marginLeft");
            validateMargin(marginRight, "marginRight");
            validateMargin(marginTop, "marginTop");
            validateMargin(marginBottom, "marginBottom");
            this.marginLeft = marginLeft;
            this.marginRight = marginRight;
            this.marginTop = marginTop;
            this.marginBottom = marginBottom;
            return this;
        }

        /**
         * Overrides the left margin.
         *
         * @param marginLeft left margin in points
         * @return this builder
         */
        public Builder withMarginLeft(float marginLeft) {
            validateMargin(marginLeft, "marginLeft");
            this.marginLeft = marginLeft;
            return this;
        }

        /**
         * Overrides the right margin.
         *
         * @param marginRight right margin in points
         * @return this builder
         */
        public Builder withMarginRight(float marginRight) {
            validateMargin(marginRight, "marginRight");
            this.marginRight = marginRight;
            return this;
        }

        /**
         * Overrides the top margin.
         *
         * @param marginTop top margin in points
         * @return this builder
         */
        public Builder withMarginTop(float marginTop) {
            validateMargin(marginTop, "marginTop");
            this.marginTop = marginTop;
            return this;
        }

        /**
         * Overrides the bottom margin.
         *
         * @param marginBottom bottom margin in points
         * @return this builder
         */
        public Builder withMarginBottom(float marginBottom) {
            validateMargin(marginBottom, "marginBottom");
            this.marginBottom = marginBottom;
            return this;
        }

        boolean hasCustomMargins() {
            return marginLeft != null || marginRight != null || marginTop != null || marginBottom != null;
        }

        private static void validateMargin(float value, String field) {
            if (value < 0) {
                throw new IllegalArgumentException(field + " cannot be negative");
            }
        }

        /**
         * Builds a new {@link PDFQuill} instance based on the supplied configuration.
         *
         * @return configured printer
         */
        public PDFQuill build() {
            return new PDFQuill(this);
        }
    }
}
