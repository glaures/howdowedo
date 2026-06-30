package sandbox27.howdowedo.survey;

import java.util.List;

/**
 * Aggregated results split into one bucket per distinct answer to a single-choice segmentation
 * question.
 *
 * <p>k-anonymity is preserved per segment: a bucket whose response count is below the survey's
 * {@code minResponsesForResults} threshold carries no data and is reported in {@code suppressedLabels}
 * (label only, never a count), so an individual can never be singled out via a small segment.
 */
public record SegmentedSurveyResults(Long surveyId,
                                     String title,
                                     int responseCount,
                                     Long segmentQuestionId,
                                     String segmentQuestionText,
                                     List<Segment> segments,
                                     List<String> suppressedLabels) {

    /** One segment: its value label ({@code ""} for responses that left the question blank) and results. */
    public record Segment(String label, SurveyResults results) {
    }
}
