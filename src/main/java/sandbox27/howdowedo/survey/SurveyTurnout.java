package sandbox27.howdowedo.survey;

/**
 * Aggregate turnout for a survey: how many access codes were issued and how many have been used.
 * Reveals participation numbers only - never who participated.
 */
public record SurveyTurnout(long invited, long responded) {
}
