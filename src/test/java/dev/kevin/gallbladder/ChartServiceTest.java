package dev.kevin.gallbladder;

import dev.kevin.gallbladder.ChartService.ChartData;
import dev.kevin.gallbladder.ChartService.Point;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ChartServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 12, 0);

    @Autowired MealRepository meals;
    @Autowired EpisodeRepository episodes;
    @Autowired SettingsRepository settings;
    @Autowired FoodRepository foods;
    ChartService service;
    Food oil;

    @BeforeEach
    void setUp() {
        service = new ChartService(meals, episodes, settings,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.of("UTC")));
        oil = foods.save(new Food("Olive oil", new BigDecimal("100"), FoodSource.CUSTOM));
    }

    @Test
    void coversTheLast30DaysIncludingToday() {
        LocalDateTime firstDay = LocalDate.of(2026, 10, 17).atStartOfDay();
        meal(firstDay.minusMinutes(1), "99");  // 31 days ago, left out
        meal(firstDay, "12.5");
        meal(NOW.minusHours(1), "20");
        episodes.save(new Episode(NOW.minusMinutes(30), null, 6, null, null));
        episodes.save(new Episode(firstDay.minusHours(1), null, 3, null, null)); // left out

        ChartData data = service.lastDays();

        assertThat(data.from()).isEqualTo(LocalDate.of(2026, 10, 17));
        assertThat(data.to()).isEqualTo(LocalDate.of(2026, 11, 15));
        assertThat(data.meals()).extracting(Point::value).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("12.5"), new BigDecimal("20"));
        assertThat(data.episodes()).singleElement().satisfies(p -> {
            assertThat(p.at()).isEqualTo(NOW.minusMinutes(30));
            assertThat(p.value()).isEqualByComparingTo("6");
        });
        assertThat(data.threshold()).isNull();
    }

    @Test
    void carriesTheFatLimit() {
        Settings s = new Settings();
        s.setFatThresholdGrams(new BigDecimal("15"));
        settings.save(s);

        assertThat(service.lastDays().threshold()).isEqualByComparingTo("15");
    }

    private void meal(LocalDateTime at, String fatGrams) {
        Meal meal = new Meal(at, null);
        meal.addItem(oil, new BigDecimal(fatGrams));
        meals.save(meal);
    }
}
