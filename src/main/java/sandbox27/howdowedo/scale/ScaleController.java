package sandbox27.howdowedo.scale;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import sandbox27.howdowedo.common.errors.LocalizedException;

import java.util.ArrayList;
import java.util.List;

/**
 * Survey-manager screen for the reusable scale library: list, create, edit and delete scales. Each
 * option carries a label and an optional integer score (paired form fields {@code values}/{@code
 * scores}).
 */
@Controller
@RequestMapping("/scales")
public class ScaleController {

    private final ScaleService scaleService;

    public ScaleController(ScaleService scaleService) {
        this.scaleService = scaleService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("scales", scaleService.findAll());
        return "scales/list";
    }

    @PostMapping
    public String create(@RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(name = "values", required = false) List<String> values,
                         @RequestParam(name = "scores", required = false) List<String> scores) {
        scaleService.create(name, description, toScaleValues(values, scores));
        return "redirect:/scales";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable Long id,
                         @RequestParam String name,
                         @RequestParam(required = false) String description,
                         @RequestParam(name = "values", required = false) List<String> values,
                         @RequestParam(name = "scores", required = false) List<String> scores) {
        scaleService.update(id, name, description, toScaleValues(values, scores));
        return "redirect:/scales";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        scaleService.delete(id);
        return "redirect:/scales";
    }

    /** Zips the parallel {@code values}/{@code scores} form fields into ordered {@link ScaleValue}s. */
    private List<ScaleValue> toScaleValues(List<String> values, List<String> scores) {
        if (values == null) {
            return List.of();
        }
        List<ScaleValue> result = new ArrayList<>();
        for (int i = 0; i < values.size(); i++) {
            String score = (scores != null && i < scores.size()) ? scores.get(i) : null;
            result.add(new ScaleValue(values.get(i), parseScore(score)));
        }
        return result;
    }

    private Integer parseScore(String score) {
        if (score == null || score.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(score.trim());
        } catch (NumberFormatException e) {
            throw new LocalizedException("error.scale.invalidScore", score);
        }
    }
}
