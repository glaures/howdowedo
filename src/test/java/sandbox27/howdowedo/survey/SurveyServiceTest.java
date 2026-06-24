package sandbox27.howdowedo.survey;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.common.errors.NotFoundException;
import sandbox27.howdowedo.common.errors.SurveyStateException;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

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
    private SurveyRecipientService recipientService;
    @Autowired
    private SurveyResponseRepository responses;
    @Autowired
    private SurveyAccessCodeRepository accessCodes;

    @MockitoBean
    private InvitationMailSender mailSender;

    @Test
    void createdSurveyStartsAsDraftAndEmpty() {
        Survey survey = surveyService.createSurvey(MANAGER, request(2));

        assertThat(survey.getStatus()).isEqualTo(SurveyStatus.DRAFT);
        assertThat(survey.getSections()).isEmpty();
        assertThat(survey.getQuestions()).isEmpty();
    }

    @Test
    void distributionStoresOnlyHashedCodesAndMailsThePlaintextLink() {
        Survey survey = openSurvey(1);

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
        Survey survey = openSurvey(1);
        String code = inviteOne(survey.getId());
        Long questionId = survey.getQuestions().get(0).getId();

        surveyService.submitResponse(survey.getId(), code, answer(questionId, "Yes"));

        assertThat(responses.findBySurveyId(survey.getId())).hasSize(1);
        assertThat(surveyService.turnout(survey.getId())).isEqualTo(new SurveyTurnout(1, 1));
    }

    @Test
    void unknownCodeIsRejected() {
        Survey survey = openSurvey(1);
        Long questionId = survey.getQuestions().get(0).getId();

        assertThatThrownBy(() -> surveyService.submitResponse(survey.getId(), "not-a-real-code",
                answer(questionId, "Yes")))
                .isInstanceOf(SurveyStateException.class);
    }

    @Test
    void aCodeCannotBeUsedTwice() {
        Survey survey = openSurvey(1);
        String code = inviteOne(survey.getId());
        Long questionId = survey.getQuestions().get(0).getId();
        surveyService.submitResponse(survey.getId(), code, answer(questionId, "Yes"));

        assertThatThrownBy(() -> surveyService.submitResponse(survey.getId(), code, answer(questionId, "No")))
                .isInstanceOf(SurveyStateException.class);
    }

    @Test
    void cannotSubmitAfterSurveyClosed() {
        Survey survey = openSurvey(1);
        String code = inviteOne(survey.getId());
        surveyService.close(survey.getId());
        Long questionId = survey.getQuestions().get(0).getId();

        assertThatThrownBy(() -> surveyService.submitResponse(survey.getId(), code, answer(questionId, "Yes")))
                .isInstanceOf(SurveyStateException.class);
    }

    @Test
    void cannotOpenSurveyWithoutQuestions() {
        Survey survey = surveyService.createSurvey(MANAGER, request(1));
        surveyService.addSection(survey.getId(), "General");

        assertThatThrownBy(() -> surveyService.open(survey.getId()))
                .isInstanceOf(SurveyStateException.class)
                .hasMessage("error.survey.noQuestions");
    }

    @Test
    void invitationsRequireAnOpenSurvey() {
        Survey survey = yesNoSurvey(1); // DRAFT

        assertThatThrownBy(() -> surveyService.distributeInvitations(survey.getId(), List.of("a@example.com")))
                .isInstanceOf(SurveyStateException.class)
                .hasMessage("error.survey.mustBeOpenToInvite");
    }

    @Test
    void invitationsRequireRecipients() {
        Survey survey = openSurvey(1);

        assertThatThrownBy(() -> surveyService.distributeInvitations(survey.getId(), List.of()))
                .isInstanceOf(SurveyStateException.class)
                .hasMessage("error.invitations.noRecipients");
    }

    @Test
    void inviteRecipientsSendsToPendingOnceAndMarksThemInvited() {
        Survey survey = openSurvey(1);
        recipientService.addRecipients(survey.getId(), "a@example.com b@example.com");

        int first = surveyService.inviteRecipients(survey.getId());
        assertThat(first).isEqualTo(2);
        assertThat(recipientService.invitedCount(survey.getId())).isEqualTo(2);
        assertThat(accessCodes.countBySurveyId(survey.getId())).isEqualTo(2);

        // Adding one more recipient and re-sending only reaches the new address.
        recipientService.addRecipients(survey.getId(), "c@example.com");
        int second = surveyService.inviteRecipients(survey.getId());
        assertThat(second).isEqualTo(1);
        assertThat(accessCodes.countBySurveyId(survey.getId())).isEqualTo(3);
    }

    @Test
    void invitingWithoutRecipientsFails() {
        Survey survey = openSurvey(1);

        assertThatThrownBy(() -> surveyService.inviteRecipients(survey.getId()))
                .isInstanceOf(SurveyStateException.class)
                .hasMessage("error.invitations.noRecipients");
    }

    @Test
    void invitingWhenEveryoneIsAlreadyInvitedFails() {
        Survey survey = openSurvey(1);
        recipientService.addRecipients(survey.getId(), "a@example.com");
        surveyService.inviteRecipients(survey.getId());

        assertThatThrownBy(() -> surveyService.inviteRecipients(survey.getId()))
                .isInstanceOf(SurveyStateException.class)
                .hasMessage("error.invitations.allInvited");
    }

    @Test
    void sectionAnswersAreSavedResumableAndNotCountedUntilCompletion() {
        Survey survey = openSurvey(1);
        String code = inviteOne(survey.getId());
        Long sectionId = survey.getSections().get(0).getId();
        Long questionId = survey.getQuestions().get(0).getId();

        surveyService.saveSection(survey.getId(), code, sectionId, Map.of(questionId, List.of("Yes")), Map.of());

        // Saved progress is retrievable and the code is not yet consumed (still resumable).
        assertThat(surveyService.savedAnswers(survey.getId(), code))
                .containsEntry(questionId, List.of("Yes"));
        assertThat(surveyService.turnout(survey.getId())).isEqualTo(new SurveyTurnout(1, 0));
        surveyService.loadForParticipation(survey.getId(), code); // does not throw

        surveyService.completeParticipation(survey.getId(), code);

        assertThat(surveyService.turnout(survey.getId())).isEqualTo(new SurveyTurnout(1, 1));
        assertThatThrownBy(() -> surveyService.loadForParticipation(survey.getId(), code))
                .isInstanceOf(SurveyStateException.class)
                .hasMessage("error.survey.codeUsed");
    }

    @Test
    void resavingASectionReplacesItsPreviousAnswers() {
        Survey survey = openSurvey(1);
        String code = inviteOne(survey.getId());
        Long sectionId = survey.getSections().get(0).getId();
        Long questionId = survey.getQuestions().get(0).getId();

        surveyService.saveSection(survey.getId(), code, sectionId, Map.of(questionId, List.of("Yes")), Map.of());
        surveyService.saveSection(survey.getId(), code, sectionId, Map.of(questionId, List.of("No")), Map.of());

        assertThat(surveyService.savedAnswers(survey.getId(), code))
                .containsEntry(questionId, List.of("No"));
    }

    @Test
    void inProgressResponsesDoNotCountTowardsResults() {
        Survey survey = openSurvey(1);
        String code = inviteOne(survey.getId());
        Long sectionId = survey.getSections().get(0).getId();
        Long questionId = survey.getQuestions().get(0).getId();
        surveyService.saveSection(survey.getId(), code, sectionId, Map.of(questionId, List.of("Yes")), Map.of());

        // Saved but not completed -> still below the (1) threshold.
        assertThatThrownBy(() -> surveyService.results(survey.getId()))
                .isInstanceOf(SurveyStateException.class)
                .hasMessage("error.survey.notEnoughResponses");

        surveyService.completeParticipation(survey.getId(), code);
        assertThat(surveyService.results(survey.getId()).responseCount()).isEqualTo(1);
    }

    @Test
    void responsesAreRejectedAfterTheEndDate() {
        Survey survey = surveyService.createSurvey(MANAGER,
                new CreateSurveyRequest("Past", "d", 1, LocalDate.now().minusDays(1)));
        Section section = surveyService.addSection(survey.getId(), "General");
        surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Happy?", QuestionType.SINGLE_CHOICE, List.of("Yes", "No")));
        surveyService.open(survey.getId());
        String code = inviteOne(survey.getId());
        Long questionId = surveyService.get(survey.getId()).getQuestions().get(0).getId();

        assertThatThrownBy(() -> surveyService.submitResponse(survey.getId(), code, answer(questionId, "Yes")))
                .isInstanceOf(SurveyStateException.class)
                .hasMessage("error.survey.ended");
    }

    @Test
    void updatesEndDate() {
        Survey survey = surveyService.createSurvey(MANAGER, request(1));
        LocalDate end = LocalDate.now().plusDays(7);

        surveyService.updateEndDate(survey.getId(), end);

        assertThat(surveyService.get(survey.getId()).getEndDate()).isEqualTo(end);
    }

    @Test
    void addsSectionsInInsertionOrder() {
        Survey survey = surveyService.createSurvey(MANAGER, request(1));
        surveyService.addSection(survey.getId(), "Intro");
        surveyService.addSection(survey.getId(), "Details");

        assertThat(surveyService.get(survey.getId()).getSections())
                .extracting(Section::getTitle).containsExactly("Intro", "Details");
    }

    @Test
    void blankSectionTitleIsRejected() {
        Survey survey = surveyService.createSurvey(MANAGER, request(1));

        assertThatThrownBy(() -> surveyService.addSection(survey.getId(), "   "))
                .isInstanceOf(SurveyStateException.class)
                .hasMessage("error.section.titleRequired");
    }

    @Test
    void addsQuestionWithSnapshottedOptionsToSection() {
        Survey survey = surveyService.createSurvey(MANAGER, request(2));
        Section section = surveyService.addSection(survey.getId(), "General");

        surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Happy?", QuestionType.SINGLE_CHOICE, List.of("Yes", "No")));

        Survey reloaded = surveyService.get(survey.getId());
        assertThat(reloaded.getQuestions()).hasSize(1);
        assertThat(reloaded.getQuestions().get(0).getOptions()).containsExactly("Yes", "No");
    }

    @Test
    void choiceQuestionWithoutOptionsIsRejected() {
        Survey survey = surveyService.createSurvey(MANAGER, request(2));
        Section section = surveyService.addSection(survey.getId(), "General");

        assertThatThrownBy(() -> surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Happy?", QuestionType.SINGLE_CHOICE, List.of())))
                .isInstanceOf(SurveyStateException.class)
                .hasMessage("error.survey.optionsRequired");
    }

    @Test
    void addingQuestionToUnknownSectionIsRejected() {
        Survey survey = surveyService.createSurvey(MANAGER, request(1));

        assertThatThrownBy(() -> surveyService.addQuestion(survey.getId(), 999L,
                new NewQuestion("Q", QuestionType.TEXT, List.of())))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("error.section.notFound");
    }

    @Test
    void cannotAddOrRemoveOnNonDraftSurvey() {
        Survey survey = openSurvey(1); // OPEN
        Long sectionId = survey.getSections().get(0).getId();
        Long questionId = survey.getQuestions().get(0).getId();

        assertThatThrownBy(() -> surveyService.addSection(survey.getId(), "Late"))
                .isInstanceOf(SurveyStateException.class).hasMessage("error.survey.notDraft");
        assertThatThrownBy(() -> surveyService.addQuestion(survey.getId(), sectionId,
                new NewQuestion("More?", QuestionType.TEXT, List.of())))
                .isInstanceOf(SurveyStateException.class).hasMessage("error.survey.notDraft");
        assertThatThrownBy(() -> surveyService.removeQuestion(survey.getId(), questionId))
                .isInstanceOf(SurveyStateException.class).hasMessage("error.survey.notDraft");
    }

    @Test
    void removesQuestionFromSection() {
        Survey survey = yesNoSurvey(1);
        Long questionId = survey.getQuestions().get(0).getId();

        surveyService.removeQuestion(survey.getId(), questionId);

        assertThat(surveyService.get(survey.getId()).getQuestions()).isEmpty();
    }

    @Test
    void updatesQuestionTextTypeAndOptions() {
        Survey survey = yesNoSurvey(1);
        Long questionId = survey.getQuestions().get(0).getId();

        surveyService.updateQuestion(survey.getId(), questionId,
                new NewQuestion("Engaged?", QuestionType.SCALE, List.of("Low", "Mid", "High")));

        Question updated = surveyService.get(survey.getId()).getQuestions().get(0);
        assertThat(updated.getText()).isEqualTo("Engaged?");
        assertThat(updated.getType()).isEqualTo(QuestionType.SCALE);
        assertThat(updated.getOptions()).containsExactly("Low", "Mid", "High");
    }

    @Test
    void updatingChoiceQuestionWithoutOptionsIsRejected() {
        Survey survey = yesNoSurvey(1);
        Long questionId = survey.getQuestions().get(0).getId();

        assertThatThrownBy(() -> surveyService.updateQuestion(survey.getId(), questionId,
                new NewQuestion("Happy?", QuestionType.SINGLE_CHOICE, List.of())))
                .isInstanceOf(SurveyStateException.class)
                .hasMessage("error.survey.optionsRequired");
    }

    @Test
    void updatingUnknownQuestionIsRejected() {
        Survey survey = yesNoSurvey(1);

        assertThatThrownBy(() -> surveyService.updateQuestion(survey.getId(), 999L,
                new NewQuestion("Q", QuestionType.TEXT, List.of())))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("error.question.notFound");
    }

    @Test
    void cannotUpdateQuestionOnNonDraftSurvey() {
        Survey survey = openSurvey(1); // OPEN
        Long questionId = survey.getQuestions().get(0).getId();

        assertThatThrownBy(() -> surveyService.updateQuestion(survey.getId(), questionId,
                new NewQuestion("Q", QuestionType.TEXT, List.of())))
                .isInstanceOf(SurveyStateException.class)
                .hasMessage("error.survey.notDraft");
    }

    @Test
    void removingSectionRemovesItsQuestions() {
        Survey survey = yesNoSurvey(1);
        Long sectionId = survey.getSections().get(0).getId();

        surveyService.removeSection(survey.getId(), sectionId);

        Survey reloaded = surveyService.get(survey.getId());
        assertThat(reloaded.getSections()).isEmpty();
        assertThat(reloaded.getQuestions()).isEmpty();
    }

    @Test
    void listsSurveysByCreator() {
        yesNoSurvey(1);
        yesNoSurvey(1);
        yesNoSurvey(999L, 1);

        assertThat(surveyService.findByCreator(MANAGER)).hasSize(2);
        assertThat(surveyService.findByCreator(999L)).hasSize(1);
    }

    @Test
    void resultsAreBlockedBelowAnonymityThreshold() {
        Survey survey = openSurvey(3); // needs 3 responses
        submitWith(survey, "Yes");

        assertThatThrownBy(() -> surveyService.results(survey.getId()))
                .isInstanceOf(SurveyStateException.class);
    }

    @Test
    void resultsAggregateChoiceCountsOnceThresholdMet() {
        Survey survey = openSurvey(2);
        submitWith(survey, "Yes");
        submitWith(survey, "No");

        SurveyResults results = surveyService.results(survey.getId());

        assertThat(results.responseCount()).isEqualTo(2);
        assertThat(results.questions().get(0).optionCounts())
                .containsEntry("Yes", 1L)
                .containsEntry("No", 1L);
    }

    @Test
    void questionsAllowCommentsByDefault() {
        Survey survey = yesNoSurvey(1);

        assertThat(survey.getQuestions().get(0).isAllowsComments()).isTrue();
    }

    @Test
    void updateCanDisableComments() {
        Survey survey = yesNoSurvey(1);
        Long questionId = survey.getQuestions().get(0).getId();

        surveyService.updateQuestion(survey.getId(), questionId,
                new NewQuestion("Happy?", QuestionType.SINGLE_CHOICE, List.of("Yes", "No"), false));

        assertThat(surveyService.get(survey.getId()).getQuestions().get(0).isAllowsComments()).isFalse();
    }

    @Test
    void savesCommentsAndAggregatesThemInResults() {
        Survey survey = openSurvey(1);
        String code = inviteOne(survey.getId());
        Long sectionId = survey.getSections().get(0).getId();
        Long questionId = survey.getQuestions().get(0).getId();

        surveyService.saveSection(survey.getId(), code, sectionId,
                Map.of(questionId, List.of("Yes")), Map.of(questionId, "Needs work"));

        // Comments are kept apart from the chosen values.
        assertThat(surveyService.savedAnswers(survey.getId(), code)).containsEntry(questionId, List.of("Yes"));
        assertThat(surveyService.savedComments(survey.getId(), code)).containsEntry(questionId, "Needs work");

        surveyService.completeParticipation(survey.getId(), code);
        QuestionResult result = surveyService.results(survey.getId()).questions().get(0);
        assertThat(result.optionCounts()).containsExactly(Map.entry("Yes", 1L)); // comment not counted
        assertThat(result.comments()).containsExactly("Needs work");
    }

    @Test
    void blankCommentRemovesAnyPreviousComment() {
        Survey survey = openSurvey(1);
        String code = inviteOne(survey.getId());
        Long sectionId = survey.getSections().get(0).getId();
        Long questionId = survey.getQuestions().get(0).getId();

        surveyService.saveSection(survey.getId(), code, sectionId,
                Map.of(questionId, List.of("Yes")), Map.of(questionId, "First"));
        surveyService.saveSection(survey.getId(), code, sectionId,
                Map.of(questionId, List.of("Yes")), Map.of(questionId, "   "));

        assertThat(surveyService.savedComments(survey.getId(), code)).doesNotContainKey(questionId);
    }

    @Test
    void commentsAreIgnoredWhenQuestionDisallowsThem() {
        Survey survey = surveyService.createSurvey(MANAGER, request(1));
        Section section = surveyService.addSection(survey.getId(), "General");
        Long questionId = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Happy?", QuestionType.SINGLE_CHOICE, List.of("Yes", "No"), false)).getId();
        surveyService.open(survey.getId());
        String code = inviteOne(survey.getId());

        surveyService.saveSection(survey.getId(), code, section.getId(),
                Map.of(questionId, List.of("Yes")), Map.of(questionId, "Should be ignored"));

        assertThat(surveyService.savedComments(survey.getId(), code)).isEmpty();
    }

    // --- helpers ---

    private CreateSurveyRequest request(int minResponses) {
        return new CreateSurveyRequest("Engagement", "How are we doing?", minResponses, null);
    }

    /** Creates a DRAFT survey with one section holding a single yes/no question. */
    private Survey yesNoSurvey(int minResponses) {
        return yesNoSurvey(MANAGER, minResponses);
    }

    private Survey yesNoSurvey(Long owner, int minResponses) {
        Survey survey = surveyService.createSurvey(owner, request(minResponses));
        Section section = surveyService.addSection(survey.getId(), "General");
        surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Happy at work?", QuestionType.SINGLE_CHOICE, List.of("Yes", "No")));
        return surveyService.get(survey.getId());
    }

    private Survey openSurvey(int minResponses) {
        Survey survey = yesNoSurvey(minResponses);
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
