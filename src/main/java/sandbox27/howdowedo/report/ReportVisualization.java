package sandbox27.howdowedo.report;

/**
 * How a question's answers are visualised in the report.
 *
 * <ul>
 *   <li>{@link #FAVORABLE} - NPS-style bar for scale questions whose options carry both negative and
 *       positive scores: positive score = supporter, zero = neutral, negative = detractor.</li>
 *   <li>{@link #DISTRIBUTION} - share per option for other choice/scale questions.</li>
 *   <li>{@link #TEXT} - the list of free-text answers.</li>
 * </ul>
 */
public enum ReportVisualization {
    FAVORABLE,
    DISTRIBUTION,
    TEXT
}
