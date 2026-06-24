package sandbox27.howdowedo.survey;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A set of answers to a survey.
 *
 * <p>Anonymity by construction:
 * <ul>
 *   <li>There is <strong>no user reference at all</strong> - responses arrive via one-time access
 *       codes, not logged-in users.</li>
 *   <li>The id is a random UUID, not a sequential number, so insertion order leaks nothing.</li>
 *   <li>Only the submission <em>date</em> is stored (no time), to avoid timestamp correlation with
 *       when an access code was marked used.</li>
 *   <li>A <em>completed</em> response has no link to the access code that authorised it.</li>
 * </ul>
 *
 * <p>While answering, a response carries {@code inProgressCodeHash} so that partial answers can be
 * reloaded and continued (we save after each section). This is the hash of the access code - never
 * the code itself or an e-mail - and since the code&rarr;person mapping is never stored, it cannot
 * identify anyone. On {@link #complete()} the hash is cleared, severing even that link.
 */
@Entity
@Table(name = "survey_responses")
public class SurveyResponse {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "survey_id", nullable = false)
    private Long surveyId;

    @Column(name = "submitted_on", nullable = false)
    private LocalDate submittedOn;

    /** {@code false} while the participant is still answering; {@code true} once finished. */
    @Column(nullable = false)
    private boolean completed;

    /** Hash of the authorising code while in progress; {@code null} once completed. */
    @Column(name = "in_progress_code_hash", length = 64)
    private String inProgressCodeHash;

    @OneToMany(mappedBy = "response", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Answer> answers = new ArrayList<>();

    protected SurveyResponse() {
        // for JPA
    }

    /** A one-shot, already-completed response (no in-progress link). */
    public SurveyResponse(Long surveyId) {
        this.id = UUID.randomUUID().toString();
        this.surveyId = surveyId;
        this.submittedOn = LocalDate.now();
    }

    /** An in-progress response that can be reloaded by its authorising code's hash. */
    public SurveyResponse(Long surveyId, String inProgressCodeHash) {
        this(surveyId);
        this.inProgressCodeHash = inProgressCodeHash;
    }

    public void addAnswer(Long questionId, String value) {
        answers.add(new Answer(this, questionId, value, false));
    }

    /** Replaces the chosen-value answers for one question with the given (non-blank) values. */
    public void setAnswers(Long questionId, List<String> values) {
        answers.removeIf(a -> a.getQuestionId().equals(questionId) && !a.isComment());
        if (values != null) {
            values.stream()
                    .filter(v -> v != null && !v.isBlank())
                    .forEach(v -> answers.add(new Answer(this, questionId, v.trim(), false)));
        }
    }

    /** Replaces the free-text comment for one question; a blank comment removes it. */
    public void setComment(Long questionId, String comment) {
        answers.removeIf(a -> a.getQuestionId().equals(questionId) && a.isComment());
        if (comment != null && !comment.isBlank()) {
            answers.add(new Answer(this, questionId, comment.trim(), true));
        }
    }

    /** Marks the response finished and removes the in-progress link to the access code. */
    public void complete() {
        this.completed = true;
        this.inProgressCodeHash = null;
        this.submittedOn = LocalDate.now();
    }

    public String getId() {
        return id;
    }

    public Long getSurveyId() {
        return surveyId;
    }

    public LocalDate getSubmittedOn() {
        return submittedOn;
    }

    public boolean isCompleted() {
        return completed;
    }

    public List<Answer> getAnswers() {
        return List.copyOf(answers);
    }
}
