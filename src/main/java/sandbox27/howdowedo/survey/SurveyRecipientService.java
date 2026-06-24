package sandbox27.howdowedo.survey;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.common.errors.NotFoundException;
import sandbox27.howdowedo.common.errors.SurveyStateException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Manages a survey's recipient list - the e-mail addresses that should receive a personal response
 * link. Addresses can be added by direct entry or by uploading a list; both arrive here as free-form
 * text and are parsed, validated and de-duplicated.
 *
 * <p>The list is only the survey's audience. It is never linked to issued access codes or responses,
 * so editing it does not weaken the anonymity of who answered (see {@link SurveyRecipient}).
 */
@Service
public class SurveyRecipientService {

    /** Pragmatic e-mail check: one '@', no whitespace, and a dotted domain. */
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final SurveyRepository surveys;
    private final SurveyRecipientRepository recipients;

    public SurveyRecipientService(SurveyRepository surveys, SurveyRecipientRepository recipients) {
        this.surveys = surveys;
        this.recipients = recipients;
    }

    /**
     * Adds every valid, not-yet-known address found in the given text to the survey's recipient list.
     * Tokens are split on whitespace, commas and semicolons; addresses are lower-cased so case-only
     * variants count as the same person. Allowed until the survey is closed.
     */
    @Transactional
    public RecipientImportResult addRecipients(Long surveyId, String rawText) {
        Survey survey = require(surveyId);
        if (survey.getStatus() == SurveyStatus.CLOSED) {
            throw new SurveyStateException("error.recipients.surveyClosed");
        }

        int added = 0;
        int duplicates = 0;
        int invalid = 0;
        Set<String> seenInThisBatch = new LinkedHashSet<>();
        for (String token : tokenize(rawText)) {
            String email = token.toLowerCase(Locale.ROOT);
            if (!EMAIL.matcher(email).matches()) {
                invalid++;
            } else if (!seenInThisBatch.add(email)
                    || recipients.existsBySurveyIdAndEmailIgnoreCase(surveyId, email)) {
                duplicates++;
            } else {
                recipients.save(new SurveyRecipient(surveyId, email));
                added++;
            }
        }
        return new RecipientImportResult(added, duplicates, invalid);
    }

    @Transactional(readOnly = true)
    public List<SurveyRecipient> list(Long surveyId) {
        return recipients.findBySurveyIdOrderByEmailAsc(surveyId);
    }

    @Transactional(readOnly = true)
    public long count(Long surveyId) {
        return recipients.countBySurveyId(surveyId);
    }

    @Transactional(readOnly = true)
    public long invitedCount(Long surveyId) {
        return recipients.countBySurveyIdAndInvitedTrue(surveyId);
    }

    /** Removes a single recipient from the survey. Allowed until the survey is closed. */
    @Transactional
    public void remove(Long surveyId, Long recipientId) {
        if (require(surveyId).getStatus() == SurveyStatus.CLOSED) {
            throw new SurveyStateException("error.recipients.surveyClosed");
        }
        SurveyRecipient recipient = recipients.findByIdAndSurveyId(recipientId, surveyId)
                .orElseThrow(() -> new NotFoundException("error.recipient.notFound", recipientId));
        recipients.delete(recipient);
    }

    private List<String> tokenize(String rawText) {
        if (rawText == null || rawText.isBlank()) {
            return List.of();
        }
        return List.of(rawText.trim().split("[\\s,;]+"));
    }

    private Survey require(Long surveyId) {
        return surveys.findById(surveyId)
                .orElseThrow(() -> new NotFoundException("error.survey.notFound", surveyId));
    }
}
