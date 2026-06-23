package sandbox27.howdowedo.survey;

import java.util.List;

/** Input for creating a survey. */
public record CreateSurveyRequest(String title,
                                  String description,
                                  int minResponsesForResults,
                                  List<NewQuestion> questions) {
}
