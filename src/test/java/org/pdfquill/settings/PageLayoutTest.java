package org.pdfquill.settings;

import org.junit.jupiter.api.Test;
import org.pdfquill.paper.PaperType;
import org.pdfquill.settings.font.FontSettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PageLayoutTest {

    @Test
    void setMarginsUpdatesDerivedMetrics() {
        PageLayout layout = new PageLayout(PaperType.A4);

        layout.setMargins(10f, 20f, 30f, 40f);

        assertThat(layout.getStartX()).isEqualTo(10f);
        assertThat(layout.getStartY()).isEqualTo(layout.getPageHeight() - 30f);
        assertThat(layout.getMaxLineWidth()).isEqualTo(layout.getPageWidth() - 30f);
        assertThat(layout.getPageWritingHeight()).isEqualTo(layout.getPageHeight() - 70f);
    }

    @Test
    void setFontSettingsCreatesDefensiveCopyAndRecalculatesLineHeight() {
        PageLayout layout = new PageLayout(PaperType.A4);
        FontSettings customFont = new FontSettings();
        customFont.setFontSize(18);

        layout.setFontSettings(customFont);

        assertThat(layout.getFontSettings()).isNotSameAs(customFont);
        assertThat(layout.getFontSettings().getFontSize()).isEqualTo(18);
        assertThat(layout.getLineHeight()).isCloseTo(18f * layout.getLineSpacing(), within(1e-6f));

        customFont.setFontSize(8);

        assertThat(layout.getFontSettings().getFontSize()).isEqualTo(18);
    }

    @Test
    void defaultAlignmentIsLeft() {
        PageLayout layout = new PageLayout(PaperType.A4);

        assertThat(layout.getAlignment()).isEqualTo(Alignment.LEFT);
    }

    @Test
    void setAlignmentUpdatesValueAndTreatsNullAsLeft() {
        PageLayout layout = new PageLayout(PaperType.A4);

        layout.setAlignment(Alignment.CENTER);
        assertThat(layout.getAlignment()).isEqualTo(Alignment.CENTER);

        layout.setAlignment(null);
        assertThat(layout.getAlignment()).isEqualTo(Alignment.LEFT);
    }

    @Test
    void copyConstructorPreservesAlignment() {
        PageLayout original = new PageLayout(PaperType.A4);
        original.setAlignment(Alignment.RIGHT);

        PageLayout copy = new PageLayout(original);

        assertThat(copy.getAlignment()).isEqualTo(Alignment.RIGHT);
    }
}
