package sandbox27.howdowedo.report;

/**
 * NPS-style breakdown of a scored scale question. Responses are bucketed by the score of the chosen
 * option: positive = supporter, zero = neutral, negative = detractor. The percentages always sum to
 * 100 ({@code neutralPercent} absorbs the rounding remainder). {@code favorablePercent} is the
 * headline "favorable score".
 */
public record FavorableScore(int favorablePercent,
                             int neutralPercent,
                             int detractorPercent,
                             long supporters,
                             long neutral,
                             long detractors,
                             long total) {
}
