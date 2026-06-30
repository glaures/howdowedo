package sandbox27.howdowedo.survey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import sandbox27.howdowedo.scale.Scale;
import sandbox27.howdowedo.scale.ScaleRepository;
import sandbox27.howdowedo.scale.ScaleValue;
import sandbox27.howdowedo.user.Role;
import sandbox27.howdowedo.user.User;
import sandbox27.howdowedo.user.UserService;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One-off seeder that creates a survey from a tab-separated seed file on the classpath. It builds the
 * survey as a DRAFT via the regular domain services, so a manager can review and open it.
 *
 * <p>The file's first row holds column headers and is skipped. Each subsequent row is one question
 * with the columns: {@code mandatory-flag<TAB>section<TAB>question<TAB>type<TAB>choice...}. A
 * non-blank first column marks the question mandatory (blank = optional). The type is either
 * {@code Agreement} - rendered as a shared 5-point agreement {@link QuestionType#SCALE} - or
 * {@code Single Choice}, whose options are the remaining columns of the row.
 *
 * <p>The seed file holds the actual questions and is intentionally <em>not</em> part of the
 * repository (it may contain confidential, organisation-specific content). If the file is absent the
 * seeder simply does nothing - so a public checkout builds and runs without it.
 *
 * <p>Enabled with {@code app.seed.survey=true}; the seed file location can be overridden with
 * {@code app.seed.survey-file}. Idempotent: it skips if a survey with the same title already exists,
 * and reuses the agreement scale if it is already in the library.
 */
@Component
@ConditionalOnProperty(name = "app.seed.survey", havingValue = "true")
public class SurveySeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SurveySeeder.class);

    private static final String SURVEY_TITLE = "Employee Engagement Survey";
    private static final String SURVEY_DESCRIPTION =
            "Employee engagement survey. Responses are anonymous.";
    private static final int MIN_RESPONSES_FOR_RESULTS = 5;

    private static final String SCALE_NAME = "Agreement (5-point)";
    private static final String SCALE_DESCRIPTION = "Five-point Likert agreement scale.";
    private static final List<String> SCALE_VALUES = List.of(
            "Strongly disagree",
            "Disagree",
            "Neither agree nor disagree",
            "Agree",
            "Strongly agree");

    private final SurveyRepository surveys;
    private final SurveyService surveyService;
    private final ScaleRepository scales;
    private final UserService users;
    private final String seedResource;

    public SurveySeeder(SurveyRepository surveys, SurveyService surveyService,
                        ScaleRepository scales, UserService users,
                        @Value("${app.seed.survey-file:seed/seed-survey.tsv}") String seedResource) {
        this.surveys = surveys;
        this.surveyService = surveyService;
        this.scales = scales;
        this.users = users;
        this.seedResource = seedResource;
    }

    @Override
    public void run(String... args) throws IOException {
        ClassPathResource resource = new ClassPathResource(seedResource);
        if (!resource.exists()) {
            log.info("Survey seed skipped: no seed file at classpath:{}.", seedResource);
            return;
        }
        if (surveys.existsByTitle(SURVEY_TITLE)) {
            log.info("Survey seed skipped: '{}' already exists.", SURVEY_TITLE);
            return;
        }
        User owner = resolveOwner();
        if (owner == null) {
            log.warn("Survey seed skipped: no user to own the survey. Log in once, then re-run.");
            return;
        }

        Map<String, Integer> scores = scoreByLabel();
        ensureScale(scores);
        List<SeedQuestion> seedQuestions = readSeed(resource);

        Survey survey = surveyService.createSurvey(owner.getId(),
                new CreateSurveyRequest(SURVEY_TITLE, SURVEY_DESCRIPTION, MIN_RESPONSES_FOR_RESULTS, null));

        Map<String, Section> sections = new LinkedHashMap<>();
        for (SeedQuestion sq : seedQuestions) {
            Section section = sections.computeIfAbsent(sq.section(),
                    title -> surveyService.addSection(survey.getId(), title));
            boolean scale = sq.type() == QuestionType.SCALE;
            // Snapshot the per-option scores into scale questions so the report can compute favorability.
            surveyService.addQuestion(survey.getId(), section.getId(),
                    new NewQuestion(sq.text(), sq.type(),
                            scale ? SCALE_VALUES : sq.choices(),
                            true, sq.mandatory(), scale ? scores : Map.of()));
        }
        log.info("Survey seeded: '{}' (id={}) with {} sections and {} questions as DRAFT.",
                SURVEY_TITLE, survey.getId(), sections.size(), seedQuestions.size());
    }

    /** One parsed seed row. {@code choices} is unused for SCALE questions. */
    private record SeedQuestion(String section, String text, QuestionType type, boolean mandatory,
                                List<String> choices) {
    }

    /** Prefers an administrator, then any user, as the survey author. */
    private User resolveOwner() {
        List<User> all = users.findAll();
        return all.stream().filter(u -> u.hasRole(Role.ADMINISTRATOR)).findFirst()
                .orElseGet(() -> all.stream().findFirst().orElse(null));
    }

    /** Scores the agreement scale from -2 (Strongly disagree) to +2 (Strongly agree), keyed by label. */
    private Map<String, Integer> scoreByLabel() {
        Map<String, Integer> scores = new LinkedHashMap<>();
        for (int i = 0; i < SCALE_VALUES.size(); i++) {
            scores.put(SCALE_VALUES.get(i), i - 2);
        }
        return scores;
    }

    private void ensureScale(Map<String, Integer> scores) {
        if (!scales.existsByNameIgnoreCase(SCALE_NAME)) {
            List<ScaleValue> values = scores.entrySet().stream()
                    .map(e -> new ScaleValue(e.getKey(), e.getValue()))
                    .toList();
            scales.save(new Scale(SCALE_NAME, SCALE_DESCRIPTION, values));
        }
    }

    /** Reads the TSV into an ordered list of questions, preserving file order and skipping the header row. */
    private List<SeedQuestion> readSeed(ClassPathResource resource) throws IOException {
        List<SeedQuestion> questions = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                resource.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = line.split("\t", -1);
                if (parts.length < 4) {
                    continue;
                }
                String section = parts[1].trim();
                String text = parts[2].trim();
                // Skip the header row and any incomplete line.
                if (section.isBlank() || text.isBlank() || section.equalsIgnoreCase("Section")) {
                    continue;
                }
                boolean mandatory = !parts[0].isBlank();
                if ("Single Choice".equalsIgnoreCase(parts[3].trim())) {
                    List<String> choices = new ArrayList<>();
                    for (int i = 4; i < parts.length; i++) {
                        if (!parts[i].isBlank()) {
                            choices.add(parts[i].trim());
                        }
                    }
                    questions.add(new SeedQuestion(section, text, QuestionType.SINGLE_CHOICE, mandatory, choices));
                } else {
                    questions.add(new SeedQuestion(section, text, QuestionType.SCALE, mandatory, List.of()));
                }
            }
        }
        return questions;
    }
}
