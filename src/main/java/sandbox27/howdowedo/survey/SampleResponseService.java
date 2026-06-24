package sandbox27.howdowedo.survey;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.common.errors.NotFoundException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates plausible, anonymous sample responses for a survey so the reports can be tried out with
 * realistic-looking data. Completed responses are written directly (no access codes), and choice/scale
 * answers are skewed towards the favorable end - with a per-question bias - so the favorable bars vary.
 *
 * <p>This is a development/testing aid; see {@link SampleResponseSeeder} for how it is triggered.
 */
@Service
public class SampleResponseService {

    private static final String[] TEXTS = {
            "More flexible hours would help.",
            "I enjoy working with my team.",
            "Better tooling, please.",
            "Communication could be clearer.",
            "Overall a good place to work.",
            "More learning opportunities would be great.",
    };
    private static final String[] COMMENTS = {
            "Just my honest opinion.",
            "Things have improved lately.",
            "Hope this helps.",
            "No strong feelings here.",
    };

    private final SurveyRepository surveys;
    private final SurveyResponseRepository responses;
    private final Random random = new Random();

    public SampleResponseService(SurveyRepository surveys, SurveyResponseRepository responses) {
        this.surveys = surveys;
        this.responses = responses;
    }

    /** Surveys that have questions but no completed responses yet (safe default targets). */
    @Transactional(readOnly = true)
    public List<Long> surveysWithoutResponses() {
        return surveys.findAll().stream()
                .filter(survey -> !survey.getQuestions().isEmpty())
                .filter(survey -> responses.countBySurveyIdAndCompletedTrue(survey.getId()) == 0)
                .map(Survey::getId)
                .toList();
    }

    /** Creates {@code count} completed sample responses for the survey. Returns the number created. */
    @Transactional
    public int generate(Long surveyId, int count) {
        Survey survey = surveys.findById(surveyId)
                .orElseThrow(() -> new NotFoundException("error.survey.notFound", surveyId));
        List<Question> questions = survey.getQuestions();

        // Give each question its own favorability so the report shows a spread, not identical bars.
        Map<Long, Double> favorability = new HashMap<>();
        questions.forEach(q -> favorability.put(q.getId(), 0.5 + random.nextDouble() * 0.4));

        for (int i = 0; i < count; i++) {
            SurveyResponse response = new SurveyResponse(surveyId);
            for (Question question : questions) {
                answer(response, question, favorability.get(question.getId()));
            }
            response.complete();
            responses.save(response);
        }
        return count;
    }

    private void answer(SurveyResponse response, Question question, double favorability) {
        List<String> options = question.getOptions();
        switch (question.getType()) {
            case TEXT -> response.addAnswer(question.getId(), TEXTS[random.nextInt(TEXTS.length)]);
            case MULTIPLE_CHOICE -> {
                boolean picked = false;
                for (String option : options) {
                    if (random.nextDouble() < 0.35) {
                        response.addAnswer(question.getId(), option);
                        picked = true;
                    }
                }
                if (!picked && !options.isEmpty()) {
                    response.addAnswer(question.getId(), options.get(random.nextInt(options.size())));
                }
            }
            default -> { // SINGLE_CHOICE, SCALE: one option, biased towards the favorable end
                if (!options.isEmpty()) {
                    response.addAnswer(question.getId(), options.get(biasedIndex(options.size(), favorability)));
                }
            }
        }
        if (question.isAllowsComments() && random.nextDouble() < 0.15) {
            response.setComment(question.getId(), COMMENTS[random.nextInt(COMMENTS.length)]);
        }
    }

    /** Picks an option index around {@code favorability} of the range, with some spread. */
    private int biasedIndex(int size, double favorability) {
        double center = favorability * (size - 1);
        double spread = Math.max(0.8, (size - 1) * 0.28);
        int index = (int) Math.round(center + random.nextGaussian() * spread);
        return Math.max(0, Math.min(size - 1, index));
    }
}
