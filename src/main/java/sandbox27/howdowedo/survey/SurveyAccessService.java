package sandbox27.howdowedo.survey;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.common.errors.LocalizedException;
import sandbox27.howdowedo.common.errors.NotFoundException;
import sandbox27.howdowedo.user.User;
import sandbox27.howdowedo.user.UserService;

import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Per-survey authorisation: who may edit, analyse or administer an individual survey.
 *
 * <p>The survey's creator always holds every {@link SurveyPermission} and is never stored as a grant,
 * which makes it impossible to revoke any of the creator's rights. Everyone else holds exactly the
 * permissions recorded in their {@link SurveyAccess} row (none, if there is no row).
 */
@Service
public class SurveyAccessService {

    private final SurveyRepository surveys;
    private final SurveyAccessRepository access;
    private final SurveyAccessCodeRepository accessCodes;
    private final UserService users;

    public SurveyAccessService(SurveyRepository surveys, SurveyAccessRepository access,
                               SurveyAccessCodeRepository accessCodes, UserService users) {
        this.surveys = surveys;
        this.access = access;
        this.accessCodes = accessCodes;
        this.users = users;
    }

    /** The user's effective permissions on the survey (all of them for the creator). */
    @Transactional(readOnly = true)
    public Set<SurveyPermission> permissionsFor(Survey survey, Long userId) {
        if (userId.equals(survey.getCreatedByUserId())) {
            return EnumSet.allOf(SurveyPermission.class);
        }
        return access.findBySurveyIdAndUserId(survey.getId(), userId)
                .map(SurveyAccess::getPermissions)
                .filter(p -> !p.isEmpty())
                .map(EnumSet::copyOf)
                .orElse(EnumSet.noneOf(SurveyPermission.class));
    }

    /**
     * Asserts the user may perform an action needing {@code needed}. A user with no access at all is
     * told the survey does not exist (so its existence is not leaked); a user who has some access but
     * lacks this particular right gets a "forbidden" business error shown inline.
     */
    @Transactional(readOnly = true)
    public void require(Survey survey, Long userId, SurveyPermission needed) {
        Set<SurveyPermission> held = permissionsFor(survey, userId);
        if (held.isEmpty()) {
            throw new NotFoundException("error.survey.notFound", survey.getId());
        }
        if (!held.contains(needed)) {
            throw new LocalizedException("error.survey.forbidden");
        }
    }

    /** Surveys the user created or has been granted any permission on, newest first. */
    @Transactional(readOnly = true)
    public List<SurveyAccessView> accessibleSurveys(Long userId) {
        Map<Long, Survey> byId = new LinkedHashMap<>();
        surveys.findByCreatedByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(userId)
                .forEach(s -> byId.put(s.getId(), s));

        List<Long> grantedIds = access.findByUserId(userId).stream().map(SurveyAccess::getSurveyId).toList();
        if (!grantedIds.isEmpty()) {
            surveys.findByIdInAndDeletedAtIsNullOrderByCreatedAtDesc(grantedIds)
                    .forEach(s -> byId.putIfAbsent(s.getId(), s));
        }

        // Resolve every owner in one query so the list can show who owns each survey.
        Map<Long, User> ownersById = users.findByIds(
                byId.values().stream().map(Survey::getCreatedByUserId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, u -> u));

        return byId.values().stream()
                .sorted(Comparator.comparing(Survey::getCreatedAt).reversed())
                .map(survey -> {
                    // Initialise lazy collections for the list (open-in-view is disabled).
                    survey.getSections().forEach(section -> section.getQuestions().size());
                    Set<SurveyPermission> p = permissionsFor(survey, userId);
                    SurveyTurnout turnout = new SurveyTurnout(accessCodes.countBySurveyId(survey.getId()),
                            accessCodes.countBySurveyIdAndUsedTrue(survey.getId()));
                    return new SurveyAccessView(survey, ownersById.get(survey.getCreatedByUserId()), turnout,
                            p.contains(SurveyPermission.ADMINISTER),
                            p.contains(SurveyPermission.EDIT), p.contains(SurveyPermission.ANALYZE));
                })
                .toList();
    }

