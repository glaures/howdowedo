package sandbox27.howdowedo.survey;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.util.EnumSet;
import java.util.Set;

/**
 * The set of {@link SurveyPermission}s a single user has been granted on a single survey.
 *
 * <p>The survey's creator is deliberately never represented here: they always hold every permission
 * (resolved in {@link SurveyAccessService}), so their rights cannot be stored away or revoked. A row
 * therefore only ever describes a <em>delegated</em> grant, and an empty set is not persisted - the
 * grant is removed instead.
 */
@Entity
@Table(name = "survey_permissions",
        uniqueConstraints = @UniqueConstraint(name = "uk_survey_user", columnNames = {"survey_id", "user_id"}))
public class SurveyAccess {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "survey_id", nullable = false)
    private Long surveyId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "survey_permission_grants",
            joinColumns = @JoinColumn(name = "survey_access_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission", nullable = false)
    private Set<SurveyPermission> permissions = EnumSet.noneOf(SurveyPermission.class);

    protected SurveyAccess() {
        // for JPA
    }

    public SurveyAccess(Long surveyId, Long userId) {
        this.surveyId = surveyId;
        this.userId = userId;
    }

    public void setPermissions(Set<SurveyPermission> permissions) {
        this.permissions = permissions.isEmpty()
                ? EnumSet.noneOf(SurveyPermission.class) : EnumSet.copyOf(permissions);
    }

    public Long getId() {
        return id;
    }

    public Long getSurveyId() {
        return surveyId;
    }

    public Long getUserId() {
        return userId;
    }

    public Set<SurveyPermission> getPermissions() {
        return Set.copyOf(permissions);
    }
}
