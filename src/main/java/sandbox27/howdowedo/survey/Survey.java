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
import java.time.LocalDate;
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

    /** Optional last day responses are accepted (inclusive). {@code null} means no time limit. */
    @Column(name = "end_date")
    private LocalDate endDate;

    @OneToMany(mappedBy = "survey", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position")
    private List<Section> sections = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /** When the survey was soft-deleted; {@code null} while it is live. Deleted surveys keep all
     *  their data but are hidden from every list and treated as non-existent by the domain services. */
    @Column(name = "deleted_at")
    private Instant deletedAt;

    protected Survey() {
        // for JPA
    }

    public Survey(String title, String description, int minResponsesForResults, LocalDate endDate,
                  Long createdByUserId) {
        this.title = title;
        this.description = description;
        this.minResponsesForResults = Math.max(1, minResponsesForResults);
        this.endDate = endDate;
        this.createdByUserId = createdByUserId;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Section addSection(String title) {
        Section section = new Section(this, title, sections.size());
        sections.add(section);
        return section;
    }

    /** Removes the section with the given id (and its questions via orphan removal), if present. */
    public void removeSection(Long sectionId) {
        sections.removeIf(s -> sectionId != null && sectionId.equals(s.getId()));
    }

    public Section section(Long sectionId) {
        return sections.stream()
                .filter(s -> s.getId().equals(sectionId))
                .findFirst()
                .orElse(null);
    }

    /** Removes the question with the given id from whichever section holds it. */
    public void removeQuestion(Long questionId) {
        sections.forEach(s -> s.removeQuestion(questionId));
    }

    public boolean isDraft() {
        return status == SurveyStatus.DRAFT;
    }

    /** Sets or clears the end date. Allowed until the survey is closed. */
    public void changeEndDate(LocalDate endDate) {
        if (status == SurveyStatus.CLOSED) {
            throw new SurveyStateException("error.survey.closedNoEdit");
        }
        this.endDate = endDate;
    }

    /** Whether the optional end date has passed (and responses should no longer be accepted). */
    public boolean hasEnded() {
        return endDate != null && LocalDate.now().isAfter(endDate);
    }

    public void open() {
        if (status != SurveyStatus.DRAFT) {
            throw new SurveyStateException("error.survey.onlyDraftCanOpen");
        }
        status = SurveyStatus.OPEN;
    }

    public void close() {
        if (status != SurveyStatus.OPEN) {
            throw new SurveyStateException("error.survey.onlyOpenCanClose");
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

    /** Transfers ownership to another user; the new owner then implicitly holds every permission. */
    public void assignOwner(Long userId) {
        this.createdByUserId = userId;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    /** Marks the survey as soft-deleted, keeping all its data. */
    public void markDeleted() {
        if (deletedAt == null) {
            this.deletedAt = Instant.now();
        }
    }

    public int getMinResponsesForResults() {
        return minResponsesForResults;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public List<Section> getSections() {
        return List.copyOf(sections);
    }

    /** All questions across all sections, in section then question order. */
    public List<Question> getQuestions() {
        return sections.stream()
                .flatMap(s -> s.getQuestions().stream())
                .toList();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
