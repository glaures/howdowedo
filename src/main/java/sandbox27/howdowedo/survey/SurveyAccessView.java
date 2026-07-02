package sandbox27.howdowedo.survey;

import sandbox27.howdowedo.user.User;

/**
 * A survey together with its owner, the current user's effective rights on it and its turnout, for
 * the list. {@code owner} may be {@code null} if the owning user no longer exists.
 */
public record SurveyAccessView(Survey survey,
                               User owner,
                               SurveyTurnout turnout,
                               boolean canAdminister,
                               boolean canEdit,
                               boolean canAnalyze) {
}
