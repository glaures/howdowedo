package sandbox27.howdowedo.report;

import java.util.List;

/** A survey section in the report: its title and the question reports it groups, in order. */
public record SectionReport(String title, List<QuestionReport> questions) {
}
