package sandbox27.howdowedo.user;

/**
 * System roles. {@link #USER} is the default everyone receives; the others grant additional rights.
 */
public enum Role {

    /** May assign roles to other users. */
    ADMINISTRATOR,
    /** May create and evaluate surveys. */
    SURVEY_MANAGER,
    /** May analyse survey results in greater depth. */
    SURVEY_ANALYST,
    /** Default role, no special rights. */
    USER;

    /** Spring Security authority name for this role (e.g. {@code ROLE_ADMINISTRATOR}). */
    public String authority() {
        return "ROLE_" + name();
    }
}
