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
 * A submitted set of answers.
 *
 * <p>Anonymity by construction:
 * <ul>
 *   <li>There is <strong>no user reference at all</strong> - responses arrive via one-time access
 *       codes, not logged-in users.</li>
 *   <li>The id is a random UUID, not a sequential number, so insertion order leaks nothing.</li>
 *   <li>Only the submission <em>date</em> is stored (no time), to avoid timestamp correlation with
 *       when an access code was marked used.</li>
 *   <li>There is no foreign key to the access code that authorised the submission.</li>
 * </ul>
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

    @OneToMany(mappedBy = "response", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Answer> answers = new ArrayList<>();

    protected SurveyResponse() {
        // for JPA
    }

    public SurveyResponse(Long surveyId) {
        this.id = UUID.randomUUID().toString();
        this.surveyId = surveyId;
        this.submittedOn = LocalDate.now();
    }

    public void addAnswer(Long questionId, String value) {
        answers.add(new Answer(this, questionId, value));
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

    public List<Answer> getAnswers() {
        return List.copyOf(answers);
    }
}
