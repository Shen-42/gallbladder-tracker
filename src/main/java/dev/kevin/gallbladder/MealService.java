package dev.kevin.gallbladder;

import dev.kevin.gallbladder.MealForm.Item;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.IdentityHashMap;
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

    /**
     * Checks the form, recording problems in {@code errors} next to the field they belong to.
     * Saves nothing unless the whole form is valid.
     */
    @Transactional
    public Optional<Meal> log(MealForm form, Errors errors) {
        LocalDateTime eatenAt = form.getEatenAt();
        if (eatenAt == null) {
            errors.rejectValue("eatenAt", "required", "Enter when you ate.");
        } else if (eatenAt.isAfter(LocalDateTime.now(clock))) {
            errors.rejectValue("eatenAt", "future", "That time is in the future. Pick now or earlier.");
        }
        if (form.getItems().stream().allMatch(Item::isBlank)) {
            errors.reject("noItems", "Add at least one food.");
        }

        Meal meal = new Meal(eatenAt, blankToNull(form.getNote()));
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
            meal.addItem(food, grams);
        }

        if (errors.hasErrors()) {
            return Optional.empty();
        }
        portionUpdates.forEach(Food::setPortionGrams);
        foods.saveAll(newFoods.values());
        return Optional.of(meals.save(meal));
    }

    private static boolean isPositive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
