package sandbox27.howdowedo.report;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import sandbox27.howdowedo.survey.Survey;
import sandbox27.howdowedo.survey.SurveyAccessService;
import sandbox27.howdowedo.survey.SurveyPermission;
import sandbox27.howdowedo.survey.SurveyService;
import sandbox27.howdowedo.user.CurrentUser;

/**
 * Visual reporting for a survey. Requires the {@link SurveyPermission#ANALYZE} right on the survey;
 * results stay hidden until the anonymity threshold is met (enforced in {@link ReportService}).
 */
@Controller
@RequestMapping("/surveys/{id}/report")
public class ReportController {

    private final ReportService reportService;
    private final SurveyService surveyService;
    private final SurveyAccessService accessService;
    private final CurrentUser currentUser;

    public ReportController(ReportService reportService, SurveyService surveyService,
                            SurveyAccessService accessService, CurrentUser currentUser) {
        this.reportService = reportService;
        this.surveyService = surveyService;
        this.accessService = accessService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String report(Authentication authentication, @PathVariable Long id, Model model) {
        Long userId = currentUser.require(authentication).getId();
        Survey survey = surveyService.get(id);
        accessService.require(survey, userId, SurveyPermission.ANALYZE);

        model.addAttribute("survey", survey);
        model.addAttribute("report", reportService.build(id));
        return "report/survey";
    }
}
