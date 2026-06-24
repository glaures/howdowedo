package sandbox27.howdowedo.survey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.common.errors.LocalizedException;
import sandbox27.howdowedo.common.errors.NotFoundException;
import sandbox27.howdowedo.user.Role;
import sandbox27.howdowedo.user.User;
import sandbox27.howdowedo.user.UserRepository;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SurveyAccessServiceTest {

    @Autowired
    private SurveyService surveyService;
    @Autowired
    private SurveyAccessService accessService;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private InvitationMailSender mailSender;

    @Test
    void creatorImplicitlyHoldsEveryPermission() {
        User creator = user("creator");
        Survey survey = survey(creator);

        assertThat(accessService.permissionsFor(survey, creator.getId()))
                .containsExactlyInAnyOrder(SurveyPermission.values());
    }

    @Test
    void aStrangerHasNoAccessAndIsToldTheSurveyDoesNotExist() {
        Survey survey = survey(user("creator"));
        User stranger = user("stranger");

        assertThat(accessService.permissionsFor(survey, stranger.getId())).isEmpty();
        assertThatThrownBy(() -> accessService.require(survey, stranger.getId(), SurveyPermission.EDIT))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void grantingRightsLetsTheUserActWithinThemOnly() {
        Survey survey = survey(user("creator"));
        User member = user("member");

        accessService.setPermissions(survey.getId(), member.getId(), Set.of(SurveyPermission.EDIT));

        assertThat(accessService.permissionsFor(survey, member.getId()))
                .containsExactly(SurveyPermission.EDIT);
        accessService.require(survey, member.getId(), SurveyPermission.EDIT); // does not throw
        assertThatThrownBy(() -> accessService.require(survey, member.getId(), SurveyPermission.ANALYZE))
                .isInstanceOf(LocalizedException.class)
                .hasMessage("error.survey.forbidden");
    }

    @Test
    void clearingAllRightsRemovesTheGrant() {
        Survey survey = survey(user("creator"));
        User member = user("member");
        accessService.setPermissions(survey.getId(), member.getId(),
                Set.of(SurveyPermission.EDIT, SurveyPermission.ANALYZE));

        accessService.setPermissions(survey.getId(), member.getId(), Set.of());

        assertThat(accessService.permissionsFor(survey, member.getId())).isEmpty();
    }

    @Test
    void theCreatorsRightsCanNeverBeChanged() {
        User creator = user("creator");
        Survey survey = survey(creator);

        assertThatThrownBy(() -> accessService.setPermissions(survey.getId(), creator.getId(),
                Set.of(SurveyPermission.EDIT)))
                .isInstanceOf(LocalizedException.class)
                .hasMessage("error.survey.ownerKeepsAllRights");
    }

    @Test
    void accessibleSurveysCoverCreatedAndSharedOnes() {
        User creator = user("creator");
        User member = user("member");
        Survey own = survey(creator);
        Survey shared = survey(member);
        accessService.setPermissions(shared.getId(), creator.getId(), Set.of(SurveyPermission.ANALYZE));

        var views = accessService.accessibleSurveys(creator.getId());

        assertThat(views).extracting(v -> v.survey().getId())
                .containsExactlyInAnyOrder(own.getId(), shared.getId());
        SurveyAccessView sharedView = views.stream()
                .filter(v -> v.survey().getId().equals(shared.getId())).findFirst().orElseThrow();
        assertThat(sharedView.canAnalyze()).isTrue();
        assertThat(sharedView.canEdit()).isFalse();
    }

    @Test
    void permissionsPageListsOwnerGrantsAndCandidates() {
        User creator = user("creator");
        User member = user("member");
        Survey survey = survey(creator);
        accessService.setPermissions(survey.getId(), member.getId(), Set.of(SurveyPermission.EDIT));

        SurveyPermissionsPage page = accessService.permissionsPage(survey.getId());

        assertThat(page.owner().getId()).isEqualTo(creator.getId());
        assertThat(page.grants()).hasSize(1);
        assertThat(page.grants().get(0).user().getId()).isEqualTo(member.getId());
        // Candidates exclude the creator and anyone already granted.
        assertThat(page.candidates()).extracting(User::getId)
                .doesNotContain(creator.getId(), member.getId());
    }

    @Test
    void accessibleSurveysIncludeTurnoutCounts() {
        User creator = user("creator");
        Survey survey = surveyService.createSurvey(creator.getId(),
                new CreateSurveyRequest("Turnout", "d", 1, null));
        Section section = surveyService.addSection(survey.getId(), "General");
        surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Happy?", QuestionType.SINGLE_CHOICE, List.of("Yes", "No")));
        surveyService.open(survey.getId());
        surveyService.distributeInvitations(survey.getId(), List.of("a@example.com", "b@example.com"));

        SurveyAccessView view = accessService.accessibleSurveys(creator.getId()).get(0);

        assertThat(view.turnout().invited()).isEqualTo(2);
        assertThat(view.turnout().responded()).isEqualTo(0);
    }

    private User user(String subject) {
        User user = new User("test", subject, subject + "@example.com", "User-" + subject);
        user.addRole(Role.USER);
        return userRepository.save(user);
    }

    private Survey survey(User creator) {
        return surveyService.createSurvey(creator.getId(),
                new CreateSurveyRequest("Survey-" + creator.getSubject(), "d", 1, null));
    }
}
