package dev.kevin.gallbladder;

import dev.kevin.gallbladder.MealForm.Item;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class MealService {

    static final BigDecimal LARGE_GRAMS = new BigDecimal("2000");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final FoodRepository foods;
    private final MealRepository meals;
    private final Clock clock;

    public MealService(FoodRepository foods, MealRepository meals, Clock clock) {
        this.foods = foods;
        this.meals = meals;
        this.clock = clock;
    }

    /** A valid form, turned into changes that have not been applied yet. */
    private record Draft(List<Line> lines, Collection<Food> newFoods, Map<Food, BigDecimal> portionUpdates) {
    }

    private record Line(Food food, BigDecimal grams) {
    }

    /**
     * Checks the form, recording problems in {@code errors} next to the field they belong to.
     * Saves nothing unless the whole form is valid.
     */
    @Transactional
    public Optional<Meal> log(MealForm form, Errors errors) {
        return check(form, errors).map(draft -> {
            Meal meal = new Meal(form.getEatenAt(), blankToNull(form.getNote()));
            apply(draft, meal);
            return meals.save(meal);
        });
    }

    /** Like {@link #log}, but replaces the time, note and foods of an existing meal. */
    @Transactional
    public Optional<Meal> update(long id, MealForm form, Errors errors) {
        Meal meal = meals.findById(id).orElseThrow(NotFoundException::new);
        return check(form, errors).map(draft -> {
            meal.update(form.getEatenAt(), blankToNull(form.getNote()));
            meal.getItems().clear();
            apply(draft, meal);
            return meal;
        });
    }

    /** Hides the meal everywhere until {@link #restore} is called. Returns it so the caller can say what was deleted. */
    @Transactional
    public Meal delete(long id) {
        Meal meal = meals.findById(id).orElseThrow(NotFoundException::new);
        meal.markDeleted(LocalDateTime.now(clock));
        return meal;
    }

    @Transactional
    public Meal restore(long id) {
        if (meals.restore(id) == 0) {
            throw new NotFoundException();
        }
        return meals.findById(id).orElseThrow(NotFoundException::new);
    }

    private Optional<Draft> check(MealForm form, Errors errors) {
        LocalDateTime eatenAt = form.getEatenAt();
        if (eatenAt == null) {
            errors.rejectValue("eatenAt", "required", "Enter when you ate.");
        } else if (eatenAt.isAfter(LocalDateTime.now(clock))) {
            errors.rejectValue("eatenAt", "future", "That time is in the future. Pick now or earlier.");
        }
        if (form.getItems().stream().allMatch(Item::isBlank)) {
            errors.reject("noItems", "Add at least one food.");
        }

        List<Line> lines = new ArrayList<>();
        Map<String, Food> newFoods = new HashMap<>();
        // Existing foods are managed entities, so their changes wait until the form is known to be valid.
        Map<Food, BigDecimal> portionUpdates = new IdentityHashMap<>();

        for (int i = 0; i < form.getItems().size(); i++) {
            Item item = form.getItems().get(i);
            if (item.isBlank()) {
                continue;
            }
            String row = "items[" + i + "].";
            String name = item.getFoodName() == null ? "" : item.getFoodName().trim();
            if (name.isEmpty()) {
                errors.rejectValue(row + "foodName", "required", "Enter a food name.");
                continue;
            }
            if (!isPositive(item.getAmount())) {
                errors.rejectValue(row + "amount", "positive", "Enter an amount above 0.");
                continue;
            }

            String key = name.toLowerCase(Locale.ROOT);
            Food food = foods.findByNameIgnoreCase(name).orElseGet(() -> newFoods.get(key));
            if (food == null) {
                BigDecimal fat = item.getFatPer100g();
                if (fat == null) {
                    errors.rejectValue(row + "fatPer100g", "required",
                            "\"" + name + "\" is a new food. Enter its fat per 100 g.");
                    continue;
                }
                if (fat.signum() < 0 || fat.compareTo(HUNDRED) > 0) {
                    errors.rejectValue(row + "fatPer100g", "range", "Fat per 100 g must be between 0 and 100.");
                    continue;
                }
                food = new Food(name, fat, FoodSource.CUSTOM);
                newFoods.put(key, food);
            }

            BigDecimal grams = item.getAmount();
            if (item.getUnit() == MealForm.Unit.PORTIONS) {
                BigDecimal portion = item.getPortionGrams() != null ? item.getPortionGrams() : food.getPortionGrams();
                if (!isPositive(portion)) {
                    errors.rejectValue(row + "portionGrams", "required",
                            "Enter how many grams one portion of " + name + " weighs.");
                    continue;
                }
                if (item.getPortionGrams() != null) {
                    portionUpdates.put(food, portion);
                }
                grams = grams.multiply(portion);
            }
            if (grams.compareTo(LARGE_GRAMS) > 0 && !form.isConfirmLarge()) {
                errors.rejectValue(row + "amount", "tooLarge", "That is " + grams.stripTrailingZeros().toPlainString()
                        + " g, over 2,000 g. Fix the amount, or tick the box below if it is right.");
            }
            lines.add(new Line(food, grams));
        }

        if (errors.hasErrors()) {
            return Optional.empty();
        }
        return Optional.of(new Draft(lines, newFoods.values(), portionUpdates));
    }

    private void apply(Draft draft, Meal meal) {
        draft.portionUpdates().forEach(Food::setPortionGrams);
        foods.saveAll(draft.newFoods());
        draft.lines().forEach(line -> meal.addItem(line.food(), line.grams()));
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
