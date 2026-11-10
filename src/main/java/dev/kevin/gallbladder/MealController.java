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
        return formView(model, false);
    }

    @PostMapping("/meals")
    String create(@ModelAttribute MealForm mealForm, BindingResult result, Model model) {
        Optional<Meal> meal = mealService.log(mealForm, result);
        if (meal.isPresent()) {
            return "redirect:/meals/" + meal.get().getId();
        }
        if (mealForm.getItems().isEmpty()) {
            mealForm.getItems().add(new MealForm.Item());
        }
        boolean askConfirmLarge = result.getFieldErrors().stream().anyMatch(e -> "tooLarge".equals(e.getCode()));
        return formView(model, askConfirmLarge);
    }

    @GetMapping("/meals/{id}")
    String show(@PathVariable Long id, Model model) {
        Meal meal = meals.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        BigDecimal threshold = settings.findById(Settings.ID).map(Settings::getFatThresholdGrams).orElse(null);
        model.addAttribute("meal", meal);
        model.addAttribute("threshold", threshold);
        return "meal";
    }

    private String formView(Model model, boolean askConfirmLarge) {
        model.addAttribute("foodNames", foods.findAll(Sort.by("name")).stream().map(Food::getName).toList());
        model.addAttribute("askConfirmLarge", askConfirmLarge);
        return "meal-form";
    }
}
