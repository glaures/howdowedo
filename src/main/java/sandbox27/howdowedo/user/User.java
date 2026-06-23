package sandbox27.howdowedo.user;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * An authenticated person.
 *
 * <p>Identity is keyed on (provider, subject): the OIDC {@code sub} claim is stable per identity
 * provider, so this survives e-mail or name changes and keeps providers (Microsoft, Google, ...)
 * from colliding. {@code email} and {@code name} are cached profile attributes from the provider.
 *
 * <p>Deliberately, a {@code User} holds NO reference to survey responses - see the anonymity design.
 */
@Entity
@Table(name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_identity", columnNames = {"provider", "subject"}))
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false)
    private String subject;

    private String email;

    private String name;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_roles", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected User() {
        // for JPA
    }

    public User(String provider, String subject, String email, String name) {
        this.provider = provider;
        this.subject = subject;
        this.email = email;
        this.name = name;
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public void addRole(Role role) {
        this.roles.add(role);
    }

    public boolean hasRole(Role role) {
        return this.roles.contains(role);
    }

    /** Replaces all roles. Callers are responsible for keeping {@link Role#USER} present. */
    public void setRoles(Set<Role> roles) {
        this.roles = EnumSet.copyOf(roles);
    }

    /** Refreshes the cached profile attributes from the identity provider. */
    public void updateProfile(String email, String name) {
        this.email = email;
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getSubject() {
        return subject;
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
