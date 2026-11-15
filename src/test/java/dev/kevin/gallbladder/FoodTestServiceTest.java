package dev.kevin.gallbladder;

import dev.kevin.gallbladder.FoodTestService.FoodResult;
import dev.kevin.gallbladder.FoodTestService.Report;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
class FoodTestServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 12, 0);

    @Autowired MealRepository meals;
    @Autowired EpisodeRepository episodes;
    @Autowired SettingsRepository settings;
    @Autowired FoodRepository foods;
    FoodTestService service;

    @BeforeEach
    void setUp() {
        service = new FoodTestService(meals, episodes, settings,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.of("UTC")));
    }

    @Test
    void pValueMatchesTheExactProbability() {
        // 30 meals, 10 followed; the food is in 10 meals, 6 of them followed.
        boolean[] followed = new boolean[30];
        for (int i : new int[]{0, 1, 2, 3, 4, 5, 10, 11, 12, 13}) followed[i] = true;
        int[] food = IntStream.range(0, 10).toArray();

        double p = FoodTestService.pValues(followed, Map.of("x", food), 20_000, new Random(1)).get("x");

        assertThat(p).isCloseTo(hypergeometricAtLeast(6, 30, 10, 10), within(0.01));
    }

    @Test
    void extremeResultsGetTheSmallestAndLargestPValues() {
        boolean[] followed = new boolean[20];
        for (int i = 0; i < 5; i++) followed[i] = true;
        Map<String, int[]> foods = Map.of("always", new int[]{0, 1, 2, 3, 4}, "never", new int[]{15, 16, 17, 18, 19});

        Map<String, Double> p = FoodTestService.pValues(followed, foods, 10_000, new Random(1));

        assertThat(p.get("always")).isLessThan(0.001);
        assertThat(p.get("never")).isEqualTo(1.0);
    }

    @Test
    void reportTestsOnlyFoodsWithEnoughMealsAndSortsByPValue() {
        Food pork = food("Pork belly"), rice = food("Rice"), soup = food("Soup");
        // 12 days, two meals a day: pork at noon, rice at 18:00. Pain follows 6 of the pork meals.
        for (int d = 1; d <= 12; d++) {
            LocalDateTime day = NOW.minusDays(d).withHour(0);
            meal(day.withHour(12), pork, rice);
            meal(day.withHour(18), rice);
            if (d <= 6) episodes.save(new Episode(day.withHour(14), null, 5, null, null));
        }
        meal(NOW.minusDays(1).withHour(21), soup);

        Report r = service.report();

        assertThat(r.enoughEpisodes()).isTrue();
        assertThat(r.meals()).isEqualTo(25);
        assertThat(r.followed()).isEqualTo(6);
        assertThat(r.tooFew()).containsExactly(Map.entry("Soup", 1));
        assertThat(r.results()).extracting(FoodResult::food).containsExactly("Pork belly", "Rice");
        FoodResult porkResult = r.results().get(0);
        assertThat(porkResult.meals()).isEqualTo(12);
        assertThat(porkResult.followed()).isEqualTo(6);
        assertThat(porkResult.otherMeals()).isEqualTo(13);
        assertThat(porkResult.otherFollowed()).isZero();
        assertThat(porkResult.pValue()).isLessThan(0.01);
    }

    @Test
    void nothingIsTestedBeforeEnoughEpisodes() {
        Food rice = food("Rice");
        for (int d = 1; d <= 12; d++) meal(NOW.minusDays(d), rice);
        for (int d = 1; d <= 4; d++) episodes.save(new Episode(NOW.minusDays(d).plusHours(1), null, 5, null, null));

        Report r = service.report();

        assertThat(r.enoughEpisodes()).isFalse();
        assertThat(r.results()).isEmpty();
    }

    @Test
    void resultReadsAsOnePlainSentence() {
        FoodResult r = new FoodResult("Pork belly", 12, 6, 13, 1, 0.031);

        assertThat(r.sentence()).isEqualTo("Meals with Pork belly were followed by pain 6 of 12 times, against 1 of 13 "
                + "meals without it. If Pork belly made no difference, a result at least this high would come up "
                + "about 3 times in 100 by chance (p = 0.03).");
        assertThat(new FoodResult("x", 1, 1, 1, 1, 0.0001).pText()).isEqualTo("p < 0.001");
        assertThat(new FoodResult("x", 1, 1, 1, 1, 0.004).sentence()).contains("about 4 times in 1,000 by chance (p = 0.004)");
        assertThat(new FoodResult("x", 1, 1, 1, 1, 0.007).sentence()).contains("about 7 times in 1,000 by chance (p = 0.007)");
        assertThat(new FoodResult("x", 1, 1, 1, 1, 1.0).sentence()).contains("almost every time by chance (p = 1.00)");
    }

    /** P(X >= k) for X ~ Hypergeometric(population, successes, draws). */
    private static double hypergeometricAtLeast(int k, int population, int successes, int draws) {
        double p = 0;
        for (int x = k; x <= Math.min(successes, draws); x++) {
            p += choose(successes, x) * choose(population - successes, draws - x) / choose(population, draws);
        }
        return p;
    }

    private static double choose(int n, int k) {
        double c = 1;
        for (int i = 1; i <= k; i++) c = c * (n - k + i) / i;
        return c;
    }

    private Food food(String name) {
        return foods.save(new Food(name, new BigDecimal("10"), FoodSource.CUSTOM));
    }

    private void meal(LocalDateTime at, Food... items) {
        Meal meal = new Meal(at, null);
        for (Food f : items) meal.addItem(f, new BigDecimal("100"));
        meals.save(meal);
    }
}
