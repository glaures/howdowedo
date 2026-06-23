package sandbox27.howdowedo.survey;

import java.util.List;

/** One answer in a submission: a question and the chosen value(s) (multiple for MULTIPLE_CHOICE). */
public record AnswerSubmission(Long questionId, List<String> values) {
}
