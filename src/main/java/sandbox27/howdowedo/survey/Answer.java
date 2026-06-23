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
 * several rows sharing the same {@code questionId}. References the question by id only - never the
 * responding user.
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

    protected Answer() {
        // for JPA
    }

    Answer(SurveyResponse response, Long questionId, String value) {
        this.response = response;
        this.questionId = questionId;
        this.value = value;
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
}
