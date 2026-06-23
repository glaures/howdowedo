package sandbox27.howdowedo.survey;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * A one-time access code that authorises answering a specific survey.
 *
 * <p>Only the code's <em>hash</em> is stored, and deliberately <strong>no email address</strong> -
 * the email&rarr;code mapping exists only transiently while invitations are sent and is then gone.
 * Therefore the system can recognise a valid code but can never learn whose code it is, which is what
 * makes responses untraceable and also why reminders to non-responders are impossible by design.
 */
@Entity
@Table(name = "survey_access_codes",
        uniqueConstraints = @UniqueConstraint(name = "uk_access_code", columnNames = {"survey_id", "code_hash"}))
public class SurveyAccessCode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "survey_id", nullable = false)
    private Long surveyId;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(nullable = false)
    private boolean used;

    private Instant usedAt;

    protected SurveyAccessCode() {
        // for JPA
    }

    public SurveyAccessCode(Long surveyId, String codeHash) {
        this.surveyId = surveyId;
        this.codeHash = codeHash;
    }

    public void markUsed() {
        this.used = true;
        this.usedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Long getSurveyId() {
        return surveyId;
    }

    public boolean isUsed() {
        return used;
    }

    public Instant getUsedAt() {
        return usedAt;
    }
}
