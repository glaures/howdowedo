package sandbox27.howdowedo.survey;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * A titled section that groups questions within a {@link Survey}. Sections are ordered by
 * {@code position} (insertion order); questions are ordered by their position within the section.
 */
@Entity
@Table(name = "survey_sections")
public class Section {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "survey_id")
    private Survey survey;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private int position;

    @OneToMany(mappedBy = "section", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<Question> questions = new ArrayList<>();

    protected Section() {
        // for JPA
    }

    Section(Survey survey, String title, int position) {
        this.survey = survey;
        this.title = title;
        this.position = position;
    }

    Question addQuestion(String text, QuestionType type, List<String> options, boolean allowsComments) {
        Question question = new Question(this, text, type, options, allowsComments, questions.size());
        questions.add(question);
        return question;
    }

    void removeQuestion(Long questionId) {
        questions.removeIf(q -> questionId != null && questionId.equals(q.getId()));
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public int getPosition() {
        return position;
    }

    public List<Question> getQuestions() {
        return List.copyOf(questions);
    }
}
