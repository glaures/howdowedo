package sandbox27.howdowedo.user;

/**
 * System roles. {@link #USER} is the default everyone receives; the others grant additional rights.
 *
 * <p>Working with an individual survey (editing, analysing) is governed by the per-survey
 * {@link sandbox27.howdowedo.survey.SurveyPermission}s, not by a global role: holding
 * {@code SURVEY_MANAGER} only lets a user <em>create</em> surveys.
 */
public enum Role {

    /** May administer users and assign roles. */
    ADMINISTRATOR,
    /** May create surveys (and manage those they own or were granted access to). */
    SURVEY_MANAGER,
    /** Default role, no special rights. */
    USER;

    /** Spring Security authority name for this role (e.g. {@code ROLE_ADMINISTRATOR}). */
    public String authority() {
        return "ROLE_" + name();
    }
}
