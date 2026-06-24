package sandbox27.howdowedo.survey;

/**
 * A right a user can hold on a single survey, independent of the global {@link sandbox27.howdowedo.user.Role}s.
 *
 * <p>The global {@code SURVEY_MANAGER} role lets someone <em>create</em> surveys; these per-survey
 * permissions then decide who may work with an individual survey. The survey's creator always holds
 * all of them (see {@link SurveyAccessService}).
 */
public enum SurveyPermission {

    /** Grant and revoke other users' rights on the survey. */
    ADMINISTER,
    /** Use all survey-building functions: sections, questions, recipients, lifecycle, invitations. */
    EDIT,
    /** Run the available analyses over the survey's responses. */
    ANALYZE
}
