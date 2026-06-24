package sandbox27.howdowedo.survey;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards against {@code LazyInitializationException} with {@code open-in-view: false}: the survey
 * loaded by the service must expose its questions and their options AFTER the service transaction
 * has committed (as happens when the controller/view reads them). Deliberately NOT {@code @Transactional}
 * - a test transaction would keep the session open and mask the bug.
 */
@SpringBootTest
@ActiveProfiles("test")
class SurveyLazyInitializationTest {

    @Autowired
    private SurveyService surveyService;
    @Autowired
    private SurveyRepository surveyRepository;

    @AfterEach
    void cleanUp() {
        surveyRepository.deleteAll();
    }

    @Test
    void getExposesSectionsQuestionsAndOptionsOutsideTransaction() {
        Survey created = surveyService.createSurvey(42L, new CreateSurveyRequest("Engagement", "d", 1, null));
        Section section = surveyService.addSection(created.getId(), "Wellbeing");
        surveyService.addQuestion(created.getId(), section.getId(),
                new NewQuestion("Happy?", QuestionType.SINGLE_CHOICE, List.of("Yes", "No")));

        Survey loaded = surveyService.get(created.getId());

        assertThatCode(() -> {
            assertThat(loaded.getSections()).hasSize(1);
            assertThat(loaded.getSections().get(0).getQuestions()).hasSize(1);
            assertThat(loaded.getQuestions().get(0).getOptions()).containsExactly("Yes", "No");
        }).doesNotThrowAnyException();
    }

    @Test
    void findByCreatorExposesSectionsAndQuestionCountOutsideTransaction() {
        Survey created = surveyService.createSurvey(43L, new CreateSurveyRequest("Survey", "d", 1, null));
        Section section = surveyService.addSection(created.getId(), "General");
        surveyService.addQuestion(created.getId(), section.getId(),
                new NewQuestion("Comment", QuestionType.TEXT, List.of()));

        List<Survey> list = surveyService.findByCreator(43L);

        assertThatCode(() -> assertThat(list.get(0).getSections().get(0).getQuestions()).hasSize(1))
                .doesNotThrowAnyException();
    }
}
