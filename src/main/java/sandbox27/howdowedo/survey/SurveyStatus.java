package sandbox27.howdowedo.survey;

/**
 * Lifecycle of a survey. Responses are only accepted while {@link #OPEN}.
 */
public enum SurveyStatus {
    DRAFT,
    OPEN,
    CLOSED
}
