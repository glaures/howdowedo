package sandbox27.howdowedo.report;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.survey.AnswerSubmission;
import sandbox27.howdowedo.survey.CreateSurveyRequest;
import sandbox27.howdowedo.survey.InvitationMailSender;
import sandbox27.howdowedo.survey.NewQuestion;
import sandbox27.howdowedo.survey.QuestionType;
import sandbox27.howdowedo.survey.ResponseSubmission;
import sandbox27.howdowedo.survey.Section;
import sandbox27.howdowedo.survey.Survey;
import sandbox27.howdowedo.survey.SurveyService;
import sandbox27.howdowedo.user.Role;
import sandbox27.howdowedo.user.User;
import sandbox27.howdowedo.user.UserRepository;

import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ReportControllerTest {

    private static final SimpleGrantedAuthority MANAGER = new SimpleGrantedAuthority("ROLE_SURVEY_MANAGER");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private SurveyService surveyService;
    @Autowired
    private UserRepository userRepository;

    @MockitoBean
    private InvitationMailSender mailSender;

    @Test
    void rendersFavorableReportForTheCreator() throws Exception {
        User manager = provisionManager();
        Survey survey = surveyService.createSurvey(manager.getId(), new CreateSurveyRequest("Engagement", "d", 1, null));
        Section section = surveyService.addSection(survey.getId(), "Engagement");
        Long questionId = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Engaged?", QuestionType.SCALE, List.of("No", "Yes"), true,
                        Map.of("No", -1, "Yes", 1))).getId();
        surveyService.open(survey.getId());
        submit(survey.getId(), questionId, "Yes");

        mockMvc.perform(get("/surveys/{id}/report", survey.getId()).with(oauth2Login().authorities(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Engaged?")))
                .andExpect(content().string(containsString("favorbar")));
    }

    @Test
    void rendersSegmentedReportWhenSegmentingByAQuestion() throws Exception {
        User manager = provisionManager();
        Survey survey = surveyService.createSurvey(manager.getId(), new CreateSurveyRequest("Engagement", "d", 1, null));
        Section section = surveyService.addSection(survey.getId(), "General");
        Long gender = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Gender?", QuestionType.SINGLE_CHOICE, List.of("male", "female"))).getId();
        Long team = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Which team?", QuestionType.SINGLE_CHOICE, List.of("Red", "Blue"))).getId();
        surveyService.open(survey.getId());
        submit(survey.getId(), Map.of(gender, "male", team, "Red"));
        submit(survey.getId(), Map.of(gender, "female", team, "Blue"));

        mockMvc.perform(get("/surveys/{id}/report", survey.getId()).param("segmentBy", String.valueOf(gender))
                        .with(oauth2Login().authorities(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("segmented by Gender?")))
                .andExpect(content().string(containsString("male")))
                .andExpect(content().string(containsString("female")));
    }

    @Test
    void filtersTheReportToASingleSegmentValue() throws Exception {
        User manager = provisionManager();
        Survey survey = surveyService.createSurvey(manager.getId(), new CreateSurveyRequest("Engagement", "d", 1, null));
        Section section = surveyService.addSection(survey.getId(), "General");
        Long gender = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Gender?", QuestionType.SINGLE_CHOICE, List.of("male", "female"))).getId();
        Long team = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Which team?", QuestionType.SINGLE_CHOICE, List.of("Red", "Blue"))).getId();
        surveyService.open(survey.getId());
        submit(survey.getId(), Map.of(gender, "male", team, "Red"));
        submit(survey.getId(), Map.of(gender, "female", team, "Blue"));

        mockMvc.perform(get("/surveys/{id}/report", survey.getId())
                        .param("segmentBy", String.valueOf(gender)).param("segmentValue", "male")
                        .with(oauth2Login().authorities(MANAGER)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("showing only male")))
                // Only the single "male" response is counted, not both.
                .andExpect(content().string(containsString("1 response(s)")));
    }

    private void submit(Long surveyId, Long questionId, String value) {
        submit(surveyId, Map.of(questionId, value));
    }

    private void submit(Long surveyId, Map<Long, String> answers) {
        surveyService.distributeInvitations(surveyId, List.of("p@example.com"));
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(mailSender, atLeastOnce()).sendInvitation(anyString(), anyString(), url.capture());
        String link = url.getAllValues().get(url.getAllValues().size() - 1);
        String code = link.substring(link.indexOf("code=") + "code=".length());
        List<AnswerSubmission> submissions = answers.entrySet().stream()
                .map(entry -> new AnswerSubmission(entry.getKey(), List.of(entry.getValue())))
                .toList();
        surveyService.submitResponse(surveyId, code, new ResponseSubmission(submissions));
    }

    private User provisionManager() {
        User user = new User("test", "user", "mgr@example.com", "Manager");
        user.addRole(Role.USER);
        user.addRole(Role.SURVEY_MANAGER);
        return userRepository.save(user);
    }
}
