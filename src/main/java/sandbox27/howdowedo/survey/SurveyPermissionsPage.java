package sandbox27.howdowedo.survey;

import java.util.List;
import java.util.Set;

import sandbox27.howdowedo.user.User;

/**
 * Everything the survey permissions screen needs: the read-only creator, the delegated grants, and
 * the users who could still be granted access.
 */
public record SurveyPermissionsPage(Survey survey,
                                    User owner,
                                    List<Grant> grants,
                                    List<User> candidates) {

    /** A delegated grant: which user holds which permissions. */
    public record Grant(User user, Set<SurveyPermission> permissions) {
    }
}
