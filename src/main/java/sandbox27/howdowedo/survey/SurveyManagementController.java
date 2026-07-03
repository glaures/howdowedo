package sandbox27.howdowedo.survey;

import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import sandbox27.howdowedo.common.errors.LocalizedException;
import sandbox27.howdowedo.scale.Scale;
import sandbox27.howdowedo.scale.ScaleService;
import sandbox27.howdowedo.user.CurrentUser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Survey-manager UI to list and build surveys: create a draft, then add or remove questions on the
 * survey detail page. Answer options for a question can be entered directly or snapshotted from a
 * reusable {@link sandbox27.howdowedo.scale.Scale}.
 *
 * <p>Access to an individual survey is governed by its {@link SurveyPermission}s (see
 * {@link SurveyAccessService}): editing needs {@code EDIT}, the report ({@code ANALYZE}, served by
 * {@link sandbox27.howdowedo.report.ReportController}) and the permissions screen {@code ADMINISTER}. Creating a survey still requires the global
 * {@code SURVEY_MANAGER} role (enforced in security configuration).
 */
@Controller
@RequestMapping("/surveys")
public class SurveyManagementController {

    private final SurveyService surveyService;
    private final SurveyRecipientService recipientService;
    private final SurveyAccessService accessService;
    private final ScaleService scaleService;
    private final CurrentUser currentUser;

    public SurveyManagementController(SurveyService surveyService, SurveyRecipientService recipientService,
                                      SurveyAccessService accessService, ScaleService scaleService,
                                      CurrentUser currentUser) {
        this.surveyService = surveyService;
        this.recipientService = recipientService;
        this.accessService = accessService;
        this.scaleService = scaleService;
        this.currentUser = currentUser;
    }

    @GetMapping
    public String list(Authentication authentication, Model model) {
        Long userId = currentUser.require(authentication).getId();
        model.addAttribute("surveys", accessService.accessibleSurveys(userId));
        return "surveys/list";
    }

    @GetMapping("/new")
    public String newForm() {
        return "surveys/new";
    }

    @PostMapping
    public String create(Authentication authentication,
                         @RequestParam String title,
                         @RequestParam(required = false) String description,
                         @RequestParam(name = "minResponsesForResults", defaultValue = "1") int minResponses,
                         @RequestParam(name = "endDate", required = false) String endDate) {
        Long userId = currentUser.require(authentication).getId();
        Survey survey = surveyService.createSurvey(userId,
                new CreateSurveyRequest(title, description, minResponses, parseDate(endDate)));
        return "redirect:/surveys/" + survey.getId();
    }

    @GetMapping("/{id}")
    public String detail(Authentication authentication, @PathVariable Long id, Model model) {
        Long userId = currentUser.require(authentication).getId();
        Survey survey = surveyService.get(id);
        accessService.require(survey, userId, SurveyPermission.EDIT);
        Set<SurveyPermission> permissions = accessService.permissionsFor(survey, userId);

        model.addAttribute("survey", survey);
        model.addAttribute("sections", survey.getSections());
        model.addAttribute("questionTypes", QuestionType.values());
        model.addAttribute("scales", scaleService.findAll());
        model.addAttribute("recipientCount", recipientService.count(id));
        model.addAttribute("invitedRecipientCount", recipientService.invitedCount(id));
        model.addAttribute("canAdminister", permissions.contains(SurveyPermission.ADMINISTER));
        model.addAttribute("canAnalyze", permissions.contains(SurveyPermission.ANALYZE));
        if (survey.getStatus() != SurveyStatus.DRAFT) {
            model.addAttribute("turnout", surveyService.turnout(id));
        }
        return "surveys/detail";
    }

    @GetMapping("/{id}/recipients")
    public String recipients(Authentication authentication, @PathVariable Long id, Model model) {
        Survey survey = requireAccess(authentication, id, SurveyPermission.EDIT);
        model.addAttribute("survey", survey);
        model.addAttribute("recipients", recipientService.list(id));
        return "surveys/recipients";
    }

    /** Adds addresses typed into the form. */
    @PostMapping("/{id}/recipients")
    public String addRecipients(Authentication authentication, @PathVariable Long id,
                                @RequestParam(name = "emails", required = false) String emails,
                                RedirectAttributes redirectAttributes) {
        requireAccess(authentication, id, SurveyPermission.EDIT);
        reportImport(recipientService.addRecipients(id, emails), redirectAttributes);
        return "redirect:/surveys/" + id + "/recipients";
    }

