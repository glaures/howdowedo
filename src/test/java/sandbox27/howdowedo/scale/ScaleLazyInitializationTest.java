package sandbox27.howdowedo.scale;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Guards against {@code LazyInitializationException} on {@link Scale#getValues()} with
 * {@code open-in-view: false}: scale values must be readable after the service transaction commits
 * (as the scales list view and the question snapshot do). Deliberately NOT {@code @Transactional}.
 */
@SpringBootTest
@ActiveProfiles("test")
class ScaleLazyInitializationTest {

    @Autowired
    private ScaleService scaleService;
    @Autowired
    private ScaleRepository scaleRepository;

    @AfterEach
    void cleanUp() {
        scaleRepository.deleteAll();
    }

    @Test
    void findAllExposesValuesOutsideTransaction() {
        scaleService.create("Agreement", null, vals("No", "Maybe", "Yes"));

        List<Scale> all = scaleService.findAll();

        assertThatCode(() -> assertThat(all.get(0).getLabels()).containsExactly("No", "Maybe", "Yes"))
                .doesNotThrowAnyException();
    }

    @Test
    void getExposesValuesOutsideTransaction() {
        Long id = scaleService.create("Frequency", null, vals("Never", "Always")).getId();

        Scale loaded = scaleService.get(id);

        assertThatCode(() -> assertThat(loaded.getLabels()).containsExactly("Never", "Always"))
                .doesNotThrowAnyException();
    }

    private static List<ScaleValue> vals(String... labels) {
        return java.util.Arrays.stream(labels).map(l -> new ScaleValue(l, null)).toList();
    }
}
