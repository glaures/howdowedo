package sandbox27.howdowedo.survey;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.context.MessageSource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import sandbox27.howdowedo.common.errors.LocalizedException;

/**
 * Public, unauthenticated survey-answering flow reached from the personal link e-mailed to a
 * participant ({@code /s/{surveyId}?code=...}). The participant is guided through one section at a
 * time; answers are saved on every step so an interrupted response can be resumed via the same link.
 *
 * <p>Authorisation is the one-time access code, not a login. Invalid/expired/used links render a
 * friendly page here (handled locally) rather than bouncing through the app's login.
 */
@Controller
@RequestMapping("/s")
public class SurveyParticipationController {

    private static final String ANSWER_PREFIX = "answer_";
    private static final String COMMENT_PREFIX = "comment_";

    private final SurveyService surveyService;
    private final MessageSource messages;

    public SurveyParticipationController(SurveyService surveyService, MessageSource messages) {
        this.surveyService = surveyService;
        this.messages = messages;
    }

    @GetMapping("/{surveyId}")
    public String show(@PathVariable Long surveyId, @RequestParam String code,
                       @RequestParam(name = "section", required = false) Integer sectionParam, Model model) {
        Survey survey = surveyService.loadForParticipation(surveyId, code);
        List<Section> sections = survey.getSections();

        int index = Math.max(0, Math.min(sectionParam == null ? 0 : sectionParam, sections.size() - 1));
        model.addAttribute("survey", survey);
        model.addAttribute("code", code);
        model.addAttribute("section", sections.get(index));
        model.addAttribute("sectionIndex", index);
        model.addAttribute("sectionNumber", index + 1);
        model.addAttribute("totalSections", sections.size());
        model.addAttribute("isFirst", index == 0);
        model.addAttribute("isLast", index == sections.size() - 1);
        model.addAttribute("saved", surveyService.savedAnswers(surveyId, code));
        model.addAttribute("savedComments", surveyService.savedComments(surveyId, code));
        return "participate/section";
    }

    @PostMapping("/{surveyId}/section")
    public String save(@PathVariable Long surveyId, @RequestParam String code,
                       @RequestParam Long sectionId, @RequestParam int sectionIndex,
                       @RequestParam String direction, @RequestParam MultiValueMap<String, String> form) {
        surveyService.saveSection(surveyId, code, sectionId, extractAnswers(form), extractComments(form));

        if ("finish".equals(direction)) {
            surveyService.completeParticipation(surveyId, code);
            return "redirect:/s/" + surveyId + "/done";
        }
        int target = "back".equals(direction) ? sectionIndex - 1 : sectionIndex + 1;
        return "redirect:/s/" + surveyId + "?code=" + encode(code) + "&section=" + target;
    }

    @GetMapping("/{surveyId}/done")
    public String done() {
        return "participate/done";
    }

    /** Renders invalid/expired/used links (and missing surveys) as a friendly public page. */
    @ExceptionHandler(LocalizedException.class)
    public String invalidLink(LocalizedException ex, Model model, Locale locale) {
        model.addAttribute("message", messages.getMessage(ex.getMessageKey(), ex.getArgs(), locale));
        return "participate/error";
    }

    /** Collects {@code answer_<questionId>} form fields into {@code questionId -> values}. */
    private Map<Long, List<String>> extractAnswers(MultiValueMap<String, String> form) {
        Map<Long, List<String>> answers = new LinkedHashMap<>();
        form.forEach((key, values) -> {
            if (key.startsWith(ANSWER_PREFIX)) {
                answers.put(Long.valueOf(key.substring(ANSWER_PREFIX.length())), new ArrayList<>(values));
            }
        });
        return answers;
    }

    /** Collects {@code comment_<questionId>} form fields into {@code questionId -> comment}. */
    private Map<Long, String> extractComments(MultiValueMap<String, String> form) {
        Map<Long, String> comments = new LinkedHashMap<>();
        form.forEach((key, values) -> {
            if (key.startsWith(COMMENT_PREFIX) && !values.isEmpty()) {
                comments.put(Long.valueOf(key.substring(COMMENT_PREFIX.length())), values.get(0));
            }
        });
        return comments;
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
