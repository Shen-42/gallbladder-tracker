package dev.kevin.gallbladder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class TimelineServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 11, 12);

    @Autowired MealRepository meals;
    @Autowired EpisodeRepository episodes;
    @Autowired SettingsRepository settings;
    @Autowired FoodRepository foods;
    TimelineService timeline;
    Food oil;

    @BeforeEach
    void setUp() {
        timeline = new TimelineService(meals, episodes, settings);
        oil = foods.save(new Food("Olive oil", new BigDecimal("100"), FoodSource.CUSTOM));
    }

    @Test
    void mergesMealsAndEpisodesInTimeOrderForThatDayOnly() {
        meal(DAY.atTime(0, 0), "5");
        meal(DAY.atTime(12, 30), "5");
        episode(DAY.atTime(9, 0));
        episode(DAY.atTime(12, 30));
        meal(DAY.minusDays(1).atTime(23, 59), "5");
        episode(DAY.plusDays(1).atStartOfDay());

        List<TimelineService.Entry> entries = timeline.day(DAY);

        assertThat(entries).extracting(TimelineService.Entry::at).containsExactly(
                DAY.atTime(0, 0), DAY.atTime(9, 0), DAY.atTime(12, 30), DAY.atTime(12, 30));
        assertThat(entries.get(2).meal()).as("meal before episode at the same minute").isNotNull();
        assertThat(entries.get(3).episode()).isNotNull();
    }

    @Test
    void marksOnlyMealsOverTheThreshold() {
        Settings s = new Settings();
        s.setFatThresholdGrams(new BigDecimal("15"));
        settings.save(s);
        meal(DAY.atTime(8, 0), "15");   // exactly at the limit: not over
        meal(DAY.atTime(13, 0), "20");  // over

        assertThat(timeline.day(DAY)).extracting(TimelineService.Entry::overThreshold).containsExactly(false, true);
    }

    @Test
    void marksNothingWhenNoThresholdIsSet() {
        meal(DAY.atTime(8, 0), "500");

        assertThat(timeline.day(DAY)).extracting(TimelineService.Entry::overThreshold).containsExactly(false);
    }

    /** A meal of {@code fatGrams} grams of oil, so its fat total equals {@code fatGrams}. */
    private void meal(LocalDateTime at, String fatGrams) {
        Meal meal = new Meal(at, null);
        meal.addItem(oil, new BigDecimal(fatGrams));
        meals.save(meal);
    }

    private void episode(LocalDateTime at) {
        episodes.save(new Episode(at, null, 5, null, null));
    }
}
