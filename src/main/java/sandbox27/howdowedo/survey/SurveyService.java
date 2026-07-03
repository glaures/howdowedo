package sandbox27.howdowedo.survey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.common.errors.NotFoundException;
import sandbox27.howdowedo.common.errors.SurveyStateException;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Survey lifecycle, invitation distribution, response submission and result aggregation.
 *
 * <p>Anonymity is enforced on two paths:
 * <ul>
 *   <li><b>Distribution</b> stores only a hash of each access code and never the email address, so
 *       the email&rarr;code link does not exist in the system.</li>
 *   <li><b>Submission</b> is authorised by an access code (not a logged-in user); the response is
 *       written without any reference to the code or a person.</li>
 * </ul>
 */
@Service
public class SurveyService {

    private final SurveyRepository surveys;
    private final SurveyAccessCodeRepository accessCodes;
    private final SurveyResponseRepository responses;
    private final SurveyRecipientRepository recipients;
    private final InvitationMailSender mailSender;
    private final String baseUrl;

    public SurveyService(SurveyRepository surveys,
                         SurveyAccessCodeRepository accessCodes,
                         SurveyResponseRepository responses,
                         SurveyRecipientRepository recipients,
                         InvitationMailSender mailSender,
                         @Value("${app.base-url:http://localhost:8081}") String baseUrl) {
        this.surveys = surveys;
        this.accessCodes = accessCodes;
        this.responses = responses;
        this.recipients = recipients;
        this.mailSender = mailSender;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public Survey createSurvey(Long createdByUserId, CreateSurveyRequest request) {
        Survey survey = new Survey(request.title(), request.description(),
                request.minResponsesForResults(), request.endDate(), createdByUserId);
        return surveys.save(survey);
    }

    /** Sets or clears the end date (allowed until the survey is closed). */
    @Transactional
    public void updateEndDate(Long surveyId, LocalDate endDate) {
        require(surveyId).changeEndDate(endDate);
    }

    @Transactional(readOnly = true)
    public List<Survey> findByCreator(Long createdByUserId) {
        List<Survey> result = surveys.findByCreatedByUserIdAndDeletedAtIsNullOrderByCreatedAtDesc(createdByUserId);
        // Initialise sections and their questions within the session; open-in-view is disabled.
        result.forEach(survey -> survey.getSections().forEach(section -> section.getQuestions().size()));
        return result;
    }

    @Transactional(readOnly = true)
    public Survey get(Long surveyId) {
        Survey survey = require(surveyId);
        // Initialise sections, questions and their options within the session for rendering the
        // detail page (open-in-view is disabled, so lazy access in the view would otherwise fail).
        survey.getSections().forEach(section ->
                section.getQuestions().forEach(question -> question.getOptions().size()));
        return survey;
    }

    /**
     * Soft-deletes a survey: it is marked as deleted and disappears from every list, but all its data
     * (questions, responses, grants) is retained. Any later access to it is rejected as "not found".
     */
    @Transactional
    public void deleteSurvey(Long surveyId) {
        require(surveyId).markDeleted();
    }

    /** Adds a titled section to a draft survey. */
    @Transactional
    public Section addSection(Long surveyId, String title) {
        Survey survey = requireDraft(surveyId);
        String cleanTitle = title != null ? title.trim() : "";
        if (cleanTitle.isEmpty()) {
            throw new SurveyStateException("error.section.titleRequired");
        }
        Section section = survey.addSection(cleanTitle);
        surveys.flush(); // assign the generated id (cascade insert) so callers can reference it
        return section;
    }

    @Transactional
    public void removeSection(Long surveyId, Long sectionId) {
        requireDraft(surveyId).removeSection(sectionId);
    }

    /**
     * Adds a question to a section of a draft survey. Choice and scale questions require at least one
     * answer option (for scale questions these are the snapshotted scale values).
     */
    @Transactional
    public Question addQuestion(Long surveyId, Long sectionId, NewQuestion question) {
        Survey survey = requireDraft(surveyId);
        Section section = survey.section(sectionId);
        if (section == null) {
            throw new NotFoundException("error.section.notFound", sectionId);
        }
        boolean needsOptions = question.type() != QuestionType.TEXT;
        if (needsOptions && (question.options() == null || question.options().isEmpty())) {
            throw new SurveyStateException("error.survey.optionsRequired");
        }
        Question added = section.addQuestion(question.text(), question.type(), question.options(),
                question.allowsComments(), question.mandatory(), question.optionScores());
        surveys.flush(); // assign the generated id (cascade insert) so callers can reference it
        return added;
    }

    /**
     * Updates the text, type and answer options of an existing question in a draft survey. The same
     * option rule as for adding applies (choice and scale questions need at least one option).
     */
    @Transactional
    public Question updateQuestion(Long surveyId, Long questionId, NewQuestion update) {
        Survey survey = requireDraft(surveyId);
        Question question = survey.getQuestions().stream()
                .filter(q -> q.getId().equals(questionId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("error.question.notFound", questionId));
        boolean needsOptions = update.type() != QuestionType.TEXT;
        if (needsOptions && (update.options() == null || update.options().isEmpty())) {
            throw new SurveyStateException("error.survey.optionsRequired");
        }
        question.update(update.text(), update.type(), update.options(), update.allowsComments(),
                update.mandatory(), update.optionScores());
        return question;
    }

    @Transactional
    public void removeQuestion(Long surveyId, Long questionId) {
        requireDraft(surveyId).removeQuestion(questionId);
    }

    private Survey requireDraft(Long surveyId) {
        Survey survey = require(surveyId);
        if (!survey.isDraft()) {
            throw new SurveyStateException("error.survey.notDraft");
        }
        return survey;
    }

    @Transactional
    public Survey open(Long surveyId) {
        Survey survey = require(surveyId);
        if (survey.getQuestions().isEmpty()) {
            throw new SurveyStateException("error.survey.noQuestions");
        }
        survey.open();
        return survey;
    }

    @Transactional
    public Survey close(Long surveyId) {
        Survey survey = require(surveyId);
        survey.close();
        return survey;
    }

    /**
     * Issues one single-use access code per email and emails the personal response link. Only the
     * code hash is persisted; the email is used solely to send and is then forgotten.
     *
     * <p>The email list is shuffled before codes are generated so that the row insertion order
     * cannot be correlated with any externally known ordering of the input list (e.g. an alphabetical
     * AD export).
     *
     * @return the number of invitations sent
     */
    @Transactional
    public int distributeInvitations(Long surveyId, List<String> emails) {
        Survey survey = require(surveyId);
        if (survey.getStatus() != SurveyStatus.OPEN) {
            throw new SurveyStateException("error.survey.mustBeOpenToInvite");
        }
        if (emails == null || emails.isEmpty()) {
            throw new SurveyStateException("error.invitations.noRecipients");
        }

        List<String> shuffled = new ArrayList<>(emails);
        Collections.shuffle(shuffled, new SecureRandom());

        for (String email : shuffled) {
            String code = AccessCodes.generate();
            accessCodes.save(new SurveyAccessCode(surveyId, AccessCodes.hash(code)));
            mailSender.sendInvitation(email, survey.getTitle(), responseUrl(surveyId, code));
        }
        return shuffled.size();
    }

    /**
     * Sends a personal response link to every recipient on the survey's list who has not been invited
     * yet, and marks them as invited so a repeated send only reaches newly added recipients. The
     * recipient list is the single source of addresses (see {@link SurveyRecipient}).
     *
     * @return the number of invitations sent in this run
     */
    @Transactional
    public int inviteRecipients(Long surveyId) {
        Survey survey = require(surveyId);
        if (survey.getStatus() != SurveyStatus.OPEN) {
            throw new SurveyStateException("error.survey.mustBeOpenToInvite");
        }
        if (recipients.countBySurveyId(surveyId) == 0) {
            throw new SurveyStateException("error.invitations.noRecipients");
        }
        List<SurveyRecipient> pending = recipients.findBySurveyIdAndInvitedFalse(surveyId);
        if (pending.isEmpty()) {
            throw new SurveyStateException("error.invitations.allInvited");
        }

        int sent = distributeInvitations(surveyId, pending.stream().map(SurveyRecipient::getEmail).toList());
        pending.forEach(SurveyRecipient::markInvited); // managed entities; flushed on commit
        return sent;
    }

    /**
     * Records an anonymous submission authorised by a one-time access code. The code is validated and
     * consumed; the answers are stored with no link to the code or any person.
     */
    @Transactional
    public void submitResponse(Long surveyId, String code, ResponseSubmission submission) {
        requireOpenForResponse(surveyId);
        SurveyAccessCode accessCode = requireUsableCode(surveyId, code);

        SurveyResponse response = new SurveyResponse(surveyId);
        if (submission.answers() != null) {
            for (AnswerSubmission answer : submission.answers()) {
                List<String> values = answer.values() != null ? answer.values() : List.of();
                for (String value : values) {
                    response.addAnswer(answer.questionId(), value);
                }
                response.setComment(answer.questionId(), answer.comment());
            }
        }
        response.complete();
        responses.save(response);

        // Separate, unlinked write: consume the code without referencing the response just stored.
        accessCode.markUsed();
        accessCodes.save(accessCode);
    }

    /**
     * Loads a survey for answering: validates that it is open, not past its end date and that the code
     * is valid and unused, and initialises its sections/questions/options for rendering.
     */
    @Transactional(readOnly = true)
    public Survey loadForParticipation(Long surveyId, String code) {
        Survey survey = requireOpenForResponse(surveyId);
        requireUsableCode(surveyId, code);
        survey.getSections().forEach(section ->
                section.getQuestions().forEach(question -> question.getOptions().size()));
        return survey;
    }

    /** The chosen-value answers saved for an in-progress response, as {@code questionId -> values}. */
    @Transactional(readOnly = true)
    public Map<Long, List<String>> savedAnswers(Long surveyId, String code) {
        return responses.findBySurveyIdAndInProgressCodeHash(surveyId, AccessCodes.hash(code))
                .map(response -> {
                    Map<Long, List<String>> byQuestion = new LinkedHashMap<>();
                    response.getAnswers().stream()
                            .filter(answer -> !answer.isComment())
                            .forEach(answer -> byQuestion
                                    .computeIfAbsent(answer.getQuestionId(), k -> new ArrayList<>())
                                    .add(answer.getValue()));
                    return byQuestion;
                })
                .orElseGet(Map::of);
    }

    /** The free-text comments saved for an in-progress response, as {@code questionId -> comment}. */
    @Transactional(readOnly = true)
    public Map<Long, String> savedComments(Long surveyId, String code) {
        return responses.findBySurveyIdAndInProgressCodeHash(surveyId, AccessCodes.hash(code))
                .map(response -> {
                    Map<Long, String> byQuestion = new LinkedHashMap<>();
                    response.getAnswers().stream()
                            .filter(Answer::isComment)
                            .forEach(answer -> byQuestion.put(answer.getQuestionId(), answer.getValue()));
                    return byQuestion;
                })
                .orElseGet(Map::of);
    }

    /**
     * Saves (or replaces) the answers for one section of an in-progress response, so progress is not
     * lost if the participant pauses. Creates the in-progress response on first save.
     */
    @Transactional
    public void saveSection(Long surveyId, String code, Long sectionId,
                            Map<Long, List<String>> answers, Map<Long, String> comments) {
        Survey survey = requireOpenForResponse(surveyId);
        requireUsableCode(surveyId, code);
        Section section = survey.section(sectionId);
        if (section == null) {
            throw new NotFoundException("error.section.notFound", sectionId);
        }

        String hash = AccessCodes.hash(code);
        SurveyResponse response = responses.findBySurveyIdAndInProgressCodeHash(surveyId, hash)
                .orElseGet(() -> new SurveyResponse(surveyId, hash));
        // Only the questions of this section are (re)written; other sections keep their saved answers.
        section.getQuestions().forEach(question -> {
            response.setAnswers(question.getId(), answers.get(question.getId()));
            if (question.isAllowsComments()) {
                response.setComment(question.getId(), comments.get(question.getId()));
            }
        });
        responses.save(response);
    }

    /**
     * Mandatory questions of the survey (or of a single section, if {@code sectionId} is given) that
     * have no non-blank saved answer yet. Used to block submitting an incomplete response.
     */
    @Transactional(readOnly = true)
    public List<Long> unansweredMandatory(Long surveyId, String code, Long sectionId) {
        Survey survey = require(surveyId);
        Section section = sectionId == null ? null : survey.section(sectionId);
        List<Question> questions = section != null ? section.getQuestions() : survey.getQuestions();
        Map<Long, List<String>> saved = savedAnswers(surveyId, code);
        return questions.stream()
                .filter(Question::isMandatory)
                .filter(question -> isBlank(saved.get(question.getId())))
                .map(Question::getId)
                .toList();
    }

    private boolean isBlank(List<String> values) {
        return values == null || values.stream().allMatch(value -> value == null || value.isBlank());
    }

    /** Finalises the in-progress response and consumes the access code. */
    @Transactional
    public void completeParticipation(Long surveyId, String code) {
        requireOpenForResponse(surveyId);
        SurveyAccessCode accessCode = requireUsableCode(surveyId, code);
        if (!unansweredMandatory(surveyId, code, null).isEmpty()) {
            throw new SurveyStateException("error.survey.mandatoryUnanswered");
        }

        String hash = AccessCodes.hash(code);
        SurveyResponse response = responses.findBySurveyIdAndInProgressCodeHash(surveyId, hash)
                .orElseGet(() -> new SurveyResponse(surveyId, hash));
        response.complete();
        responses.save(response);

        accessCode.markUsed();
        accessCodes.save(accessCode);
    }

    private Survey requireOpenForResponse(Long surveyId) {
        Survey survey = require(surveyId);
        if (survey.getStatus() != SurveyStatus.OPEN) {
            throw new SurveyStateException("error.survey.notOpen");
        }
        if (survey.hasEnded()) {
            throw new SurveyStateException("error.survey.ended");
        }
        return survey;
    }

    private SurveyAccessCode requireUsableCode(Long surveyId, String code) {
        SurveyAccessCode accessCode = accessCodes
                .findBySurveyIdAndCodeHash(surveyId, AccessCodes.hash(code))
                .orElseThrow(() -> new SurveyStateException("error.survey.invalidCode"));
        if (accessCode.isUsed()) {
            throw new SurveyStateException("error.survey.codeUsed");
        }
        return accessCode;
    }

    /** Turnout figures (issued vs used codes). Never reveals who responded. */
    @Transactional(readOnly = true)
    public SurveyTurnout turnout(Long surveyId) {
        return new SurveyTurnout(accessCodes.countBySurveyId(surveyId),
                accessCodes.countBySurveyIdAndUsedTrue(surveyId));
    }

    /**
     * Aggregated results. Blocked until at least {@code minResponsesForResults} responses exist, so
     * that individual answers cannot be singled out (k-anonymity).
     */
    @Transactional(readOnly = true)
    public SurveyResults results(Long surveyId) {
        Survey survey = require(surveyId);
        List<SurveyResponse> completed = responses.findBySurveyIdAndCompletedTrue(surveyId);
        if (completed.size() < survey.getMinResponsesForResults()) {
            throw new SurveyStateException("error.survey.notEnoughResponses",
                    survey.getMinResponsesForResults());
        }
        return aggregate(survey, completed);
    }

    /**
     * Aggregated results split by the answer to a single-choice segmentation question, so segments can
     * be compared (e.g. answers broken down by the response to a "gender" question).
     *
     * <p>The overall anonymity gate applies, and <em>additionally</em> every segment must reach the
     * threshold on its own: a bucket below it carries no data and is returned only as a suppressed
     * label, so a small segment can never expose an individual.
     */
    @Transactional(readOnly = true)
    public SegmentedSurveyResults segmentedResults(Long surveyId, Long segmentQuestionId) {
        Survey survey = require(surveyId);
        int min = survey.getMinResponsesForResults();
        Question segmentQuestion = requireSegmentQuestion(survey, segmentQuestionId);

        List<SurveyResponse> completed = responses.findBySurveyIdAndCompletedTrue(surveyId);
        if (completed.size() < min) {
            throw new SurveyStateException("error.survey.notEnoughResponses", min);
        }

        // Bucket responses by their chosen value for the segment question (options first for stable order).
        Map<String, List<SurveyResponse>> buckets = new LinkedHashMap<>();
        segmentQuestion.getOptions().forEach(option -> buckets.put(option, new ArrayList<>()));
        for (SurveyResponse response : completed) {
            buckets.computeIfAbsent(segmentValue(response, segmentQuestionId), k -> new ArrayList<>())
                    .add(response);
        }

        List<SegmentedSurveyResults.Segment> segments = new ArrayList<>();
        List<String> suppressed = new ArrayList<>();
        for (Map.Entry<String, List<SurveyResponse>> bucket : buckets.entrySet()) {
            List<SurveyResponse> group = bucket.getValue();
            if (group.isEmpty()) {
                continue; // an option nobody picked: nothing to show, nothing to hide
            }
            if (group.size() < min) {
                suppressed.add(bucket.getKey());
            } else {
                segments.add(new SegmentedSurveyResults.Segment(bucket.getKey(), aggregate(survey, group)));
            }
        }
        return new SegmentedSurveyResults(survey.getId(), survey.getTitle(), completed.size(),
                segmentQuestionId, segmentQuestion.getText(), segments, suppressed);
    }

    /**
     * Aggregated results restricted to the responses that answered the single-choice
     * {@code segmentQuestionId} with {@code value}, so a manager can drill into one segment alone.
     *
     * <p>Both the overall and the per-segment anonymity gate apply: if the matching subset is below
     * {@code minResponsesForResults} it is refused (never shown), so a small segment cannot expose an
     * individual.
     */
    @Transactional(readOnly = true)
    public SurveyResults filteredResults(Long surveyId, Long segmentQuestionId, String value) {
        Survey survey = require(surveyId);
        int min = survey.getMinResponsesForResults();
        requireSegmentQuestion(survey, segmentQuestionId);

        List<SurveyResponse> completed = responses.findBySurveyIdAndCompletedTrue(surveyId);
        if (completed.size() < min) {
            throw new SurveyStateException("error.survey.notEnoughResponses", min);
        }
        List<SurveyResponse> group = completed.stream()
                .filter(response -> segmentValue(response, segmentQuestionId).equals(value))
                .toList();
        if (group.size() < min) {
            throw new SurveyStateException("error.report.segmentTooSmall", min);
        }
        return aggregate(survey, group);
    }

    /** Looks up the segmentation question and verifies it is single-choice (the only segmentable type). */
    private Question requireSegmentQuestion(Survey survey, Long segmentQuestionId) {
        Question segmentQuestion = survey.getQuestions().stream()
                .filter(q -> q.getId().equals(segmentQuestionId))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("error.question.notFound", segmentQuestionId));
        if (segmentQuestion.getType() != QuestionType.SINGLE_CHOICE) {
            throw new SurveyStateException("error.report.notSegmentable");
        }
        return segmentQuestion;
    }

    /** The response's chosen value for the segment question, or {@code ""} if it was left unanswered. */
    private String segmentValue(SurveyResponse response, Long segmentQuestionId) {
        return response.getAnswers().stream()
                .filter(answer -> !answer.isComment() && answer.getQuestionId().equals(segmentQuestionId))
                .map(Answer::getValue)
                .findFirst()
                .orElse("");
    }

    /** Tallies the given completed responses into one {@link QuestionResult} per survey question. */
    private SurveyResults aggregate(Survey survey, List<SurveyResponse> completed) {
        Map<Long, List<String>> answersByQuestion = new LinkedHashMap<>();
        Map<Long, List<String>> commentsByQuestion = new LinkedHashMap<>();
        for (SurveyResponse response : completed) {
            for (Answer answer : response.getAnswers()) {
                Map<Long, List<String>> target = answer.isComment() ? commentsByQuestion : answersByQuestion;
                target.computeIfAbsent(answer.getQuestionId(), k -> new ArrayList<>())
                        .add(answer.getValue());
            }
        }

        List<QuestionResult> questionResults = new ArrayList<>();
        for (Question question : survey.getQuestions()) {
            List<String> values = answersByQuestion.getOrDefault(question.getId(), List.of());
            List<String> comments = commentsByQuestion.getOrDefault(question.getId(), List.of());
            questionResults.add(toQuestionResult(question, values, comments));
        }
        return new SurveyResults(survey.getId(), survey.getTitle(), completed.size(), questionResults);
    }

    private QuestionResult toQuestionResult(Question question, List<String> values, List<String> comments) {
        if (question.getType() == QuestionType.TEXT) {
            return new QuestionResult(question.getId(), question.getText(), question.getType(),
                    Map.of(), List.copyOf(values), List.copyOf(comments));
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        values.forEach(v -> counts.merge(v, 1L, Long::sum));
        return new QuestionResult(question.getId(), question.getText(), question.getType(), counts,
                List.of(), List.copyOf(comments));
    }

    private String responseUrl(Long surveyId, String code) {
        return baseUrl + "/s/" + surveyId + "?code=" + code;
    }

    private Survey require(Long surveyId) {
        return surveys.findById(surveyId)
                .filter(survey -> !survey.isDeleted()) // a soft-deleted survey behaves as if it never existed
                .orElseThrow(() -> new NotFoundException("error.survey.notFound", surveyId));
    }
}
