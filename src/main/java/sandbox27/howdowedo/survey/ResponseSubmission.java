package sandbox27.howdowedo.survey;

import java.util.List;

/** A complete survey submission by one participant. */
public record ResponseSubmission(List<AnswerSubmission> answers) {
}
