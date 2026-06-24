package sandbox27.howdowedo.survey;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.scale.ScaleRepository;
import sandbox27.howdowedo.user.UserService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SurveySeederTest {

    /** A small, generic seed fixture committed under src/test/resources (the real file is not). */
    private static final String TEST_SEED = "seed/seed-survey-test.tsv";
    private static final String SURVEY_TITLE = "Employee Engagement Survey";

    @Autowired
    private SurveyService surveyService;
    @Autowired
    private SurveyRepository surveys;
    @Autowired
    private ScaleRepository scales;
    @Autowired
    private UserService users;

    @MockitoBean
    private InvitationMailSender mailSender;

    private SurveySeeder seeder(String seedResource) {
        return new SurveySeeder(surveys, surveyService, scales, users, seedResource);
    }

    @Test
    void seedsSurveyWithSectionsQuestionsAndScale() throws Exception {
        users.provisionOnLogin("test", "owner", "owner@example.com", "Owner");

        seeder(TEST_SEED).run();

        Survey survey = surveys.findAll().stream()
                .filter(s -> s.getTitle().equals(SURVEY_TITLE))
                .findFirst().orElseThrow();
        survey = surveyService.get(survey.getId());

        assertThat(survey.getStatus()).isEqualTo(SurveyStatus.DRAFT);
        assertThat(survey.getSections()).extracting(Section::getTitle)
                .containsExactly("Engagement", "Leadership"); // order preserved, grouped by section

        List<Question> questions = survey.getQuestions();
        assertThat(questions).hasSize(3);
        assertThat(questions).allMatch(q -> q.getType() == QuestionType.SCALE);
        assertThat(questions).allMatch(q -> q.getOptions().size() == 5);

        assertThat(scales.existsByNameIgnoreCase("Agreement (5-point)")).isTrue();
    }

    @Test
    void scoresTheAgreementScaleFromMinusTwoToPlusTwo() throws Exception {
        users.provisionOnLogin("test", "owner", "owner@example.com", "Owner");

        seeder(TEST_SEED).run();

        var scale = scales.findAll().stream()
                .filter(s -> s.getName().equals("Agreement (5-point)")).findFirst().orElseThrow();
        assertThat(scale.getValues()).extracting(sandbox27.howdowedo.scale.ScaleValue::getScore)
                .containsExactly(-2, -1, 0, 1, 2);
    }

    @Test
    void isIdempotentAndDoesNotDuplicate() throws Exception {
        users.provisionOnLogin("test", "owner", "owner@example.com", "Owner");

        seeder(TEST_SEED).run();
        seeder(TEST_SEED).run();

        long count = surveys.findAll().stream()
                .filter(s -> s.getTitle().equals(SURVEY_TITLE))
                .count();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void doesNothingWhenSeedFileIsMissing() throws Exception {
        users.provisionOnLogin("test", "owner", "owner@example.com", "Owner");

        seeder("seed/does-not-exist.tsv").run();

        assertThat(surveys.existsByTitle(SURVEY_TITLE)).isFalse();
    }

    @Test
    void skipsWhenNoUserExists() throws Exception {
        seeder(TEST_SEED).run();

        assertThat(surveys.existsByTitle(SURVEY_TITLE)).isFalse();
    }
}
