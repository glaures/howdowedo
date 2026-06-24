package sandbox27.howdowedo.survey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SampleResponseServiceTest {

    private static final Long MANAGER = 1L;

    @Autowired
    private SurveyService surveyService;
    @Autowired
    private SampleResponseService sampleResponses;
    @Autowired
    private SurveyResponseRepository responses;

    @MockitoBean
    private InvitationMailSender mailSender;

    @Test
    void generatesCompletedResponsesWithAnswersForEveryQuestion() {
        Survey survey = surveyService.createSurvey(MANAGER, new CreateSurveyRequest("S", "d", 1, null));
        Section section = surveyService.addSection(survey.getId(), "Engagement");
        surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Engaged?", QuestionType.SCALE, List.of("No", "Maybe", "Yes"), true,
                        Map.of("No", -1, "Maybe", 0, "Yes", 1)));
        surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Anything else?", QuestionType.TEXT, List.of()));

        int created = sampleResponses.generate(survey.getId(), 25);

        assertThat(created).isEqualTo(25);
        assertThat(responses.countBySurveyIdAndCompletedTrue(survey.getId())).isEqualTo(25);
        // Enough responses now exist for the report to unlock (threshold was 1).
        assertThat(surveyService.results(survey.getId()).responseCount()).isEqualTo(25);
    }

    @Test
    void surveysWithoutResponsesExcludesAlreadyFilledOnes() {
        Survey empty = surveyService.createSurvey(MANAGER, new CreateSurveyRequest("Empty", "d", 1, null));
        Section section = surveyService.addSection(empty.getId(), "S");
        surveyService.addQuestion(empty.getId(), section.getId(),
                new NewQuestion("Q?", QuestionType.SINGLE_CHOICE, List.of("a", "b")));

        assertThat(sampleResponses.surveysWithoutResponses()).contains(empty.getId());

        sampleResponses.generate(empty.getId(), 3);
        assertThat(sampleResponses.surveysWithoutResponses()).doesNotContain(empty.getId());
    }
}
