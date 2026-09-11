package org.pdfquill.settings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlignmentUtilsTest {

    @Test
    void leftAlignmentAlwaysReturnsStartX() {
        float x = AlignmentUtils.resolveX(Alignment.LEFT, 10f, 200f, 50f);

        assertThat(x).isEqualTo(10f);
    }

    @Test
    void rightAlignmentAnchorsToRightEdge() {
        float x = AlignmentUtils.resolveX(Alignment.RIGHT, 10f, 200f, 50f);

        assertThat(x).isEqualTo(10f + 200f - 50f);
    }

    @Test
    void centerAlignmentCentersWithinLineWidth() {
        float x = AlignmentUtils.resolveX(Alignment.CENTER, 10f, 200f, 50f);

        assertThat(x).isEqualTo(10f + (200f - 50f) / 2f);
    }

    @Test
    void emptyOrZeroWidthTextReturnsStartX() {
        assertThat(AlignmentUtils.resolveX(Alignment.RIGHT, 10f, 200f, 0f)).isEqualTo(10f);
        assertThat(AlignmentUtils.resolveX(Alignment.CENTER, 10f, 200f, -1f)).isEqualTo(10f);
    }

    @Test
    void nullAlignmentDefaultsToLeft() {
        float x = AlignmentUtils.resolveX(null, 10f, 200f, 50f);

        assertThat(x).isEqualTo(10f);
    }

    @Test
    void textWiderThanLineClampsToStartX() {
        float rightX = AlignmentUtils.resolveX(Alignment.RIGHT, 10f, 100f, 150f);
        float centerX = AlignmentUtils.resolveX(Alignment.CENTER, 10f, 100f, 150f);

        assertThat(rightX).isEqualTo(10f);
        assertThat(centerX).isEqualTo(10f);
    }
}
