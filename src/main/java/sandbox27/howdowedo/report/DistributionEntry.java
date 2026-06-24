package sandbox27.howdowedo.report;

/** One option of a distribution visualisation: its label, absolute count and share in percent. */
public record DistributionEntry(String label, long count, int percent) {
}
