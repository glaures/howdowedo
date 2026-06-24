package sandbox27.howdowedo.survey;

import java.util.List;
import java.util.Map;

/**
 * Input for one question when creating or updating a survey. {@code options} is ignored for
 * TEXT/SCALE. {@code allowsComments} lets participants add a free-text comment to their answer.
 * {@code optionScores} carries an optional numeric score per option label (snapshotted from a scale).
 */
public record NewQuestion(String text, QuestionType type, List<String> options, boolean allowsComments,
                          Map<String, Integer> optionScores) {

    /** Convenience for a question without per-option scores. */
    public NewQuestion(String text, QuestionType type, List<String> options, boolean allowsComments) {
        this(text, type, options, allowsComments, Map.of());
    }

    /** Convenience for a question that allows comments (the default) and has no scores. */
    public NewQuestion(String text, QuestionType type, List<String> options) {
        this(text, type, options, true, Map.of());
    }
}
