package sandbox27.howdowedo.survey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.common.errors.NotFoundException;
import sandbox27.howdowedo.common.errors.SurveyStateException;

import java.security.SecureRandom;
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
    private final InvitationMailSender mailSender;
    private final String baseUrl;

    public SurveyService(SurveyRepository surveys,
                         SurveyAccessCodeRepository accessCodes,
                         SurveyResponseRepository responses,
                         InvitationMailSender mailSender,
                         @Value("${app.base-url:http://localhost:8081}") String baseUrl) {
        this.surveys = surveys;
        this.accessCodes = accessCodes;
        this.responses = responses;
        this.mailSender = mailSender;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public Survey createSurvey(Long createdByUserId, CreateSurveyRequest request) {
        Survey survey = new Survey(request.title(), request.description(),
                request.minResponsesForResults(), createdByUserId);
        if (request.questions() != null) {
            request.questions().forEach(q -> survey.addQuestion(q.text(), q.type(), q.options()));
        }
        return surveys.save(survey);
    }

    @Transactional
    public Survey open(Long surveyId) {
        Survey survey = require(surveyId);
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
     * Records an anonymous submission authorised by a one-time access code. The code is validated and
     * consumed; the answers are stored with no link to the code or any person.
     */
    @Transactional
    public void submitResponse(Long surveyId, String code, ResponseSubmission submission) {
        Survey survey = require(surveyId);
        if (survey.getStatus() != SurveyStatus.OPEN) {
            throw new SurveyStateException("Survey is not open for responses");
        }

        SurveyAccessCode accessCode = accessCodes
                .findBySurveyIdAndCodeHash(surveyId, AccessCodes.hash(code))
                .orElseThrow(() -> new SurveyStateException("Invalid or unknown access code"));
        if (accessCode.isUsed()) {
            throw new SurveyStateException("This access code has already been used");
        }

        SurveyResponse response = new SurveyResponse(surveyId);
        if (submission.answers() != null) {
            for (AnswerSubmission answer : submission.answers()) {
                List<String> values = answer.values() != null ? answer.values() : List.of();
                for (String value : values) {
                    response.addAnswer(answer.questionId(), value);
                }
            }
        }
        responses.save(response);

        // Separate, unlinked write: consume the code without referencing the response just stored.
        accessCode.markUsed();
        accessCodes.save(accessCode);
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
        long count = responses.countBySurveyId(surveyId);
        if (count < survey.getMinResponsesForResults()) {
            throw new SurveyStateException("Not enough responses yet to show results without "
                    + "risking de-anonymisation (need at least " + survey.getMinResponsesForResults() + ")");
        }

        Map<Long, List<String>> answersByQuestion = new LinkedHashMap<>();
        for (SurveyResponse response : responses.findBySurveyId(surveyId)) {
            for (Answer answer : response.getAnswers()) {
                answersByQuestion.computeIfAbsent(answer.getQuestionId(), k -> new ArrayList<>())
                        .add(answer.getValue());
            }
        }

        List<QuestionResult> questionResults = new ArrayList<>();
        for (Question question : survey.getQuestions()) {
            List<String> values = answersByQuestion.getOrDefault(question.getId(), List.of());
            questionResults.add(toQuestionResult(question, values));
        }
        return new SurveyResults(survey.getId(), survey.getTitle(), (int) count, questionResults);
    }

    private QuestionResult toQuestionResult(Question question, List<String> values) {
        if (question.getType() == QuestionType.TEXT) {
            return new QuestionResult(question.getId(), question.getText(), question.getType(),
                    Map.of(), List.copyOf(values));
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        values.forEach(v -> counts.merge(v, 1L, Long::sum));
        return new QuestionResult(question.getId(), question.getText(), question.getType(), counts, List.of());
    }

    private String responseUrl(Long surveyId, String code) {
        return baseUrl + "/s/" + surveyId + "?code=" + code;
    }

    private Survey require(Long surveyId) {
        return surveys.findById(surveyId)
                .orElseThrow(() -> new NotFoundException("Survey " + surveyId + " not found"));
    }
}
