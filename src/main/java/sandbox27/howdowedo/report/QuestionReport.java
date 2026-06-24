package sandbox27.howdowedo.report;

import sandbox27.howdowedo.survey.QuestionType;

import java.util.List;

/**
 * The report for a single question: which visualisation to render plus the data for it. Only the
 * field matching {@link #visualization} is populated; the others are empty/null.
 */
public record QuestionReport(Long questionId,
                             String text,
                             QuestionType type,
                             ReportVisualization visualization,
                             FavorableScore favorable,
                             List<DistributionEntry> distribution,
                             List<String> textAnswers,
                             List<String> comments) {

    static QuestionReport favorable(Long id, String text, QuestionType type,
                                    FavorableScore favorable, List<String> comments) {
        return new QuestionReport(id, text, type, ReportVisualization.FAVORABLE,
                favorable, List.of(), List.of(), comments);
    }

    static QuestionReport distribution(Long id, String text, QuestionType type,
                                       List<DistributionEntry> distribution, List<String> comments) {
        return new QuestionReport(id, text, type, ReportVisualization.DISTRIBUTION,
                null, distribution, List.of(), comments);
    }

    static QuestionReport text(Long id, String text, QuestionType type,
                               List<String> textAnswers, List<String> comments) {
        return new QuestionReport(id, text, type, ReportVisualization.TEXT,
                null, List.of(), textAnswers, comments);
    }
}
