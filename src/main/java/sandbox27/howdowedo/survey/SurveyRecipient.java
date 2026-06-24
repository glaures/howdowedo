package sandbox27.howdowedo.survey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * An e-mail address that is meant to receive a personal response link for a survey.
 *
 * <p>This is the survey's <em>audience</em>: who should be invited. It is deliberately separate from
 * {@link SurveyAccessCode} (the issued codes), and the two are never linked - knowing whom we invited
 * must not reveal who actually responded. The recipient list can therefore be edited freely before
 * codes are sent without weakening response anonymity.
 */
@Entity
@Table(name = "survey_recipients",
        uniqueConstraints = @UniqueConstraint(name = "uk_recipient", columnNames = {"survey_id", "email"}))
public class SurveyRecipient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "survey_id", nullable = false)
    private Long surveyId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false, updatable = false)
    private Instant addedAt;

    /**
     * Whether this recipient has already been sent an invitation. Tracks outreach only - it never
     * reveals which access code was issued or whether the person responded, so anonymity is intact.
     */
    @Column(nullable = false)
    private boolean invited;

    private Instant invitedAt;

    protected SurveyRecipient() {
        // for JPA
    }

    public SurveyRecipient(Long surveyId, String email) {
        this.surveyId = surveyId;
        this.email = email;
    }

    @PrePersist
    void onCreate() {
        this.addedAt = Instant.now();
    }

    public void markInvited() {
        this.invited = true;
        this.invitedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSurveyId() {
        return surveyId;
    }

    public String getEmail() {
        return email;
    }

    public Instant getAddedAt() {
        return addedAt;
    }

    public boolean isInvited() {
        return invited;
    }

    public Instant getInvitedAt() {
        return invitedAt;
    }
}
