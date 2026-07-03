package sandbox27.howdowedo.survey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.scale.Scale;
import sandbox27.howdowedo.scale.ScaleService;
import sandbox27.howdowedo.scale.ScaleValue;
import sandbox27.howdowedo.user.Role;
import sandbox27.howdowedo.user.User;
import sandbox27.howdowedo.user.UserRepository;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SurveyManagementControllerTest {

    private static final SimpleGrantedAuthority MANAGER =
            new SimpleGrantedAuthority(Role.SURVEY_MANAGER.authority());
    private static final SimpleGrantedAuthority PLAIN_USER =
            new SimpleGrantedAuthority(Role.USER.authority());

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private SurveyService surveyService;
    @Autowired
    private SurveyRecipientService recipientService;
    @Autowired
    private SurveyAccessService accessService;
    @Autowired
    private ScaleService scaleService;
    @MockitoBean
    private InvitationMailSender mailSender;

    // --- menu visibility / access control ---

    @Test
    void surveyManagerSeesSurveyMenu() throws Exception {
        provisionManager();
        // A manager is redirected from "/" to the surveys area, which carries the same navbar.
        mockMvc.perform(get("/surveys").with(oauth2Login().authorities(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/scales")))
                .andExpect(content().string(containsString("/surveys")));
    }

    @Test
    void plainUserHasNoSurveyMenu() throws Exception {
        mockMvc.perform(get("/").with(oauth2Login().authorities(PLAIN_USER)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("/scales"))));
    }

    @Test
    void plainUserMaySeeTheirSurveyListButNotCreateSurveysOrManageScales() throws Exception {
        provisionUser("user"); // a plain user with no grants
        // The list itself is reachable (it just shows whatever was shared with them) ...
        mockMvc.perform(get("/surveys").with(oauth2Login().authorities(PLAIN_USER)))
                .andExpect(status().isOk());
        // ... but creating surveys and managing scales stays a Survey Manager privilege.
        mockMvc.perform(get("/surveys/new").with(oauth2Login().authorities(PLAIN_USER)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/surveys").param("title", "X")
                        .with(oauth2Login().authorities(PLAIN_USER)).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/scales").with(oauth2Login().authorities(PLAIN_USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    void resolvesCurrentUserBySubjectClaimForRealOidcLogin() throws Exception {
        // Real OIDC: the principal name is the provider's name attribute (e.g. the display name),
        // while the user is keyed on the 'sub' claim. CurrentUser must resolve via 'sub'.
        User user = new User("test", "oidc-sub-123", "g@example.com", "Guido Laures");
        user.addRole(Role.USER);
        user.addRole(Role.SURVEY_MANAGER);
        userRepository.save(user);

        mockMvc.perform(get("/surveys")
                        .with(oidcLogin().authorities(MANAGER)
                                .idToken(token -> token.subject("oidc-sub-123").claim("name", "Guido Laures"))))
                .andExpect(status().isOk());
    }

    // --- survey building flow ---

    @Test
    void managerCreatesSurveyAddsSectionThenSnapshotsAScaleIntoAQuestion() throws Exception {
        User manager = provisionManager();
        Scale agreement = scaleService.create("Agreement", null, scaleValues("No", "Maybe", "Yes"));

        mockMvc.perform(post("/surveys")
                        .param("title", "Engagement")
                        .param("description", "How are we doing?")
                        .param("minResponsesForResults", "3")
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/surveys/*"));

        Survey survey = surveyService.findByCreator(manager.getId()).get(0);

        mockMvc.perform(post("/surveys/{id}/sections", survey.getId())
                        .param("title", "Wellbeing")
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Long sectionId = surveyService.get(survey.getId()).getSections().get(0).getId();

        mockMvc.perform(post("/surveys/{id}/sections/{sectionId}/questions", survey.getId(), sectionId)
                        .param("text", "Do you feel valued?")
                        .param("type", "SINGLE_CHOICE")
                        .param("scaleId", String.valueOf(agreement.getId()))
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Survey reloaded = surveyService.get(survey.getId());
        assertThat(reloaded.getQuestions()).hasSize(1);
        assertThat(reloaded.getQuestions().get(0).getOptions()).containsExactly("No", "Maybe", "Yes");
    }

    @Test
    void addingAQuestionFromAScaleSnapshotsItsPerOptionScores() throws Exception {
        User manager = provisionManager();
        Scale agreement = scaleService.create("Signed", null,
                List.of(new ScaleValue("No", -1), new ScaleValue("Yes", 1)));
        Survey survey = surveyService.createSurvey(manager.getId(),
                new CreateSurveyRequest("Engagement", "d", 1, null));
        Section section = surveyService.addSection(survey.getId(), "General");

        mockMvc.perform(post("/surveys/{id}/sections/{sectionId}/questions", survey.getId(), section.getId())
                        .param("text", "Engaged?")
                        .param("type", "SCALE")
                        .param("scaleId", String.valueOf(agreement.getId()))
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Question question = surveyService.get(survey.getId()).getQuestions().get(0);
        assertThat(question.getOptions()).containsExactly("No", "Yes");
        assertThat(question.getOptionScores()).containsEntry("No", -1).containsEntry("Yes", 1);
    }

    @Test
    void managerOpensSurveySendsInvitationsThenCloses() throws Exception {
        User manager = provisionManager();
        Survey survey = surveyService.createSurvey(manager.getId(),
                new CreateSurveyRequest("Engagement", "d", 1, null));
        Section section = surveyService.addSection(survey.getId(), "General");
        surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Happy?", QuestionType.SINGLE_CHOICE, List.of("Yes", "No")));

        recipientService.addRecipients(survey.getId(), "a@example.com, b@example.com");

        mockMvc.perform(post("/surveys/{id}/open", survey.getId())
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(surveyService.get(survey.getId()).getStatus()).isEqualTo(SurveyStatus.OPEN);

        mockMvc.perform(post("/surveys/{id}/invitations", survey.getId())
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("invitedCount", 2));
        verify(mailSender, times(2)).sendInvitation(anyString(), anyString(), anyString());
        assertThat(recipientService.invitedCount(survey.getId())).isEqualTo(2);

        // A second send reaches nobody new: all recipients are already invited.
        mockMvc.perform(post("/surveys/{id}/invitations", survey.getId())
                        .header("Referer", "http://localhost/surveys/" + survey.getId())
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(flash().attribute("errorMessage", "All recipients have already been invited"));

        mockMvc.perform(post("/surveys/{id}/close", survey.getId())
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(status().is3xxRedirection());
        assertThat(surveyService.get(survey.getId()).getStatus()).isEqualTo(SurveyStatus.CLOSED);
    }

    @Test
    void openingEmptySurveyRedirectsBackWithInlineError() throws Exception {
        User manager = provisionManager();
        Survey survey = surveyService.createSurvey(manager.getId(),
                new CreateSurveyRequest("Empty", "d", 1, null));

        mockMvc.perform(post("/surveys/{id}/open", survey.getId())
                        .header("Referer", "http://localhost/surveys/" + survey.getId())
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/surveys/" + survey.getId()))
                .andExpect(flash().attribute("errorMessage",
                        "Add at least one question before opening the survey"));

        assertThat(surveyService.get(survey.getId()).getStatus()).isEqualTo(SurveyStatus.DRAFT);
    }

    @Test
    void managerCannotOpenSomeoneElsesSurvey() throws Exception {
        provisionManager();
        Survey other = surveyService.createSurvey(999L,
                new CreateSurveyRequest("Not yours", null, 1, null));

        mockMvc.perform(get("/surveys/{id}", other.getId())
                        .with(oauth2Login().authorities(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("went wrong"))); // localized error page
    }

    // --- template rendering (catches Thymeleaf errors) ---

    @Test
    void managerCanRenderSurveyListNewFormAndDetail() throws Exception {
        User manager = provisionManager();
        scaleService.create("Agreement", null, scaleValues("No", "Yes"));
        Survey survey = surveyService.createSurvey(manager.getId(),
                new CreateSurveyRequest("Engagement", "How are we doing?", 2, null));
        Section section = surveyService.addSection(survey.getId(), "Wellbeing");
        surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Happy?", QuestionType.SINGLE_CHOICE, List.of("No", "Yes")));

        mockMvc.perform(get("/surveys").with(oauth2Login().authorities(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Engagement")));

        mockMvc.perform(get("/surveys/new").with(oauth2Login().authorities(MANAGER)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/surveys/{id}", survey.getId()).with(oauth2Login().authorities(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Wellbeing")))  // section title
                .andExpect(content().string(containsString("Happy?")))
                .andExpect(content().string(containsString("Agreement"))); // scale offered in the form
    }

    @Test
    void deletingASurveyHidesItFromTheListButKeepsItInTheDatabase() throws Exception {
        User manager = provisionManager();
        Survey survey = surveyService.createSurvey(manager.getId(),
                new CreateSurveyRequest("Doomed", "d", 1, null));

        mockMvc.perform(post("/surveys/{id}/delete", survey.getId())
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/surveys"))
                .andExpect(flash().attribute("deletedSurveyTitle", "Doomed"));

        // Gone from the list, but still retrievable as a soft-deleted row.
        mockMvc.perform(get("/surveys").with(oauth2Login().authorities(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("Doomed"))));
        assertThat(surveyService.findByCreator(manager.getId())).isEmpty();
    }

    @Test
    void deletingASurveyRequiresAdministerRights() throws Exception {
        User manager = provisionManager();
        User member = provisionUser("member");
        Survey survey = surveyService.createSurvey(manager.getId(),
                new CreateSurveyRequest("Protected", "d", 1, null));
        // The member may only edit, not administer, so must not be able to delete it.
        accessService.setPermissions(survey.getId(), member.getId(), Set.of(SurveyPermission.EDIT));

        mockMvc.perform(post("/surveys/{id}/delete", survey.getId())
                        .with(oauth2Login().authorities(PLAIN_USER)
                                .attributes(a -> a.put("sub", "member")))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection()); // redirected back with an inline "forbidden" error
        assertThat(surveyService.get(survey.getId()).getId()).isEqualTo(survey.getId()); // not deleted
    }

    @Test
    void rendersOpenSurveyDetailWithInvitationsAndTurnout() throws Exception {
        User manager = provisionManager();
        Survey survey = surveyService.createSurvey(manager.getId(),
                new CreateSurveyRequest("Engagement", "d", 1, null));
        Section section = surveyService.addSection(survey.getId(), "General");
        surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Happy?", QuestionType.SINGLE_CHOICE, List.of("Yes", "No")));
        surveyService.open(survey.getId());

        mockMvc.perform(get("/surveys/{id}", survey.getId()).with(oauth2Login().authorities(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Send invitations")))
                .andExpect(content().string(containsString("responded"))); // turnout label binds correctly
    }

    @Test
    void managerCanRenderScalesPage() throws Exception {
        provisionManager();
        scaleService.create("Frequency", "How often", scaleValues("Never", "Always"));

        mockMvc.perform(get("/scales").with(oauth2Login().authorities(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Frequency")));
    }

    // --- scale management ---

    @Test
    void invalidScaleRedirectsBackKeepingTheEnteredValues() throws Exception {
        provisionManager();

        MvcResult result = mockMvc.perform(post("/scales")
                        .param("name", "Agreement")
                        .param("values", "OnlyOne") // fewer than two values -> rejected
                        .header("Referer", "http://localhost/scales")
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/scales"))
                .andExpect(flash().attribute("errorMessage", "A scale needs at least two values"))
                .andExpect(flash().attributeExists("submitted"))
                .andReturn();

        // Following the redirect, the form is repopulated from the flashed submission.
        mockMvc.perform(get("/scales").flashAttrs(result.getFlashMap())
                        .with(oauth2Login().authorities(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Agreement")))  // name kept
                .andExpect(content().string(containsString("OnlyOne")));   // value kept
    }

    @Test
    void managerCreatesScale() throws Exception {
        provisionManager();

        mockMvc.perform(post("/scales")
                        .param("name", "Frequency")
                        .param("values", "Never", "Sometimes", "Always")
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(scaleService.findAll()).extracting(Scale::getName).contains("Frequency");
    }

    @Test
    void managerCreatesScaleWithPerOptionScores() throws Exception {
        provisionManager();

        mockMvc.perform(post("/scales")
                        .param("name", "Agreement")
                        .param("values", "Disagree", "Neutral", "Agree")
                        .param("scores", "1", "", "3") // middle option left without a score
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(status().is3xxRedirection());

        Scale scale = scaleService.findAll().stream()
                .filter(s -> s.getName().equals("Agreement")).findFirst().orElseThrow();
        assertThat(scale.getValues())
                .extracting(ScaleValue::getValue, ScaleValue::getScore)
                .containsExactly(tuple("Disagree", 1), tuple("Neutral", null), tuple("Agree", 3));
    }

    @Test
    void nonNumericScoreIsRejected() throws Exception {
        provisionManager();

        mockMvc.perform(post("/scales")
                        .param("name", "Agreement")
                        .param("values", "Disagree", "Agree")
                        .param("scores", "1", "abc")
                        .header("Referer", "http://localhost/scales")
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", "Score \"abc\" is not a whole number"));

        assertThat(scaleService.findAll()).isEmpty();
    }

    // --- recipient management ---

    @Test
    void managerAddsRecipientsByDirectEntry() throws Exception {
        User manager = provisionManager();
        Survey survey = surveyService.createSurvey(manager.getId(),
                new CreateSurveyRequest("Engagement", "d", 1, null));

        mockMvc.perform(post("/surveys/{id}/recipients", survey.getId())
                        .param("emails", "alice@example.com, bob@example.com, alice@example.com")
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/surveys/" + survey.getId() + "/recipients"))
                .andExpect(flash().attribute("importedAdded", 2))
                .andExpect(flash().attribute("importedDuplicates", 1));

        assertThat(recipientService.list(survey.getId()))
                .extracting(SurveyRecipient::getEmail)
                .containsExactly("alice@example.com", "bob@example.com");
    }

    @Test
    void managerUploadsRecipientList() throws Exception {
        User manager = provisionManager();
        Survey survey = surveyService.createSurvey(manager.getId(),
                new CreateSurveyRequest("Engagement", "d", 1, null));
        MockMultipartFile file = new MockMultipartFile("file", "list.txt", "text/plain",
                "carol@example.com\ndave@example.com\n".getBytes());

        mockMvc.perform(multipart("/surveys/{id}/recipients/upload", survey.getId()).file(file)
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("importedAdded", 2));

        assertThat(recipientService.count(survey.getId())).isEqualTo(2);
    }

    @Test
    void managerRendersRecipientsPageWithCurrentList() throws Exception {
        User manager = provisionManager();
        Survey survey = surveyService.createSurvey(manager.getId(),
                new CreateSurveyRequest("Engagement", "d", 1, null));
        recipientService.addRecipients(survey.getId(), "alice@example.com");

        mockMvc.perform(get("/surveys/{id}/recipients", survey.getId())
                        .with(oauth2Login().authorities(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("alice@example.com")));
    }

    // --- per-survey permissions ---

    @Test
    void grantedUserCanEditSharedSurveyButCannotAdministerIt() throws Exception {
        User manager = provisionManager();
        Survey survey = surveyService.createSurvey(manager.getId(),
                new CreateSurveyRequest("Shared", "d", 1, null));
        User grantee = provisionUser("grantee");
        accessService.setPermissions(survey.getId(), grantee.getId(), Set.of(SurveyPermission.EDIT));

        // The grantee is only a plain USER, yet can open the shared survey's management page.
        mockMvc.perform(get("/surveys/{id}", survey.getId())
                        .with(oidcLogin().authorities(PLAIN_USER).idToken(t -> t.subject("grantee"))))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Shared")));

        // But not the permissions screen, which needs ADMINISTER.
        mockMvc.perform(get("/surveys/{id}/permissions", survey.getId())
                        .header("Referer", "http://localhost/surveys/" + survey.getId())
                        .with(oidcLogin().authorities(PLAIN_USER).idToken(t -> t.subject("grantee"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage",
                        "You do not have permission for this action on the survey"));
    }

    @Test
    void editOnlyUserCannotAnalyze() throws Exception {
        User manager = provisionManager();
        Survey survey = surveyService.createSurvey(manager.getId(),
                new CreateSurveyRequest("Shared", "d", 1, null));
        User grantee = provisionUser("grantee");
        accessService.setPermissions(survey.getId(), grantee.getId(), Set.of(SurveyPermission.EDIT));

        mockMvc.perform(get("/surveys/{id}/report", survey.getId())
                        .header("Referer", "http://localhost/surveys/" + survey.getId())
                        .with(oidcLogin().authorities(PLAIN_USER).idToken(t -> t.subject("grantee"))))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage",
                        "You do not have permission for this action on the survey"));
    }

    @Test
    void administratorRendersPermissionsPageAndGrantsRights() throws Exception {
        User manager = provisionManager();
        Survey survey = surveyService.createSurvey(manager.getId(),
                new CreateSurveyRequest("Shared", "d", 1, null));
        User other = provisionUser("other");

        mockMvc.perform(get("/surveys/{id}/permissions", survey.getId())
                        .with(oauth2Login().authorities(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Manager")))     // creator row
                .andExpect(content().string(containsString("Administer")));  // permission label

        mockMvc.perform(post("/surveys/{id}/permissions", survey.getId())
                        .param("userId", String.valueOf(other.getId()))
                        .param("permissions", "EDIT", "ANALYZE")
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/surveys/" + survey.getId() + "/permissions"));

        assertThat(accessService.permissionsFor(survey, other.getId()))
                .containsExactlyInAnyOrder(SurveyPermission.EDIT, SurveyPermission.ANALYZE);
    }

    @Test
    void cannotGrantRightsToTheCreator() throws Exception {
        User manager = provisionManager();
        Survey survey = surveyService.createSurvey(manager.getId(),
                new CreateSurveyRequest("Shared", "d", 1, null));

        mockMvc.perform(post("/surveys/{id}/permissions", survey.getId())
                        .param("userId", String.valueOf(manager.getId()))
                        .param("permissions", "EDIT")
                        .header("Referer", "http://localhost/surveys/" + survey.getId() + "/permissions")
                        .with(oauth2Login().authorities(MANAGER)).with(csrf()))
                .andExpect(flash().attribute("errorMessage",
                        "The survey creator always keeps all rights and cannot be changed"));
        assertThat(accessService.permissionsFor(survey, manager.getId()))
                .containsExactlyInAnyOrder(SurveyPermission.values());
    }

    /** The OAuth2 test principal has provider "test" / subject "user"; provision a matching manager. */
    private User provisionManager() {
        User user = new User("test", "user", "mgr@example.com", "Manager");
        user.addRole(Role.USER);
        user.addRole(Role.SURVEY_MANAGER);
        return userRepository.save(user);
    }

    /** Provisions a plain user with the given OIDC subject (used to log in as a second person). */
    private User provisionUser(String subject) {
        User user = new User("test", subject, subject + "@example.com", "User-" + subject);
        user.addRole(Role.USER);
        return userRepository.save(user);
    }

    private static List<ScaleValue> scaleValues(String... labels) {
        return java.util.Arrays.stream(labels).map(l -> new ScaleValue(l, null)).toList();
    }
}
