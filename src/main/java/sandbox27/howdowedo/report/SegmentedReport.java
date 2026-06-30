package sandbox27.howdowedo.report;

import sandbox27.howdowedo.survey.QuestionType;

import java.util.List;

/**
 * A report broken down by the answer to a {@link SegmentationOption}: for every question the
 * per-segment visualisations are lined up so segments can be compared side by side. Segments hidden
 * for anonymity are listed in {@code suppressedLabels} (label only, no count).
 */
public record SegmentedReport(Long surveyId,
                              String title,
                              int responseCount,
                              SegmentationOption segmentedBy,
                              List<Segment> segments,
                              List<String> suppressedLabels,
                              List<Section> sections) {

    /** A visible segment: its value label and how many responses fell into it. */
    public record Segment(String label, int responseCount) {
    }

    public record Section(String title, List<Question> questions) {
    }

    /** One question with one {@link Cell} per visible segment. */
    public record Question(Long questionId, String text, QuestionType type, List<Cell> cells) {
    }

    /** One segment's view of a question: its label, size and the visualisation to render. */
    public record Cell(String label, int responseCount, QuestionReport report) {
    }
}
