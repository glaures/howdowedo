package sandbox27.howdowedo.report;

/** A question a report can be segmented by (currently single-choice questions). */
public record SegmentationOption(Long questionId, String text) {
}
