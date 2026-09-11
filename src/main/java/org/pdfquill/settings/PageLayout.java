package org.pdfquill.settings;

import org.pdfquill.paper.PaperType;
import org.pdfquill.paper.PaperUtils;
import org.pdfquill.settings.font.FontSettings;

/**
 * Encapsulates printable area, margins, line metrics, and paper type settings
 * used while rendering PDF pages.
 */
public class PageLayout {
    private FontSettings fontSettings = new FontSettings();
    // Margins
    private float marginLeft = 4f;
    private float marginRight = 4f;
    private float marginTop = fontSettings.getFontSize();
    private float marginBottom = 4f;
    private float lineSpacing = 1.15f;

    private PaperType paperType = PaperType.A4;
    private Alignment alignment = Alignment.LEFT;

    //Defined by margins
    private float startX;
    private float startY;

    // Calculated
    private float lineHeight;
    private float maxLineWidth;
    private float pageWritingHeight;

    /**
     * Builds a layout for the given paper type using default margins and fonts.
     *
     * @param paperType paper format to base the layout on; defaults to A4 when {@code null}
     */
    public PageLayout(PaperType paperType) {
        this.paperType = paperType != null ? paperType : PaperType.A4;
        this.assignDependentAttrs();
    }

    /**
     * Applies externally managed font settings without cloning and updates internal metrics.
     *
     * @param fontSettings shared font settings instance (callers retain ownership)
     */
    public void updateFontSettings(FontSettings fontSettings) {
        this.fontSettings = fontSettings;
        recalculateAfterFontUpdate();
    }

    /**
     * Copy constructor that performs defensive cloning of dependent settings.
     *
     * @param other layout to clone; can be {@code null}
     */
    public PageLayout(PageLayout other) {
        if (other != null) {
            this.marginLeft = other.marginLeft;
            this.marginRight = other.marginRight;
            this.marginTop = other.marginTop;
            this.marginBottom = other.marginBottom;
            this.fontSettings = other.fontSettings != null ? other.fontSettings.copy() : new FontSettings();
            this.paperType = other.paperType != null ? other.paperType : PaperType.A4;
            this.alignment = other.alignment != null ? other.alignment : Alignment.LEFT;
        }
        this.assignDependentAttrs();
    }

    /**
     * Replaces the font settings and recalculates derived measurements.
     *
     * @param fontSettings new font configuration; defaults are used when {@code null}
     */
    public void setFontSettings(FontSettings fontSettings) {
        this.fontSettings = fontSettings != null ? fontSettings.copy() : new FontSettings();
        this.assignDependentAttrs();
    }

    /**
     * Updates all margins in points and recalculates dependent measurements.
     *
     * @param marginLeft   left margin
     * @param marginRight  right margin
     * @param marginTop    top margin
     * @param marginBottom bottom margin
     */
    public void setMargins(float marginLeft, float marginRight, float marginTop, float marginBottom) {
        validateMargin(marginLeft, "marginLeft");
        validateMargin(marginRight, "marginRight");
        validateMargin(marginTop, "marginTop");
        validateMargin(marginBottom, "marginBottom");
        this.marginLeft = marginLeft;
        this.marginRight = marginRight;
        this.marginTop = marginTop;
        this.marginBottom = marginBottom;
        this.assignDependentAttrs();
    }

    /**
     * Recomputes derived measurements such as line height, max line width, and writing height.
     */
    public void recalculate() {
        this.assignDependentAttrs();
    }

    private void recalculateAfterFontUpdate() {
        this.lineHeight = this.fontSettings.getFontSize() * this.lineSpacing;
        this.pageWritingHeight = getPageHeight() - this.marginTop - this.marginBottom;
    }

    private void assignDependentAttrs() {
        this.startX = this.marginLeft;
        this.startY = getPageHeight() - this.marginTop;

        this.maxLineWidth = getPageWidth() - this.marginLeft - this.marginRight;

        this.lineHeight = this.fontSettings.getFontSize() * this.lineSpacing;
        this.pageWritingHeight = getPageHeight() - this.marginTop - this.marginBottom;
    }

    private static void validateMargin(float value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " cannot be negative");
        }
    }

    /**
     * @return currently configured paper type
     */
    public PaperType getPaperType() {
        return this.paperType;
    }

    /**
     * Sets the paper type and recalculates derived measurements.
     *
     * @param paperType new paper format; defaults to A4 when {@code null}
     */
    public void setPaperType(PaperType paperType) {
        this.paperType = paperType != null ? paperType : PaperType.A4;
        this.assignDependentAttrs();
    }

    /**
     * @return currently configured default text alignment
     */
    public Alignment getAlignment() {
        return this.alignment;
    }

    /**
     * Sets the default text alignment applied to printed lines that don't request an explicit override.
     *
     * @param alignment new default alignment; defaults to {@link Alignment#LEFT} when {@code null}
     */
    public void setAlignment(Alignment alignment) {
        this.alignment = alignment != null ? alignment : Alignment.LEFT;
    }

    /**
     * @return {@code true} when the current paper type is thermal
     */
    public boolean isThermalPaper() {
        return PaperUtils.isThermal(this.paperType);
    }

    /**
     * @return {@code true} when the current paper type is not thermal
     */
    public boolean isNonThermalPaper() {
        return PaperUtils.isNotThermal(this.paperType);
    }

    /**
     * @return page width in points
     */
    public float getPageWidth() {
        return this.paperType.getWidth();
    }

    /**
     * @return page height in points
     */
    public float getPageHeight() {
        return this.paperType.getHeight();
    }

    /**
     * @return x-coordinate of the printable area's left edge
     */
    public float getStartX() {
        return startX;
    }

    /**
     * @return y-coordinate of the first baseline measured from the page bottom
     */
    public float getStartY() {
        return startY;
    }

    /**
     * @return distance between two text baselines
     */
    public float getLineHeight() {
        return lineHeight;
    }

    /**
     * @return maximum width allowed for a text line
     */
    public float getMaxLineWidth() {
        return maxLineWidth;
    }

    /**
     * Overrides the calculated line width. Use with care since other metrics are not updated.
     *
     * @param maxLineWidth width in points
     */
    public void setMaxLineWidth(float maxLineWidth) {
        this.maxLineWidth = maxLineWidth;
    }

    /**
     * @return font settings applied to text rendering
     */
    public FontSettings getFontSettings() {
        return this.fontSettings;
    }

    /**
     * @return left margin in points
     */
    public float getMarginLeft() {
        return marginLeft;
    }

    /**
     * @return right margin in points
     */
    public float getMarginRight() {
        return marginRight;
    }

    /**
     * @return top margin in points
     */
    public float getMarginTop() {
        return marginTop;
    }

    /**
     * @return bottom margin in points
     */
    public float getMarginBottom() {
        return marginBottom;
    }

    /**
     * @return height available for content after top/bottom margins are applied
     */
    public float getPageWritingHeight() {
        return pageWritingHeight;
    }

    public float getLineSpacing() {
        return lineSpacing;
    }
}
