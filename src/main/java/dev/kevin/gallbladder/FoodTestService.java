package dev.kevin.gallbladder;

import dev.kevin.gallbladder.SummaryService.Outcome;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * For each food, whether meals containing it were followed by pain more often than chance would explain.
 * The test shuffles which meals were followed by pain, keeping how many were, and counts how often a food's meals
 * come out at least as often followed as they really were.
 */
@Service
public class FoodTestService {

    /** Foods eaten in fewer meals than this get no result. */
    static final int MIN_MEALS_PER_FOOD = 10;
    static final int PERMUTATIONS = 10_000;
    /** Fixed so the page shows the same p-values each time it loads. */
    private static final long SEED = 20261115L;

    /**
     * {@code followed} of the food's {@code meals} were followed by pain, against {@code otherFollowed} of
     * {@code otherMeals} without it. {@code pValue} is one-sided: the chance of at least {@code followed} if the food
     * made no difference.
     */
    public record FoodResult(String food, int meals, int followed, int otherMeals, int otherFollowed, double pValue) {

        /** For example "p = 0.03", or "p < 0.001". */
        public String pText() {
            return pValue < 0.001 ? "p < 0.001" : "p = " + String.format(java.util.Locale.ROOT, pValue < 0.01 ? "%.3f" : "%.2f", pValue);
        }

        /** The result in one plain sentence. */
        public String sentence() {
            long per100 = Math.round(pValue * 100);
            String chance = pValue < 0.001 ? "fewer than 1 time in 1,000"
                    : pValue < 0.01 ? "about " + Math.round(pValue * 1000) + " times in 1,000"
                    : per100 >= 100 ? "almost every time"
                    : "about " + per100 + (per100 == 1 ? " time" : " times") + " in 100";
            return "Meals with " + food + " were followed by pain " + followed + " of " + meals + " times, against "
                    + otherFollowed + " of " + otherMeals + " meals without it. If " + food + " made no difference, "
                    + "a result at least this high would come up " + chance + " by chance (" + pText() + ").";
        }
    }

    /** {@code results} sorted by p-value; {@code tooFew} maps each left-out food to its meal count. */
    public record Report(boolean enoughEpisodes, int meals, int followed, int lookbackHours,
                         List<FoodResult> results, Map<String, Integer> tooFew) {
    }

    private final MealRepository meals;
    private final EpisodeRepository episodes;
    private final SettingsRepository settings;
    private final Clock clock;

    public FoodTestService(MealRepository meals, EpisodeRepository episodes, SettingsRepository settings, Clock clock) {
        this.meals = meals;
        this.episodes = episodes;
        this.settings = settings;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Report report() {
        int hours = settings.current().getLookbackHours();
        List<Episode> allEpisodes = episodes.findAll();
        List<Outcome> outcomes = SummaryService.outcomes(meals.findAll(), allEpisodes, hours, LocalDateTime.now(clock));

        boolean[] followed = new boolean[outcomes.size()];
        Map<String, List<Integer>> mealsByFood = new LinkedHashMap<>();
        for (int i = 0; i < outcomes.size(); i++) {
            followed[i] = outcomes.get(i).followed();
            int meal = i;
            outcomes.get(i).meal().getItems().stream().map(item -> item.getFood().getName()).distinct()
                    .forEach(food -> mealsByFood.computeIfAbsent(food, k -> new ArrayList<>()).add(meal));
        }
        int totalFollowed = count(followed);

        Map<String, Integer> tooFew = new LinkedHashMap<>();
        Map<String, int[]> tested = new LinkedHashMap<>();
        mealsByFood.forEach((food, idx) -> {
            if (idx.size() < MIN_MEALS_PER_FOOD) {
                tooFew.put(food, idx.size());
            } else {
                tested.put(food, idx.stream().mapToInt(Integer::intValue).toArray());
            }
        });

        boolean enoughEpisodes = allEpisodes.size() >= SummaryService.MIN_EPISODES_FOR_PERCENTAGES
                && totalFollowed > 0 && totalFollowed < outcomes.size();
        List<FoodResult> results = new ArrayList<>();
        if (enoughEpisodes && !tested.isEmpty()) {
            Map<String, Double> p = pValues(followed, tested, PERMUTATIONS, new Random(SEED));
            tested.forEach((food, idx) -> {
                int f = countAt(followed, idx);
                results.add(new FoodResult(food, idx.length, f, outcomes.size() - idx.length, totalFollowed - f,
                        p.get(food)));
            });
            results.sort(Comparator.comparingDouble(FoodResult::pValue).thenComparing(FoodResult::food));
        }
        return new Report(enoughEpisodes, outcomes.size(), totalFollowed, hours, results, tooFew);
    }

    /**
     * One-sided permutation p-values, (hits + 1) / (permutations + 1), where a hit is a shuffle giving the food's meals
     * at least as many followed as observed. All foods share each shuffle.
     */
    static Map<String, Double> pValues(boolean[] followed, Map<String, int[]> mealsByFood, int permutations, Random random) {
        Map<String, Integer> observed = new LinkedHashMap<>();
        Map<String, Integer> hits = new LinkedHashMap<>();
        mealsByFood.forEach((food, idx) -> {
            observed.put(food, countAt(followed, idx));
            hits.put(food, 0);
        });
        boolean[] shuffled = followed.clone();
        for (int r = 0; r < permutations; r++) {
            shuffle(shuffled, random);
            mealsByFood.forEach((food, idx) -> {
                if (countAt(shuffled, idx) >= observed.get(food)) {
                    hits.merge(food, 1, Integer::sum);
                }
            });
        }
        Map<String, Double> p = new LinkedHashMap<>();
        hits.forEach((food, h) -> p.put(food, (h + 1.0) / (permutations + 1.0)));
        return p;
    }

    private static void shuffle(boolean[] values, Random random) {
        for (int i = values.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            boolean t = values[i];
            values[i] = values[j];
            values[j] = t;
        }
    }

    private static int countAt(boolean[] values, int[] idx) {
        int n = 0;
        for (int i : idx) {
            if (values[i]) n++;
        }
        return n;
    }

    private static int count(boolean[] values) {
        int n = 0;
        for (boolean v : values) {
            if (v) n++;
        }
        return n;
    }
}
