package org.pdfquill.settings;

/**
 * Pure helper computing the X coordinate a line of text should be drawn at, given
 * its {@link Alignment} and the width of the printable area.
 */
public final class AlignmentUtils {

    private AlignmentUtils() {
    }

    /**
     * Resolves the X coordinate for a line of text based on the requested alignment.
     *
     * @param alignment    requested alignment; treated as {@link Alignment#LEFT} when {@code null}
     * @param startX       X coordinate of the printable area's left edge
     * @param maxLineWidth width of the printable area
     * @param textWidth    measured width of the text being positioned
     * @return X coordinate to draw the text at
     */
    public static float resolveX(Alignment alignment, float startX, float maxLineWidth, float textWidth) {
        Alignment effective = alignment != null ? alignment : Alignment.LEFT;

        if (textWidth <= 0f) {
            return startX;
        }

        float offset;
        switch (effective) {
            case LEFT:
                return startX;
            case RIGHT:
                offset = maxLineWidth - textWidth;
                break;
            case CENTER:
                offset = (maxLineWidth - textWidth) / 2f;
                break;
            default:
                throw new IllegalArgumentException("Unsupported alignment: " + effective);
        }

        return startX + Math.max(offset, 0f);
    }
}
