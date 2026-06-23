package sandbox27.howdowedo.survey;

import java.util.List;

/** Aggregated, anonymous results for a survey. Only produced once the k-anonymity threshold is met. */
public record SurveyResults(Long surveyId,
                            String title,
                            int responseCount,
                            List<QuestionResult> questions) {
}
