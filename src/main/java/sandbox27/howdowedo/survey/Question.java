package sandbox27.howdowedo.survey;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * A single question within a {@link Section} of a {@link Survey}.
 */
@Entity
@Table(name = "survey_questions")
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "section_id")
    private Section section;

    @Column(nullable = false, length = 1000)
    private String text;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;

    @Column(nullable = false)
    private int position;

    /** Whether participants may attach a free-text comment to their answer. */
    @Column(name = "allows_comments", columnDefinition = "boolean not null default true")
    private boolean allowsComments = true;

    @ElementCollection
    @CollectionTable(name = "survey_question_options", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "option_value")
    private List<String> options = new ArrayList<>();

    protected Question() {
        // for JPA
    }

    Question(Section section, String text, QuestionType type, List<String> options, boolean allowsComments,
             int position) {
        this.section = section;
        this.text = text;
        this.type = type;
        this.options = options != null ? new ArrayList<>(options) : new ArrayList<>();
        this.allowsComments = allowsComments;
        this.position = position;
    }

    /** Updates the editable attributes (text, type, options, comments) in place; position is kept. */
    void update(String text, QuestionType type, List<String> options, boolean allowsComments) {
        this.text = text;
        this.type = type;
        this.options = options != null ? new ArrayList<>(options) : new ArrayList<>();
        this.allowsComments = allowsComments;
    }

    public Long getId() {
        return id;
    }

    public String getText() {
        return text;
    }

    public QuestionType getType() {
        return type;
    }

    public int getPosition() {
        return position;
    }

    public List<String> getOptions() {
        return List.copyOf(options);
    }

    public boolean isAllowsComments() {
        return allowsComments;
    }
}