    /** Adds addresses found in an uploaded text/CSV file. */
    @PostMapping("/{id}/recipients/upload")
    public String uploadRecipients(Authentication authentication, @PathVariable Long id,
                                   @RequestParam("file") MultipartFile file,
                                   RedirectAttributes redirectAttributes) {
        requireAccess(authentication, id, SurveyPermission.EDIT);
        if (file == null || file.isEmpty()) {
            throw new LocalizedException("error.recipients.fileRequired");
        }
        reportImport(recipientService.addRecipients(id, readText(file)), redirectAttributes);
        return "redirect:/surveys/" + id + "/recipients";
    }

    @PostMapping("/{id}/recipients/{recipientId}/delete")
    public String removeRecipient(Authentication authentication, @PathVariable Long id,
                                  @PathVariable Long recipientId) {
        requireAccess(authentication, id, SurveyPermission.EDIT);
        recipientService.remove(id, recipientId);
        return "redirect:/surveys/" + id + "/recipients";
    }

    @PostMapping("/{id}/open")
    public String open(Authentication authentication, @PathVariable Long id) {
        requireAccess(authentication, id, SurveyPermission.EDIT);
        surveyService.open(id);
        return "redirect:/surveys/" + id;
    }

    @PostMapping("/{id}/close")
    public String close(Authentication authentication, @PathVariable Long id) {
        requireAccess(authentication, id, SurveyPermission.EDIT);
        surveyService.close(id);
        return "redirect:/surveys/" + id;
    }

    @PostMapping("/{id}/end-date")
    public String updateEndDate(Authentication authentication, @PathVariable Long id,
                                @RequestParam(name = "endDate", required = false) String endDate) {
        requireAccess(authentication, id, SurveyPermission.EDIT);
        surveyService.updateEndDate(id, parseDate(endDate));
        return "redirect:/surveys/" + id;
    }

    /** Sends one-time response links to all recipients who have not been invited yet. */
    @PostMapping("/{id}/invitations")
    public String invite(Authentication authentication, @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        requireAccess(authentication, id, SurveyPermission.EDIT);
        int sent = surveyService.inviteRecipients(id);
        redirectAttributes.addFlashAttribute("invitedCount", sent);
        return "redirect:/surveys/" + id;
    }

    @PostMapping("/{id}/sections")
    public String addSection(Authentication authentication, @PathVariable Long id,
                             @RequestParam String title) {
        requireAccess(authentication, id, SurveyPermission.EDIT);
        surveyService.addSection(id, title);
        return "redirect:/surveys/" + id;
    }

    @PostMapping("/{id}/sections/{sectionId}/delete")
    public String removeSection(Authentication authentication, @PathVariable Long id,
                                @PathVariable Long sectionId) {
        requireAccess(authentication, id, SurveyPermission.EDIT);
        surveyService.removeSection(id, sectionId);
        return "redirect:/surveys/" + id;
    }

    @PostMapping("/{id}/sections/{sectionId}/questions")
    public String addQuestion(Authentication authentication, @PathVariable Long id,
                              @PathVariable Long sectionId,
                              @RequestParam String text,
                              @RequestParam QuestionType type,
                              @RequestParam(required = false) Long scaleId,
                              @RequestParam(name = "options", required = false) List<String> options,
                              @RequestParam(name = "allowsComments", defaultValue = "false") boolean allowsComments,
                              @RequestParam(name = "mandatory", defaultValue = "false") boolean mandatory) {
        requireAccess(authentication, id, SurveyPermission.EDIT);
        surveyService.addQuestion(id, sectionId, buildQuestion(text, type, scaleId, options, allowsComments, mandatory));
        return "redirect:/surveys/" + id;
    }

    @PostMapping("/{id}/questions/{questionId}")
    public String updateQuestion(Authentication authentication, @PathVariable Long id,
                                 @PathVariable Long questionId,
                                 @RequestParam String text,
                                 @RequestParam QuestionType type,
                                 @RequestParam(required = false) Long scaleId,
                                 @RequestParam(name = "options", required = false) List<String> options,
                                 @RequestParam(name = "allowsComments", defaultValue = "false") boolean allowsComments,
                                 @RequestParam(name = "mandatory", defaultValue = "false") boolean mandatory) {
        requireAccess(authentication, id, SurveyPermission.EDIT);
        surveyService.updateQuestion(id, questionId,
                buildQuestion(text, type, scaleId, options, allowsComments, mandatory));
        return "redirect:/surveys/" + id;
    }

