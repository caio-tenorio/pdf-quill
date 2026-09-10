package org.pdfquill.writer;

import org.junit.jupiter.api.Test;
import org.pdfquill.settings.font.FontSettings;
import org.pdfquill.settings.font.FontUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class LineAccumulatorTest {

    @Test
    void addChunkTracksHorizontalOffsetsAndFlushClearsBuffer() throws IOException {
        LineAccumulator accumulator = new LineAccumulator(12f);
        FontSettings fontSettings = new FontSettings();

        Text first = new Text("AB", fontSettings);
        float firstWidth = FontUtils.getTextWidth("AB", fontSettings.getSelectedFont(), fontSettings.getFontSize());
        accumulator.addChunk(first, firstWidth, fontSettings.getFontSize());

        Text second = new Text("CD", fontSettings);
        float secondWidth = FontUtils.getTextWidth("CD", fontSettings.getSelectedFont(), fontSettings.getFontSize());
        accumulator.addChunk(second, secondWidth, fontSettings.getFontSize());

        List<TextLinePlan> plans = new ArrayList<>();
        accumulator.flushInto(plans);

        assertThat(plans).hasSize(1);
        TextLinePlan plan = plans.get(0);
        assertThat(plan.getTextList()).hasSize(2);
        assertThat(plan.getTextList().get(0).getX()).isEqualTo(12f);
        assertThat(plan.getTextList().get(1).getX()).isEqualTo(12f + firstWidth);

        accumulator.flushInto(plans);

        assertThat(plans).hasSize(1);
        assertThat(accumulator.isEmpty()).isTrue();
    }
}
