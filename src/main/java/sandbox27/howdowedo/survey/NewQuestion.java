package sandbox27.howdowedo.survey;

import java.util.List;

/**
 * Input for one question when creating or updating a survey. {@code options} is ignored for
 * TEXT/SCALE. {@code allowsComments} lets participants add a free-text comment to their answer.
 */
public record NewQuestion(String text, QuestionType type, List<String> options, boolean allowsComments) {

    /** Convenience for a question that allows comments (the default). */
    public NewQuestion(String text, QuestionType type, List<String> options) {
        this(text, type, options, true);
    }
}
