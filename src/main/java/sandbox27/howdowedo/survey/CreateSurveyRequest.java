package sandbox27.howdowedo.survey;

import java.time.LocalDate;

/** Input for creating a survey. Sections and questions are added afterwards. */
public record CreateSurveyRequest(String title,
                                  String description,
                                  int minResponsesForResults,
                                  LocalDate endDate) {
}
