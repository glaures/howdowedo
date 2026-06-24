package sandbox27.howdowedo.scale;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * One option of a {@link Scale}: a label and an optional integer {@code score} that may later be used
 * to turn answers into numbers for analysis (e.g. averaging an agreement scale 1..5).
 */
@Embeddable
public class ScaleValue {

    @Column(name = "scale_value", nullable = false)
    private String value;

    @Column(name = "score")
    private Integer score;

    protected ScaleValue() {
        // for JPA
    }

    public ScaleValue(String value, Integer score) {
        this.value = value;
        this.score = score;
    }

    public String getValue() {
        return value;
    }

    public Integer getScore() {
        return score;
    }
}
