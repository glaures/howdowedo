package sandbox27.howdowedo.survey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Development aid: on startup, fills surveys with anonymous sample responses so the reports can be
 * tried out. Enabled with {@code app.seed.sample-responses.enabled=true}.
 *
 * <p>With {@code app.seed.sample-responses.survey-id} it targets one survey (always generating);
 * without it, it targets every survey that has questions but no completed responses yet, so a restart
 * does not keep piling up data.
 */
@Component
@ConditionalOnProperty(name = "app.seed.sample-responses.enabled", havingValue = "true")
public class SampleResponseSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SampleResponseSeeder.class);

    private final SampleResponseService sampleResponses;
    private final int count;
    private final String surveyId;

    public SampleResponseSeeder(SampleResponseService sampleResponses,
                                @Value("${app.seed.sample-responses.count:40}") int count,
                                @Value("${app.seed.sample-responses.survey-id:}") String surveyId) {
        this.sampleResponses = sampleResponses;
        this.count = count;
        this.surveyId = surveyId;
    }

    @Override
    public void run(String... args) {
        List<Long> targets = (surveyId != null && !surveyId.isBlank())
                ? List.of(Long.valueOf(surveyId.trim()))
                : sampleResponses.surveysWithoutResponses();
        if (targets.isEmpty()) {
            log.info("Sample responses skipped: no target survey (set app.seed.sample-responses.survey-id?).");
            return;
        }
        for (Long id : targets) {
            int created = sampleResponses.generate(id, count);
            log.info("Sample responses: generated {} for survey id={}.", created, id);
        }
    }
}
