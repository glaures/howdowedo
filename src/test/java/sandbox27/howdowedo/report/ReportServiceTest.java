package sandbox27.howdowedo.report;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.common.errors.SurveyStateException;
import sandbox27.howdowedo.survey.AnswerSubmission;
import sandbox27.howdowedo.survey.CreateSurveyRequest;
import sandbox27.howdowedo.survey.InvitationMailSender;
import sandbox27.howdowedo.survey.NewQuestion;
import sandbox27.howdowedo.survey.QuestionType;
import sandbox27.howdowedo.survey.ResponseSubmission;
import sandbox27.howdowedo.survey.Section;
import sandbox27.howdowedo.survey.Survey;
import sandbox27.howdowedo.survey.SurveyService;

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
class ReportServiceTest {

    private static final Long MANAGER = 1L;

    @Autowired
    private SurveyService surveyService;
    @Autowired
    private ReportService reportService;

    @MockitoBean
    private InvitationMailSender mailSender;

    @Test
    void reportGroupsQuestionsBySection() {
        Survey survey = surveyService.createSurvey(MANAGER, new CreateSurveyRequest("S", "d", 1, null));
        Section first = surveyService.addSection(survey.getId(), "Engagement");
        Section second = surveyService.addSection(survey.getId(), "Workplace");
        surveyService.addQuestion(survey.getId(), first.getId(),
                new NewQuestion("Engaged?", QuestionType.SCALE, List.of("No", "Yes"), true, Map.of("No", -1, "Yes", 1)));
        Long q2 = surveyService.addQuestion(survey.getId(), second.getId(),
                new NewQuestion("Tidy desk?", QuestionType.SINGLE_CHOICE, List.of("No", "Yes"))).getId();
        surveyService.open(survey.getId());
        submit(survey.getId(), q2, "Yes");

        SurveyReport report = reportService.build(survey.getId());
        assertThat(report.sections()).extracting(SectionReport::title)
                .containsExactly("Engagement", "Workplace");
        assertThat(report.sections().get(0).questions()).extracting(QuestionReport::text)
                .containsExactly("Engaged?");
        assertThat(report.sections().get(1).questions()).extracting(QuestionReport::text)
                .containsExactly("Tidy desk?");
    }

