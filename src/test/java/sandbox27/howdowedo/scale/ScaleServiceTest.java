package sandbox27.howdowedo.scale;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import sandbox27.howdowedo.common.errors.LocalizedException;
import sandbox27.howdowedo.common.errors.NotFoundException;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ScaleServiceTest {

    @Autowired
    private ScaleService scaleService;

    /** Builds scale values without scores (the common case in these tests). */
    private static List<ScaleValue> vals(String... labels) {
        return Arrays.stream(labels).map(l -> new ScaleValue(l, null)).toList();
    }

    @Test
    void createsScaleTrimmingAndDroppingBlankValues() {
        Scale scale = scaleService.create("Agreement", "  How much you agree  ",
                vals("  Disagree ", "", "   ", "Agree"));

        assertThat(scale.getId()).isNotNull();
        assertThat(scale.getName()).isEqualTo("Agreement");
        assertThat(scale.getDescription()).isEqualTo("How much you agree");
        assertThat(scale.getLabels()).containsExactly("Disagree", "Agree");
    }

    @Test
    void createsScaleKeepingPerOptionScores() {
        Scale scale = scaleService.create("Agreement", null,
                List.of(new ScaleValue("Disagree", 1), new ScaleValue("Neutral", null),
                        new ScaleValue("Agree", 3)));

        assertThat(scaleService.get(scale.getId()).getValues())
                .extracting(ScaleValue::getValue, ScaleValue::getScore)
                .containsExactly(tuple("Disagree", 1), tuple("Neutral", null), tuple("Agree", 3));
    }

    @Test
    void keepsValueOrder() {
        Scale scale = scaleService.create("Frequency", null, vals("Never", "Sometimes", "Always"));

        assertThat(scaleService.get(scale.getId()).getLabels())
                .containsExactly("Never", "Sometimes", "Always");
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> scaleService.create("  ", null, vals("a", "b")))
                .isInstanceOf(LocalizedException.class)
                .hasMessage("error.scale.nameRequired");
    }

    @Test
    void rejectsDuplicateNameCaseInsensitive() {
        scaleService.create("Agreement", null, vals("a", "b"));

        assertThatThrownBy(() -> scaleService.create("agreement", null, vals("c", "d")))
                .isInstanceOf(LocalizedException.class)
                .hasMessage("error.scale.nameTaken");
    }

    @Test
    void rejectsFewerThanTwoValues() {
        assertThatThrownBy(() -> scaleService.create("Solo", null, vals("only")))
                .isInstanceOf(LocalizedException.class)
                .hasMessage("error.scale.tooFewValues");
    }

    @Test
    void updatesNameDescriptionAndValuesWithScores() {
        Scale scale = scaleService.create("Agreement", "old", vals("a", "b"));

        scaleService.update(scale.getId(), "  Agreement v2 ", "  new desc  ",
                List.of(new ScaleValue(" Disagree ", 1), new ScaleValue("", 9), new ScaleValue("Agree", 5)));

        Scale updated = scaleService.get(scale.getId());
        assertThat(updated.getName()).isEqualTo("Agreement v2");
        assertThat(updated.getDescription()).isEqualTo("new desc");
        assertThat(updated.getValues())
                .extracting(ScaleValue::getValue, ScaleValue::getScore)
                .containsExactly(tuple("Disagree", 1), tuple("Agree", 5)); // blank-label row dropped
    }

    @Test
    void updateKeepingOwnNameIsAllowed() {
        Scale scale = scaleService.create("Agreement", null, vals("a", "b"));

        scaleService.update(scale.getId(), "agreement", null, vals("c", "d"));

        assertThat(scaleService.get(scale.getId()).getLabels()).containsExactly("c", "d");
    }

    @Test
    void updateToAnotherExistingNameIsRejected() {
        scaleService.create("Frequency", null, vals("a", "b"));
        Scale agreement = scaleService.create("Agreement", null, vals("a", "b"));

        assertThatThrownBy(() -> scaleService.update(agreement.getId(), "frequency", null, vals("a", "b")))
                .isInstanceOf(LocalizedException.class)
                .hasMessage("error.scale.nameTaken");
    }

    @Test
    void updateWithTooFewValuesIsRejected() {
        Scale scale = scaleService.create("Agreement", null, vals("a", "b"));

        assertThatThrownBy(() -> scaleService.update(scale.getId(), "Agreement", null, vals("only")))
                .isInstanceOf(LocalizedException.class)
                .hasMessage("error.scale.tooFewValues");
    }

    @Test
    void updatingUnknownScaleIsRejected() {
        assertThatThrownBy(() -> scaleService.update(999L, "X", null, vals("a", "b")))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listsScalesAlphabeticallyAndDeletes() {
        Scale zebra = scaleService.create("Zebra", null, vals("a", "b"));
        scaleService.create("Alpha", null, vals("a", "b"));

        assertThat(scaleService.findAll()).extracting(Scale::getName).containsExactly("Alpha", "Zebra");

        scaleService.delete(zebra.getId());
        assertThat(scaleService.findAll()).extracting(Scale::getName).containsExactly("Alpha");
    }
}
