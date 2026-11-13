package dev.kevin.gallbladder;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

@Controller
public class MealController {

    private final MealService mealService;
    private final MealRepository meals;
    private final FoodRepository foods;
    private final SettingsRepository settings;
    private final Clock clock;

    public MealController(MealService mealService, MealRepository meals, FoodRepository foods,
                          SettingsRepository settings, Clock clock) {
        this.mealService = mealService;
        this.meals = meals;
        this.foods = foods;
        this.settings = settings;
        this.clock = clock;
    }

    @GetMapping("/")
    String home() {
        return "redirect:/meals/new";
    }

    @GetMapping("/meals/new")
    String newMeal(Model model) {
        MealForm form = new MealForm();
        form.setEatenAt(LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES));
        form.getItems().add(new MealForm.Item());
        model.addAttribute("mealForm", form);
        return formView(model, false, null);
    }

    @PostMapping("/meals")
    String create(@ModelAttribute MealForm mealForm, BindingResult result, Model model) {
        Optional<Meal> meal = mealService.log(mealForm, result);
        if (meal.isPresent()) {
            return "redirect:/meals/" + meal.get().getId();
        }
        return failedFormView(mealForm, result, model, null);
    }

    @GetMapping("/meals/{id}/edit")
    String edit(@PathVariable Long id, Model model) {
        Meal meal = meals.findById(id).orElseThrow(NotFoundException::new);
        model.addAttribute("mealForm", MealForm.from(meal));
        return formView(model, false, id);
    }

    @PostMapping("/meals/{id}")
    String update(@PathVariable Long id, @ModelAttribute MealForm mealForm, BindingResult result, Model model) {
        if (mealService.update(id, mealForm, result).isPresent()) {
            return "redirect:/meals/" + id;
        }
        return failedFormView(mealForm, result, model, id);
    }

    @PostMapping("/meals/{id}/delete")
    String delete(@PathVariable Long id, RedirectAttributes redirect) {
        Meal meal = mealService.delete(id);
        redirect.addFlashAttribute("undo", new Undo(
                "Deleted the meal at " + meal.getEatenAt().toLocalTime() + ".", "/meals/" + id + "/restore"));
        return "redirect:/days/" + meal.getEatenAt().toLocalDate();
    }

    @PostMapping("/meals/{id}/restore")
    String restore(@PathVariable Long id) {
        return "redirect:/days/" + mealService.restore(id).getEatenAt().toLocalDate();
    }

    @GetMapping("/meals/{id}")
    String show(@PathVariable Long id, Model model) {
        Meal meal = meals.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        BigDecimal threshold = settings.findById(Settings.ID).map(Settings::getFatThresholdGrams).orElse(null);
        model.addAttribute("meal", meal);
        model.addAttribute("threshold", threshold);
        return "meal";
    }

    private String failedFormView(MealForm mealForm, BindingResult result, Model model, Long mealId) {
        if (mealForm.getItems().isEmpty()) {
            mealForm.getItems().add(new MealForm.Item());
        }
        boolean askConfirmLarge = result.getFieldErrors().stream().anyMatch(e -> "tooLarge".equals(e.getCode()));
        return formView(model, askConfirmLarge, mealId);
    }

    /** {@code mealId} is null for a new meal and set when editing. */
    private String formView(Model model, boolean askConfirmLarge, Long mealId) {
        model.addAttribute("foodNames", foods.findAll(Sort.by("name")).stream().map(Food::getName).toList());
        model.addAttribute("askConfirmLarge", askConfirmLarge);
        model.addAttribute("mealId", mealId);
        return "meal-form";
    }
}
