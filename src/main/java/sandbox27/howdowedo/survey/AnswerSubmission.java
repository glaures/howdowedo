package sandbox27.howdowedo.survey;

import java.util.List;

/**
 * One answer in a submission: a question, the chosen value(s) (multiple for MULTIPLE_CHOICE) and an
 * optional free-text {@code comment}.
 */
public record AnswerSubmission(Long questionId, List<String> values, String comment) {

    /** Convenience for an answer without a comment. */
    public AnswerSubmission(Long questionId, List<String> values) {
        this(questionId, values, null);
    }
}
