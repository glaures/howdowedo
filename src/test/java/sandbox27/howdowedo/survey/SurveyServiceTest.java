package sandbox27.howdowedo.survey;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.common.errors.SurveyStateException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SurveyServiceTest {

    private static final Long MANAGER = 1L;

    @Autowired
    private SurveyService surveyService;
    @Autowired
    private SurveyResponseRepository responses;
    @Autowired
    private SurveyAccessCodeRepository accessCodes;

    @MockitoBean
    private InvitationMailSender mailSender;

    @Test
    void createdSurveyStartsAsDraft() {
        Survey survey = surveyService.createSurvey(MANAGER, yesNo(2));

        assertThat(survey.getStatus()).isEqualTo(SurveyStatus.DRAFT);
        assertThat(survey.getQuestions()).hasSize(1);
    }

    @Test
    void distributionStoresOnlyHashedCodesAndMailsThePlaintextLink() {
        Survey survey = surveyService.createSurvey(MANAGER, yesNo(1));

        int sent = surveyService.distributeInvitations(survey.getId(),
                List.of("a@example.com", "b@example.com", "c@example.com"));

        assertThat(sent).isEqualTo(3);
        assertThat(accessCodes.countBySurveyId(survey.getId())).isEqualTo(3);

        // The plaintext code only ever appears in the email link; the DB holds its 64-char SHA-256.
        String code = sentCodes().get(0);
        assertThat(accessCodes.findBySurveyIdAndCodeHash(survey.getId(), AccessCodes.hash(code))).isPresent();
        assertThat(accessCodes.findBySurveyIdAndCodeHash(survey.getId(), code)).isEmpty();
    }

    @Test
    void validCodeSubmitsAnUnlinkedResponseAndIsConsumed() {
        Survey survey = openSurvey(yesNo(1));
        String code = inviteOne(survey.getId());
        Long questionId = survey.getQuestions().get(0).getId();

        surveyService.submitResponse(survey.getId(), code, answer(questionId, "Yes"));

        assertThat(responses.findBySurveyId(survey.getId())).hasSize(1);
        assertThat(surveyService.turnout(survey.getId())).isEqualTo(new SurveyTurnout(1, 1));
    }

    @Test
    void unknownCodeIsRejected() {
        Survey survey = openSurvey(yesNo(1));
        Long questionId = survey.getQuestions().get(0).getId();

        assertThatThrownBy(() -> surveyService.submitResponse(survey.getId(), "not-a-real-code",
                answer(questionId, "Yes")))
                .isInstanceOf(SurveyStateException.class);
    }

    @Test
    void aCodeCannotBeUsedTwice() {
        Survey survey = openSurvey(yesNo(1));
        String code = inviteOne(survey.getId());
        Long questionId = survey.getQuestions().get(0).getId();
        surveyService.submitResponse(survey.getId(), code, answer(questionId, "Yes"));

        assertThatThrownBy(() -> surveyService.submitResponse(survey.getId(), code, answer(questionId, "No")))
                .isInstanceOf(SurveyStateException.class);
    }

    @Test
    void cannotSubmitWhenNotOpen() {
        Survey survey = surveyService.createSurvey(MANAGER, yesNo(1)); // still DRAFT
        String code = inviteOne(survey.getId());
        Long questionId = survey.getQuestions().get(0).getId();

        assertThatThrownBy(() -> surveyService.submitResponse(survey.getId(), code, answer(questionId, "Yes")))
                .isInstanceOf(SurveyStateException.class);
    }

    @Test
    void resultsAreBlockedBelowAnonymityThreshold() {
        Survey survey = openSurvey(yesNo(3)); // needs 3 responses
        submitWith(survey, "Yes");

        assertThatThrownBy(() -> surveyService.results(survey.getId()))
                .isInstanceOf(SurveyStateException.class);
    }

    @Test
    void resultsAggregateChoiceCountsOnceThresholdMet() {
        Survey survey = openSurvey(yesNo(2));
        submitWith(survey, "Yes");
        submitWith(survey, "No");

        SurveyResults results = surveyService.results(survey.getId());

        assertThat(results.responseCount()).isEqualTo(2);
        assertThat(results.questions().get(0).optionCounts())
                .containsEntry("Yes", 1L)
                .containsEntry("No", 1L);
    }

    // --- helpers ---

    private CreateSurveyRequest yesNo(int minResponses) {
        return new CreateSurveyRequest("Engagement", "How are we doing?", minResponses,
                List.of(new NewQuestion("Happy at work?", QuestionType.SINGLE_CHOICE, List.of("Yes", "No"))));
    }

    private Survey openSurvey(CreateSurveyRequest request) {
        Survey survey = surveyService.createSurvey(MANAGER, request);
        return surveyService.open(survey.getId());
    }

    private ResponseSubmission answer(Long questionId, String value) {
        return new ResponseSubmission(List.of(new AnswerSubmission(questionId, List.of(value))));
    }

    /** Distributes one invitation and returns the plaintext code from the most recent email link. */
    private String inviteOne(Long surveyId) {
        surveyService.distributeInvitations(surveyId, List.of("p@example.com"));
        List<String> codes = sentCodes();
        return codes.get(codes.size() - 1);
    }

    /** Submits one fresh response to an open survey with the given answer value. */
    private void submitWith(Survey survey, String value) {
        String code = inviteOne(survey.getId());
        surveyService.submitResponse(survey.getId(), code, answer(survey.getQuestions().get(0).getId(), value));
    }

    /** All plaintext codes captured from invitation emails sent so far in this test. */
    private List<String> sentCodes() {
        ArgumentCaptor<String> urls = ArgumentCaptor.forClass(String.class);
        verify(mailSender, atLeastOnce()).sendInvitation(anyString(), anyString(), urls.capture());
        return urls.getAllValues().stream()
                .map(url -> url.substring(url.indexOf("code=") + "code=".length()))
                .toList();
    }
}
