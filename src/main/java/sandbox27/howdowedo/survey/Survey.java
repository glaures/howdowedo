package sandbox27.howdowedo.survey;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import sandbox27.howdowedo.common.errors.SurveyStateException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A survey owned by a Survey Manager.
 *
 * <p>Responses are always anonymous: they are submitted via one-time access codes (not by logged-in
 * users) and stored without any reference to a person (see {@link SurveyResponse}).
 * {@code minResponsesForResults} enforces k-anonymity - results stay hidden until enough responses
 * exist that an individual answer cannot be singled out.
 *
 * <p>Note that {@code createdByUserId} (the author) is intentionally the only user link a survey
 * holds; it says nothing about who <em>responded</em>.
 */
@Entity
@Table(name = "surveys")
public class Survey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SurveyStatus status = SurveyStatus.DRAFT;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "min_responses_for_results", nullable = false)
    private int minResponsesForResults;

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<Question> questions = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Survey() {
        // for JPA
    }

    public Survey(String title, String description, int minResponsesForResults, Long createdByUserId) {
        this.title = title;
        this.description = description;
        this.minResponsesForResults = Math.max(1, minResponsesForResults);
        this.createdByUserId = createdByUserId;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Question addQuestion(String text, QuestionType type, List<String> options) {
        Question question = new Question(this, text, type, options, questions.size());
        questions.add(question);
        return question;
    }

    public void open() {
        if (status != SurveyStatus.DRAFT) {
            throw new SurveyStateException("Only draft surveys can be opened");
        }
        status = SurveyStatus.OPEN;
    }

    public void close() {
        if (status != SurveyStatus.OPEN) {
            throw new SurveyStateException("Only open surveys can be closed");
        }
        status = SurveyStatus.CLOSED;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public SurveyStatus getStatus() {
        return status;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public int getMinResponsesForResults() {
        return minResponsesForResults;
    }

    public List<Question> getQuestions() {
        return List.copyOf(questions);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