    @PostMapping("/{id}/questions/{questionId}/delete")
    public String removeQuestion(Authentication authentication, @PathVariable Long id,
                                 @PathVariable Long questionId) {
        requireAccess(authentication, id, SurveyPermission.EDIT);
        surveyService.removeQuestion(id, questionId);
        return "redirect:/surveys/" + id;
    }

    // --- per-survey permissions (ADMINISTER) ---

    @GetMapping("/{id}/permissions")
    public String permissions(Authentication authentication, @PathVariable Long id, Model model) {
        requireAccess(authentication, id, SurveyPermission.ADMINISTER);
        SurveyPermissionsPage page = accessService.permissionsPage(id);
        model.addAttribute("page", page);
        model.addAttribute("survey", page.survey());
        model.addAttribute("allPermissions", SurveyPermission.values());
        return "surveys/permissions";
    }

    @PostMapping("/{id}/permissions")
    public String grant(Authentication authentication, @PathVariable Long id,
                        @RequestParam Long userId,
                        @RequestParam(name = "permissions", required = false) Set<SurveyPermission> permissions) {
        requireAccess(authentication, id, SurveyPermission.ADMINISTER);
        accessService.setPermissions(id, userId, permissions != null ? permissions : Set.of());
        return "redirect:/surveys/" + id + "/permissions";
    }

    @PostMapping("/{id}/owner")
    public String changeOwner(Authentication authentication, @PathVariable Long id,
                              @RequestParam Long newOwnerId) {
        requireAccess(authentication, id, SurveyPermission.ADMINISTER);
        accessService.changeOwner(id, newOwnerId);
        return "redirect:/surveys/" + id + "/permissions";
    }

    @PostMapping("/{id}/delete")
    public String delete(Authentication authentication, @PathVariable Long id,
                         RedirectAttributes redirectAttributes) {
        Survey survey = requireAccess(authentication, id, SurveyPermission.ADMINISTER);
        redirectAttributes.addFlashAttribute("deletedSurveyTitle", survey.getTitle());
        surveyService.deleteSurvey(id);
        return "redirect:/surveys";
    }

    /**
     * Builds the question input. TEXT questions carry no options; a chosen scale is snapshotted
     * (labels and, where present, per-option scores); otherwise the typed options win (no scores).
     */
    private NewQuestion buildQuestion(String text, QuestionType type, Long scaleId, List<String> options,
                                      boolean allowsComments, boolean mandatory) {
        if (type == QuestionType.TEXT) {
            return new NewQuestion(text, type, List.of(), allowsComments, mandatory, Map.of());
        }
        if (scaleId != null) {
            Scale scale = scaleService.get(scaleId);
            Map<String, Integer> scores = new LinkedHashMap<>();
            scale.getValues().forEach(v -> {
                if (v.getScore() != null) {
                    scores.put(v.getValue(), v.getScore());
                }
            });
            return new NewQuestion(text, type, scale.getLabels(), allowsComments, mandatory, scores);
        }
        List<String> custom = options == null ? List.of() : options.stream()
                .filter(o -> o != null && !o.isBlank())
                .map(String::trim)
                .toList();
        return new NewQuestion(text, type, custom, allowsComments, mandatory, Map.of());
    }

    /** Parses an ISO date (yyyy-MM-dd) from a form field; blank means "no end date". */
    private LocalDate parseDate(String value) {
        return (value == null || value.isBlank()) ? null : LocalDate.parse(value.trim());
    }

    private void reportImport(RecipientImportResult result, RedirectAttributes redirectAttributes) {
        redirectAttributes.addFlashAttribute("importedAdded", result.added());
        redirectAttributes.addFlashAttribute("importedDuplicates", result.duplicates());
        redirectAttributes.addFlashAttribute("importedInvalid", result.invalid());
    }

    private String readText(MultipartFile file) {
        try {
            return new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new LocalizedException("error.recipients.fileUnreadable");
        }
    }

    /** Loads the survey and asserts the current user holds {@code needed} on it. */
    private Survey requireAccess(Authentication authentication, Long surveyId, SurveyPermission needed) {
        Long userId = currentUser.require(authentication).getId();
        Survey survey = surveyService.get(surveyId);
        accessService.require(survey, userId, needed);
        return survey;
    }
}
