package sandbox27.howdowedo.survey;

import java.util.List;

/** Input for one question when creating a survey. {@code options} is ignored for TEXT/SCALE. */
public record NewQuestion(String text, QuestionType type, List<String> options) {
}
