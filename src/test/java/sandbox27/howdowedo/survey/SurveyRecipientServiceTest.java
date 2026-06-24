package sandbox27.howdowedo.survey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.common.errors.NotFoundException;
import sandbox27.howdowedo.common.errors.SurveyStateException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SurveyRecipientServiceTest {

    private static final Long MANAGER = 1L;

    @Autowired
    private SurveyService surveyService;
    @Autowired
    private SurveyRecipientService recipientService;

    @MockitoBean
    private InvitationMailSender mailSender;

    @Test
    void parsesMixedSeparatorsValidatesAndLowerCases() {
        Survey survey = draft();

        RecipientImportResult result = recipientService.addRecipients(survey.getId(),
                "Alice@Example.com, bob@example.com\n  carol@example.com ; not-an-email");

        assertThat(result.added()).isEqualTo(3);
        assertThat(result.invalid()).isEqualTo(1);
        assertThat(result.duplicates()).isZero();
        assertThat(recipientService.list(survey.getId()))
                .extracting(SurveyRecipient::getEmail)
                .containsExactly("alice@example.com", "bob@example.com", "carol@example.com");
    }

    @Test
    void skipsDuplicatesWithinTheBatchAndAgainstExistingEntries() {
        Survey survey = draft();
        recipientService.addRecipients(survey.getId(), "a@example.com");

        RecipientImportResult result = recipientService.addRecipients(survey.getId(),
                "A@EXAMPLE.COM b@example.com b@example.com");

        assertThat(result.added()).isEqualTo(1);       // only b@example.com is new
        assertThat(result.duplicates()).isEqualTo(2);  // existing a@ + repeated b@
        assertThat(recipientService.count(survey.getId())).isEqualTo(2);
    }

    @Test
    void removeDeletesASingleRecipient() {
        Survey survey = draft();
        recipientService.addRecipients(survey.getId(), "a@example.com b@example.com");
        SurveyRecipient first = recipientService.list(survey.getId()).get(0);

        recipientService.remove(survey.getId(), first.getId());

        assertThat(recipientService.list(survey.getId()))
                .extracting(SurveyRecipient::getEmail)
                .containsExactly("b@example.com");
    }

    @Test
    void removingAnotherSurveysRecipientFails() {
        Survey survey = draft();
        Survey other = draft();
        recipientService.addRecipients(other.getId(), "a@example.com");
        Long foreignId = recipientService.list(other.getId()).get(0).getId();

        assertThatThrownBy(() -> recipientService.remove(survey.getId(), foreignId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void recipientsCannotBeChangedOnceClosed() {
        Survey survey = draft();
        Section section = surveyService.addSection(survey.getId(), "General");
        surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Happy?", QuestionType.SINGLE_CHOICE, java.util.List.of("Yes", "No")));
        surveyService.open(survey.getId());
        surveyService.close(survey.getId());

        assertThatThrownBy(() -> recipientService.addRecipients(survey.getId(), "a@example.com"))
                .isInstanceOf(SurveyStateException.class)
                .hasMessage("error.recipients.surveyClosed");
    }

    private Survey draft() {
        return surveyService.createSurvey(MANAGER, new CreateSurveyRequest("Engagement", "d", 1, null));
    }
}
