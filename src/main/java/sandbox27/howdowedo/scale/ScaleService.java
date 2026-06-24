package sandbox27.howdowedo.scale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.common.errors.LocalizedException;
import sandbox27.howdowedo.common.errors.NotFoundException;

import java.util.List;

/**
 * Management of the organisation-wide library of reusable {@link Scale}s.
 */
@Service
public class ScaleService {

    private final ScaleRepository scales;

    public ScaleService(ScaleRepository scales) {
        this.scales = scales;
    }

    /**
     * Creates a scale from a name and its ordered values. Blank values are dropped and the rest are
     * trimmed; a scale needs a unique, non-blank name and at least two distinct answer values.
     */
    @Transactional
    public Scale create(String name, String description, List<ScaleValue> values) {
        String cleanName = requireName(name);
        if (scales.existsByNameIgnoreCase(cleanName)) {
            throw new LocalizedException("error.scale.nameTaken", cleanName);
        }
        return scales.save(new Scale(cleanName, cleanDescription(description), cleanValues(values)));
    }

    /**
     * Updates an existing scale. The new name must stay unique (its own current name aside), and the
     * same value rules as {@link #create} apply. Existing surveys are unaffected because they hold a
     * snapshot of the values, not a reference to the scale.
     */
    @Transactional
    public Scale update(Long id, String name, String description, List<ScaleValue> values) {
        Scale scale = scales.findById(id)
                .orElseThrow(() -> new NotFoundException("error.scale.notFound", id));
        String cleanName = requireName(name);
        if (!scale.getName().equalsIgnoreCase(cleanName) && scales.existsByNameIgnoreCase(cleanName)) {
            throw new LocalizedException("error.scale.nameTaken", cleanName);
        }
        scale.update(cleanName, cleanDescription(description), cleanValues(values));
        return scale;
    }

    private String requireName(String name) {
        String cleanName = name != null ? name.trim() : "";
        if (cleanName.isEmpty()) {
            throw new LocalizedException("error.scale.nameRequired");
        }
        return cleanName;
    }

    private String cleanDescription(String description) {
        return description != null ? description.trim() : null;
    }

    /**
     * Drops entries with a blank label, trims the rest (keeping each option's optional score) and
     * requires at least two answer values.
     */
    private List<ScaleValue> cleanValues(List<ScaleValue> values) {
        List<ScaleValue> cleanValues = values == null ? List.of() : values.stream()
                .filter(v -> v != null && v.getValue() != null && !v.getValue().isBlank())
                .map(v -> new ScaleValue(v.getValue().trim(), v.getScore()))
                .toList();
        if (cleanValues.size() < 2) {
            throw new LocalizedException("error.scale.tooFewValues");
        }
        return cleanValues;
    }

    @Transactional(readOnly = true)
    public List<Scale> findAll() {
        List<Scale> result = scales.findAllByOrderByNameAsc();
        // Initialise values within the session; open-in-view is disabled.
        result.forEach(scale -> scale.getValues().size());
        return result;
    }

    @Transactional(readOnly = true)
    public Scale get(Long id) {
        Scale scale = scales.findById(id)
                .orElseThrow(() -> new NotFoundException("error.scale.notFound", id));
        scale.getValues().size();
        return scale;
    }

    @Transactional
    public void delete(Long id) {
        if (!scales.existsById(id)) {
            throw new NotFoundException("error.scale.notFound", id);
        }
        scales.deleteById(id);
    }
}
