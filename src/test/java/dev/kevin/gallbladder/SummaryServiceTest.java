package dev.kevin.gallbladder;

import dev.kevin.gallbladder.SummaryService.Summary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SummaryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 12, 0);

    @Autowired MealRepository meals;
    @Autowired EpisodeRepository episodes;
    @Autowired SettingsRepository settings;
    @Autowired FoodRepository foods;
    SummaryService service;
    Food oil;

    @BeforeEach
    void setUp() {
        service = new SummaryService(meals, episodes, settings,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.of("UTC")));
        oil = foods.save(new Food("Olive oil", new BigDecimal("100"), FoodSource.CUSTOM));
    }

    @Test
    void countsMealsByLimitAndWhetherAnEpisodeFollowedWithinTheWindow() {
        settings("15", 6);
        LocalDateTime a = NOW.minusDays(1), b = NOW.minusDays(2), c = NOW.minusDays(3), d = NOW.minusDays(4);
        meal(a, "20");                     // over, pain 2 h later
        episode(a.plusHours(2));
        meal(b, "20");                     // over, no pain
        meal(c, "15");                     // exactly at the limit counts as within; pain right at the window's end
        episode(c.plusHours(6));
        meal(d, "5");                      // within; pain one minute too late
        episode(d.plusHours(6).plusMinutes(1));

        Summary s = service.summary();

        assertThat(List.of(s.overFollowed(), s.overNotFollowed(), s.withinFollowed(), s.withinNotFollowed()))
                .containsExactly(1, 1, 1, 1);
        assertThat(s.episodes()).isEqualTo(3);
        assertThat(s.medianOnset()).isEqualTo(Duration.ofHours(4)); // median of 2 h and 6 h
    }

    @Test
    void anEpisodeBeforeTheMealDoesNotCount() {
        settings("15", 6);
        LocalDateTime t = NOW.minusDays(1);
        episode(t.minusMinutes(1));
        meal(t, "5");

        Summary s = service.summary();

        assertThat(s.withinNotFollowed()).isOne();
        assertThat(s.medianOnset()).isNull();
    }

    @Test
    void recentMealsWaitUntilTheirWindowEndsUnlessPainAlreadyFollowed() {
        settings("15", 6);
        meal(NOW.minusHours(30), "5");     // window ended long ago without pain: counted
        meal(NOW.minusHours(4), "20");     // window still open, but pain already came: counted
        episode(NOW.minusHours(3));
        meal(NOW.minusHours(2), "5");      // window still open, no pain since: not counted yet

        Summary s = service.summary();

        assertThat(s.pendingMeals()).isOne();
        assertThat(s.overFollowed()).isOne();
        assertThat(s.withinNotFollowed()).isOne();
        assertThat(s.meals()).isEqualTo(2);
    }

    @Test
    void usesTheSixHourDefaultAndPutsEveryMealWithinWhenNoLimitIsSet() {
        LocalDateTime t = NOW.minusDays(1);
        meal(t, "500");
        episode(t.plusHours(6));

        Summary s = service.summary();

        assertThat(s.threshold()).isNull();
        assertThat(s.lookbackHours()).isEqualTo(6);
        assertThat(s.withinFollowed()).isOne();
        assertThat(s.over()).isZero();
    }

    @Test
    void percentagesOnlyFromFiveEpisodes() {
        assertThat(summaryWithEpisodes(4).showPercentages()).isFalse();
        assertThat(summaryWithEpisodes(5).showPercentages()).isTrue();
        assertThat(summaryWithEpisodes(5).percent(1, 3)).isEqualTo(33);
        assertThat(summaryWithEpisodes(5).percent(0, 0)).isNull();
    }

    @Test
    void medianOfOddAndEvenCounts() {
        assertThat(SummaryService.median(List.of())).isNull();
        assertThat(SummaryService.median(List.of(Duration.ofMinutes(90), Duration.ofMinutes(10), Duration.ofMinutes(30))))
                .isEqualTo(Duration.ofMinutes(30));
        assertThat(SummaryService.median(List.of(Duration.ofMinutes(10), Duration.ofMinutes(40))))
                .isEqualTo(Duration.ofMinutes(25));
    }

    @Test
    void medianIsWrittenInHoursAndMinutes() {
        assertThat(summaryWithOnset(Duration.ofMinutes(45)).medianOnsetText()).isEqualTo("45 min");
        assertThat(summaryWithOnset(Duration.ofMinutes(120)).medianOnsetText()).isEqualTo("2 h");
        assertThat(summaryWithOnset(Duration.ofMinutes(150)).medianOnsetText()).isEqualTo("2 h 30 min");
    }

    private static Summary summaryWithEpisodes(long episodes) {
        return new Summary(null, 6, 0, 0, 0, 0, episodes, 0, null);
    }

    private static Summary summaryWithOnset(Duration onset) {
        return new Summary(null, 6, 0, 0, 0, 0, 0, 0, onset);
    }

    private void settings(String threshold, int hours) {
        Settings s = new Settings();
        s.setFatThresholdGrams(new BigDecimal(threshold));
        s.setLookbackHours(hours);
        settings.save(s);
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
