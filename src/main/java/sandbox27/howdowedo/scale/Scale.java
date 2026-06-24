package sandbox27.howdowedo.scale;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A reusable, named set of ordered answer values (e.g. an agreement scale "Strongly disagree" ...
 * "Strongly agree"). Scales form an organisation-wide library: a survey manager picks one when
 * adding a question, and its values are copied into the question (snapshot), so editing a scale
 * later never changes existing surveys.
 */
@Entity
@Table(name = "scales",
        uniqueConstraints = @UniqueConstraint(name = "uk_scale_name", columnNames = "name"))
public class Scale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(length = 1000)
    private String description;

    @ElementCollection
    @CollectionTable(name = "scale_values", joinColumns = @JoinColumn(name = "scale_id"))
    @OrderColumn(name = "position")
    private List<ScaleValue> values = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected Scale() {
        // for JPA
    }

    public Scale(String name, String description, List<ScaleValue> values) {
        this.name = name;
        this.description = description;
        this.values = values != null ? new ArrayList<>(values) : new ArrayList<>();
    }

    /** Updates the name, description and ordered values in place. */
    public void update(String name, String description, List<ScaleValue> values) {
        this.name = name;
        this.description = description;
        this.values = values != null ? new ArrayList<>(values) : new ArrayList<>();
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<ScaleValue> getValues() {
        return List.copyOf(values);
    }

    /** The option labels only, in order (used when snapshotting a scale into a question). */
    public List<String> getLabels() {
        return values.stream().map(ScaleValue::getValue).toList();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
