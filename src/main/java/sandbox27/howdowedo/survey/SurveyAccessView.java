package sandbox27.howdowedo.survey;

/**
 * A survey together with the current user's effective rights on it and its turnout, for the list.
 */
public record SurveyAccessView(Survey survey,
                               SurveyTurnout turnout,
                               boolean canAdminister,
                               boolean canEdit,
                               boolean canAnalyze) {
}
