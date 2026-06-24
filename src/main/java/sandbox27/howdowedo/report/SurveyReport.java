package sandbox27.howdowedo.report;

import java.util.List;

/** Visual report for a survey, derived from its anonymous aggregated results, grouped by section. */
public record SurveyReport(Long surveyId, String title, int responseCount, List<SectionReport> sections) {

    /** All question reports across sections, in order (convenience for callers that ignore grouping). */
    public List<QuestionReport> questions() {
        return sections.stream().flatMap(section -> section.questions().stream()).toList();
    }
}
