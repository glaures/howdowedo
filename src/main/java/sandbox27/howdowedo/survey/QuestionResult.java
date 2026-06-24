package sandbox27.howdowedo.survey;

import java.util.List;
import java.util.Map;

/**
 * Aggregated results for one question. For choice/scale questions {@code optionCounts} holds the
 * tally per value; for text questions {@code textAnswers} holds the (anonymous) free-text answers.
 * {@code comments} holds the (anonymous) free-text comments participants attached to their answer.
 */
public record QuestionResult(Long questionId,
                             String text,
                             QuestionType type,
                             Map<String, Long> optionCounts,
                             List<String> textAnswers,
                             List<String> comments) {
}
