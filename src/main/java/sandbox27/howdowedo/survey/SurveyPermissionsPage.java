package sandbox27.howdowedo.survey;

import java.util.List;
import java.util.Set;

import sandbox27.howdowedo.user.User;

/**
 * Everything the survey permissions screen needs: the current owner, the delegated grants, the users
 * who could still be granted access, and the users the survey could be handed over to
 * ({@code ownerCandidates}: everyone except the current owner).
 */
public record SurveyPermissionsPage(Survey survey,
                                    User owner,
                                    List<Grant> grants,
                                    List<User> candidates,
                                    List<User> ownerCandidates) {

    /** A delegated grant: which user holds which permissions. */
    public record Grant(User user, Set<SurveyPermission> permissions) {
    }
}