    @Test
    void signedScaleProducesFavorableScore() {
        Survey survey = surveyService.createSurvey(MANAGER, new CreateSurveyRequest("S", "d", 1, null));
        Section section = surveyService.addSection(survey.getId(), "Engagement");
        Long questionId = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Engaged?", QuestionType.SCALE, List.of("Disagree", "Neutral", "Agree"), true,
                        Map.of("Disagree", -1, "Neutral", 0, "Agree", 1))).getId();
        surveyService.open(survey.getId());

        submit(survey.getId(), questionId, "Agree");
        submit(survey.getId(), questionId, "Agree");
        submit(survey.getId(), questionId, "Neutral");
        submit(survey.getId(), questionId, "Disagree");

        QuestionReport q = reportService.build(survey.getId()).questions().get(0);
        assertThat(q.visualization()).isEqualTo(ReportVisualization.FAVORABLE);
        FavorableScore f = q.favorable();
        assertThat(f.supporters()).isEqualTo(2);
        assertThat(f.neutral()).isEqualTo(1);
        assertThat(f.detractors()).isEqualTo(1);
        assertThat(f.favorablePercent()).isEqualTo(50);
        assertThat(f.detractorPercent()).isEqualTo(25);
        assertThat(f.neutralPercent()).isEqualTo(25); // shares always add up to 100
    }

    @Test
    void allPositiveScaleBucketsAroundItsMidpoint() {
        Survey survey = surveyService.createSurvey(MANAGER, new CreateSurveyRequest("S", "d", 1, null));
        Section section = surveyService.addSection(survey.getId(), "General");
        Long questionId = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("How often?", QuestionType.SCALE, List.of("Never", "Sometimes", "Always"), true,
                        Map.of("Never", 1, "Sometimes", 2, "Always", 3))).getId(); // midpoint 2 -> Sometimes neutral
        surveyService.open(survey.getId());

        submit(survey.getId(), questionId, "Always");
        submit(survey.getId(), questionId, "Sometimes");
        submit(survey.getId(), questionId, "Never");

        QuestionReport q = reportService.build(survey.getId()).questions().get(0);
        assertThat(q.visualization()).isEqualTo(ReportVisualization.FAVORABLE);
        FavorableScore f = q.favorable();
        assertThat(f.supporters()).isEqualTo(1);
        assertThat(f.neutral()).isEqualTo(1);
        assertThat(f.detractors()).isEqualTo(1);
    }

    @Test
    void unscoredChoiceQuestionFallsBackToDistribution() {
        Survey survey = surveyService.createSurvey(MANAGER, new CreateSurveyRequest("S", "d", 1, null));
        Section section = surveyService.addSection(survey.getId(), "General");
        Long questionId = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Which team?", QuestionType.SINGLE_CHOICE, List.of("Red", "Blue"))).getId();
        surveyService.open(survey.getId());

        submit(survey.getId(), questionId, "Red");
        submit(survey.getId(), questionId, "Blue");

        QuestionReport q = reportService.build(survey.getId()).questions().get(0);
        assertThat(q.visualization()).isEqualTo(ReportVisualization.DISTRIBUTION);
        assertThat(q.distribution()).extracting(DistributionEntry::label, DistributionEntry::count)
                .contains(org.assertj.core.api.Assertions.tuple("Red", 1L),
                        org.assertj.core.api.Assertions.tuple("Blue", 1L));
    }

    @Test
    void textQuestionListsAnswers() {
        Survey survey = surveyService.createSurvey(MANAGER, new CreateSurveyRequest("S", "d", 1, null));
        Section section = surveyService.addSection(survey.getId(), "Open");
        Long questionId = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Anything else?", QuestionType.TEXT, List.of())).getId();
        surveyService.open(survey.getId());

        submit(survey.getId(), questionId, "More fruit please");

        QuestionReport q = reportService.build(survey.getId()).questions().get(0);
        assertThat(q.visualization()).isEqualTo(ReportVisualization.TEXT);
        assertThat(q.textAnswers()).containsExactly("More fruit please");
    }

    @Test
    void reportIsBlockedBelowAnonymityThreshold() {
        Survey survey = surveyService.createSurvey(MANAGER, new CreateSurveyRequest("S", "d", 5, null));
        Section section = surveyService.addSection(survey.getId(), "Engagement");
        Long questionId = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Engaged?", QuestionType.SCALE, List.of("No", "Yes"), true,
                        Map.of("No", -1, "Yes", 1))).getId();
        surveyService.open(survey.getId());
        submit(survey.getId(), questionId, "Yes");

        assertThatThrownBy(() -> reportService.build(survey.getId()))
                .isInstanceOf(SurveyStateException.class);
    }

    @Test
    void segmentsAnswersByTheChosenQuestion() {
        Survey survey = surveyService.createSurvey(MANAGER, new CreateSurveyRequest("S", "d", 1, null));
        Section section = surveyService.addSection(survey.getId(), "General");
        Long gender = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Gender?", QuestionType.SINGLE_CHOICE, List.of("m", "w"))).getId();
        Long team = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Which team?", QuestionType.SINGLE_CHOICE, List.of("Red", "Blue"))).getId();
        surveyService.open(survey.getId());
        submit(survey.getId(), Map.of(gender, "m", team, "Red"));
        submit(survey.getId(), Map.of(gender, "m", team, "Blue"));
        submit(survey.getId(), Map.of(gender, "w", team, "Red"));

        SegmentedReport report = reportService.buildSegmented(survey.getId(), gender);

        assertThat(report.segments()).extracting(SegmentedReport.Segment::label, SegmentedReport.Segment::responseCount)
                .containsExactly(org.assertj.core.api.Assertions.tuple("m", 2),
                        org.assertj.core.api.Assertions.tuple("w", 1));
        // The team question carries one cell per segment, aligned with report.segments().
        SegmentedReport.Question teamReport = report.sections().get(0).questions().get(1);
        assertThat(teamReport.questionId()).isEqualTo(team);
        assertThat(teamReport.cells().get(0).report().distribution())
                .extracting(DistributionEntry::label, DistributionEntry::count)
                .containsExactlyInAnyOrder(org.assertj.core.api.Assertions.tuple("Red", 1L),
                        org.assertj.core.api.Assertions.tuple("Blue", 1L));
        assertThat(teamReport.cells().get(1).report().distribution())
                .extracting(DistributionEntry::label, DistributionEntry::count)
                .containsExactly(org.assertj.core.api.Assertions.tuple("Red", 1L));
    }

    @Test
    void suppressesSegmentsBelowTheAnonymityThreshold() {
        Survey survey = surveyService.createSurvey(MANAGER, new CreateSurveyRequest("S", "d", 2, null));
        Section section = surveyService.addSection(survey.getId(), "General");
        Long gender = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Gender?", QuestionType.SINGLE_CHOICE, List.of("m", "w"))).getId();
        surveyService.open(survey.getId());
        submit(survey.getId(), Map.of(gender, "m"));
        submit(survey.getId(), Map.of(gender, "m"));
        submit(survey.getId(), Map.of(gender, "w")); // only one "w": below the threshold of 2

        SegmentedReport report = reportService.buildSegmented(survey.getId(), gender);

        assertThat(report.segments()).extracting(SegmentedReport.Segment::label).containsExactly("m");
        assertThat(report.suppressedLabels()).containsExactly("w");
    }

    @Test
    void filtersTheReportToASingleSegment() {
        Survey survey = surveyService.createSurvey(MANAGER, new CreateSurveyRequest("S", "d", 1, null));
        Section section = surveyService.addSection(survey.getId(), "General");
        Long gender = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Gender?", QuestionType.SINGLE_CHOICE, List.of("m", "w"))).getId();
        Long team = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Which team?", QuestionType.SINGLE_CHOICE, List.of("Red", "Blue"))).getId();
        surveyService.open(survey.getId());
        submit(survey.getId(), Map.of(gender, "m", team, "Red"));
        submit(survey.getId(), Map.of(gender, "m", team, "Blue"));
        submit(survey.getId(), Map.of(gender, "w", team, "Red"));

        SurveyReport report = reportService.buildFiltered(survey.getId(), gender, "m");

        // Only the two "m" responses are counted; the "w" response is excluded.
        assertThat(report.responseCount()).isEqualTo(2);
        QuestionReport teamReport = report.sections().get(0).questions().get(1);
        assertThat(teamReport.questionId()).isEqualTo(team);
        assertThat(teamReport.distribution())
                .extracting(DistributionEntry::label, DistributionEntry::count)
                .containsExactlyInAnyOrder(org.assertj.core.api.Assertions.tuple("Red", 1L),
                        org.assertj.core.api.Assertions.tuple("Blue", 1L));
    }

    @Test
    void filteringRefusesASegmentBelowTheAnonymityThreshold() {
        Survey survey = surveyService.createSurvey(MANAGER, new CreateSurveyRequest("S", "d", 2, null));
        Section section = surveyService.addSection(survey.getId(), "General");
        Long gender = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Gender?", QuestionType.SINGLE_CHOICE, List.of("m", "w"))).getId();
        surveyService.open(survey.getId());
        submit(survey.getId(), Map.of(gender, "m"));
        submit(survey.getId(), Map.of(gender, "m"));
        submit(survey.getId(), Map.of(gender, "w")); // only one "w": below the threshold of 2

        assertThatThrownBy(() -> reportService.buildFiltered(survey.getId(), gender, "w"))
                .isInstanceOf(SurveyStateException.class);
    }

    @Test
    void segmentValuesAreTheQuestionsOptions() {
        Survey survey = surveyService.createSurvey(MANAGER, new CreateSurveyRequest("S", "d", 1, null));
        Section section = surveyService.addSection(survey.getId(), "General");
        Long gender = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Gender?", QuestionType.SINGLE_CHOICE, List.of("m", "w"))).getId();

        assertThat(reportService.segmentValues(survey.getId(), gender)).containsExactly("m", "w");
    }

    @Test
    void onlySingleChoiceQuestionsCanSegment() {
        Survey survey = surveyService.createSurvey(MANAGER, new CreateSurveyRequest("S", "d", 1, null));
        Section section = surveyService.addSection(survey.getId(), "General");
        Long gender = surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Gender?", QuestionType.SINGLE_CHOICE, List.of("m", "w"))).getId();
        surveyService.addQuestion(survey.getId(), section.getId(),
                new NewQuestion("Anything else?", QuestionType.TEXT, List.of()));

        assertThat(reportService.segmentationOptions(survey.getId()))
                .extracting(SegmentationOption::questionId).containsExactly(gender);
    }

    /** Distributes one invitation and submits the given answer using the emailed one-time code. */
    private void submit(Long surveyId, Long questionId, String value) {
        submit(surveyId, Map.of(questionId, value));
    }

    /** Distributes one invitation and submits one answer per (questionId, value) entry. */
    private void submit(Long surveyId, Map<Long, String> answers) {
        surveyService.distributeInvitations(surveyId, List.of("p@example.com"));
        List<AnswerSubmission> submissions = answers.entrySet().stream()
                .map(entry -> new AnswerSubmission(entry.getKey(), List.of(entry.getValue())))
                .toList();
        surveyService.submitResponse(surveyId, lastCode(), new ResponseSubmission(submissions));
    }

    private String lastCode() {
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(mailSender, atLeastOnce()).sendInvitation(anyString(), anyString(), url.capture());
        List<String> links = url.getAllValues();
        String link = links.get(links.size() - 1);
        return link.substring(link.indexOf("code=") + "code=".length());
    }
}
