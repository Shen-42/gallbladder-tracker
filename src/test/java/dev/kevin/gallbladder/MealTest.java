package dev.kevin.gallbladder;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class MealTest {

    @Test
    void fatTotalSumsEachItemsFatByWeight() {
        Meal meal = new Meal(LocalDateTime.now(), null);
        meal.addItem(new Food("Egg", new BigDecimal("10"), FoodSource.CUSTOM), new BigDecimal("100")); // 10 g
        meal.addItem(new Food("Olive oil", new BigDecimal("100"), FoodSource.CUSTOM), new BigDecimal("5")); // 5 g
        meal.addItem(new Food("Rice", new BigDecimal("0.3"), FoodSource.CUSTOM), new BigDecimal("150")); // 0.45 g

        assertThat(meal.fatTotal()).isEqualByComparingTo("15.45");
    }

    @Test
    void emptyMealHasZeroFat() {
        assertThat(new Meal(LocalDateTime.now(), null).fatTotal()).isEqualByComparingTo("0");
    }
}
