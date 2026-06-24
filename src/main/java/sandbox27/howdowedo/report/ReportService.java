package sandbox27.howdowedo.report;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.survey.Question;
import sandbox27.howdowedo.survey.QuestionResult;
import sandbox27.howdowedo.survey.QuestionType;
import sandbox27.howdowedo.survey.SurveyResults;
import sandbox27.howdowedo.survey.SurveyService;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns a survey's anonymous aggregated results into a visual {@link SurveyReport}: it picks a
 * visualisation per question and computes the numbers for it. Anonymity (the k-anonymity threshold)
 * is enforced upstream by {@link SurveyService#results}.
 */
@Service
public class ReportService {

    private final SurveyService surveyService;

    public ReportService(SurveyService surveyService) {
        this.surveyService = surveyService;
    }

    @Transactional(readOnly = true)
    public SurveyReport build(Long surveyId) {
        SurveyResults results = surveyService.results(surveyId);
        Map<Long, QuestionResult> resultsById = results.questions().stream()
                .collect(Collectors.toMap(QuestionResult::questionId, Function.identity()));

        // Mirror the survey's own structure: one report section per survey section, in order.
        List<SectionReport> sections = surveyService.get(surveyId).getSections().stream()
                .map(section -> new SectionReport(section.getTitle(),
                        section.getQuestions().stream()
                                .map(question -> toReport(resultsById.get(question.getId()), question))
                                .toList()))
                .toList();
        return new SurveyReport(results.surveyId(), results.title(), results.responseCount(), sections);
    }

    private QuestionReport toReport(QuestionResult result, Question question) {
        if (result.type() == QuestionType.TEXT) {
            return QuestionReport.text(result.questionId(), result.text(), result.type(),
                    result.textAnswers(), result.comments());
        }
        Map<String, Integer> scores = question != null ? question.getOptionScores() : Map.of();
        if (isScaled(scores)) {
            return QuestionReport.favorable(result.questionId(), result.text(), result.type(),
                    favorable(result.optionCounts(), scores), result.comments());
        }
        return QuestionReport.distribution(result.questionId(), result.text(), result.type(),
                distribution(result.optionCounts()), result.comments());
    }

    /** A question qualifies for the three-part favorable bar only if its options carry a range of scores. */
    private boolean isScaled(Map<String, Integer> scores) {
        return scores.values().stream().distinct().count() > 1;
    }

    /**
     * Buckets responses around the midpoint of the scale's score range: below the midpoint counts as a
     * detractor, above as a supporter, exactly on it as neutral. For a symmetric scale (e.g. -2..2) the
     * midpoint is 0; for an all-positive scale (e.g. 1..5) it is the centre value.
     */
    private FavorableScore favorable(Map<String, Long> optionCounts, Map<String, Integer> scores) {
        int min = scores.values().stream().mapToInt(Integer::intValue).min().orElse(0);
        int max = scores.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        double midpoint = (min + max) / 2.0;
        long supporters = 0;
        long neutral = 0;
        long detractors = 0;
        for (Map.Entry<String, Long> entry : optionCounts.entrySet()) {
            Integer score = scores.get(entry.getKey());
            if (score == null) {
                continue; // an option without a score does not count towards the buckets
            }
            long count = entry.getValue();
            if (score > midpoint) {
                supporters += count;
            } else if (score < midpoint) {
                detractors += count;
            } else {
                neutral += count;
            }
        }
        long total = supporters + neutral + detractors;
        int favorablePercent = percent(supporters, total);
        int detractorPercent = percent(detractors, total);
        // Neutral absorbs the rounding remainder so the three shares always add up to 100.
        int neutralPercent = total == 0 ? 0 : 100 - favorablePercent - detractorPercent;
        return new FavorableScore(favorablePercent, neutralPercent, detractorPercent,
                supporters, neutral, detractors, total);
    }

    private List<DistributionEntry> distribution(Map<String, Long> optionCounts) {
        long total = optionCounts.values().stream().mapToLong(Long::longValue).sum();
        return optionCounts.entrySet().stream()
                .map(entry -> new DistributionEntry(entry.getKey(), entry.getValue(),
                        percent(entry.getValue(), total)))
                .toList();
    }

    private int percent(long count, long total) {
        return total == 0 ? 0 : Math.round(100f * count / total);
    }
}