    /** Data for the permissions admin screen: the creator, current grants and grantable users. */
    @Transactional(readOnly = true)
    public SurveyPermissionsPage permissionsPage(Long surveyId) {
        Survey survey = require(surveyId);
        User owner = users.findById(survey.getCreatedByUserId()).orElse(null);

        List<SurveyAccess> rows = access.findBySurveyId(surveyId);
        Map<Long, User> usersById = users.findByIds(rows.stream().map(SurveyAccess::getUserId).toList())
                .stream().collect(Collectors.toMap(User::getId, u -> u));
        List<SurveyPermissionsPage.Grant> grants = rows.stream()
                .filter(row -> usersById.containsKey(row.getUserId()))
                .map(row -> new SurveyPermissionsPage.Grant(usersById.get(row.getUserId()), row.getPermissions()))
                .sorted(Comparator.comparing(g -> g.user().getName(), Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        List<User> allUsers = users.findAll().stream()
                .sorted(Comparator.comparing(User::getName, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();

        Set<Long> taken = rows.stream().map(SurveyAccess::getUserId).collect(Collectors.toCollection(java.util.HashSet::new));
        taken.add(survey.getCreatedByUserId());
        List<User> candidates = allUsers.stream().filter(u -> !taken.contains(u.getId())).toList();

        // Ownership can be handed to anyone but the current owner (including already-granted users).
        List<User> ownerCandidates = allUsers.stream()
                .filter(u -> !u.getId().equals(survey.getCreatedByUserId())).toList();

        return new SurveyPermissionsPage(survey, owner, grants, candidates, ownerCandidates);
    }

    /**
     * Hands ownership of a survey to another user. The new owner then implicitly holds every
     * permission (any delegated grant they had becomes redundant and is removed), while the previous
     * owner keeps full access as a normal grant so the handover never locks anyone out.
     */
    @Transactional
    public void changeOwner(Long surveyId, Long newOwnerId) {
        Survey survey = require(surveyId);
        Long previousOwnerId = survey.getCreatedByUserId();
        if (newOwnerId.equals(previousOwnerId)) {
            return; // already the owner: nothing to do
        }
        if (users.findById(newOwnerId).isEmpty()) {
            throw new NotFoundException("error.user.notFound", newOwnerId);
        }

        // The new owner no longer needs a delegated grant; drop it to keep the "owner has no row" invariant.
        access.findBySurveyIdAndUserId(surveyId, newOwnerId).ifPresent(access::delete);

        survey.assignOwner(newOwnerId);
        surveys.save(survey);

        // Preserve the previous owner's access explicitly (they lose their implicit owner rights).
        setPermissions(surveyId, previousOwnerId, EnumSet.allOf(SurveyPermission.class));
    }

    /**
     * Sets a user's permissions on a survey. An empty set removes the grant entirely. The creator's
     * rights cannot be changed here - they always keep everything.
     */
    @Transactional
    public void setPermissions(Long surveyId, Long targetUserId, Set<SurveyPermission> permissions) {
        Survey survey = require(surveyId);
        if (targetUserId.equals(survey.getCreatedByUserId())) {
            throw new LocalizedException("error.survey.ownerKeepsAllRights");
        }
        if (users.findById(targetUserId).isEmpty()) {
            throw new NotFoundException("error.user.notFound", targetUserId);
        }

        SurveyAccess grant = access.findBySurveyIdAndUserId(surveyId, targetUserId).orElse(null);
        if (permissions == null || permissions.isEmpty()) {
            if (grant != null) {
                access.delete(grant);
            }
            return;
        }
        if (grant == null) {
            grant = new SurveyAccess(surveyId, targetUserId);
        }
        grant.setPermissions(permissions);
        access.save(grant);
    }

    private Survey require(Long surveyId) {
        return surveys.findById(surveyId)
                .filter(survey -> !survey.isDeleted()) // a soft-deleted survey behaves as if it never existed
                .orElseThrow(() -> new NotFoundException("error.survey.notFound", surveyId));
    }
}
