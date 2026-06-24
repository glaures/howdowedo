package sandbox27.howdowedo.survey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * A single answer value belonging to a {@link SurveyResponse}. Multiple-choice answers are stored as
 * several rows sharing the same {@code questionId}. A row with {@code comment == true} holds the
 * participant's free-text comment for a question rather than a chosen value. References the question
 * by id only - never the responding user.
 */
@Entity
@Table(name = "survey_answers")
public class Answer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "response_id")
    private SurveyResponse response;

    @Column(name = "question_id", nullable = false)
    private Long questionId;

    @Column(name = "answer_value", length = 4000)
    private String value;

    /** {@code true} if this row holds a free-text comment rather than a chosen answer value. */
    @Column(name = "is_comment", columnDefinition = "boolean not null default false")
    private boolean comment;

    protected Answer() {
        // for JPA
    }

    Answer(SurveyResponse response, Long questionId, String value, boolean comment) {
        this.response = response;
        this.questionId = questionId;
        this.value = value;
        this.comment = comment;
    }

    public Long getId() {
        return id;
    }

    public Long getQuestionId() {
        return questionId;
    }

    public String getValue() {
        return value;
    }

    public boolean isComment() {
        return comment;
    }
}
