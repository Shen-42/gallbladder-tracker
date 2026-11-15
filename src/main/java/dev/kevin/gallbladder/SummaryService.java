package dev.kevin.gallbladder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

@Service
public class SummaryService {

    /** Below this many episodes the page shows only counts, no percentages. */
    static final int MIN_EPISODES_FOR_PERCENTAGES = 5;

    /**
     * A meal is "followed" when an episode starts at or after the meal and at most {@code lookbackHours} later.
     * {@code threshold} is null when no fat limit is set; then every meal counts as within it and the table is hidden.
     * {@code medianOnset} is null when no meal was followed.
     */
    public record Summary(BigDecimal threshold, int lookbackHours,
                          int overFollowed, int overNotFollowed, int withinFollowed, int withinNotFollowed,
                          long episodes, int pendingMeals, Duration medianOnset) {

        public int over() { return overFollowed + overNotFollowed; }
        public int within() { return withinFollowed + withinNotFollowed; }
        public int followed() { return overFollowed + withinFollowed; }
        public int notFollowed() { return overNotFollowed + withinNotFollowed; }
        public int meals() { return over() + within(); }

        public boolean showPercentages() {
            return episodes >= MIN_EPISODES_FOR_PERCENTAGES;
        }

        /** {@code part} as a whole-number percentage of {@code whole}, or null when {@code whole} is 0. */
        public Long percent(int part, int whole) {
            return whole == 0 ? null : Math.round(100.0 * part / whole);
        }

        /** For example "1 h 30 min", or null when there is no median. */
        public String medianOnsetText() {
            if (medianOnset == null) {
                return null;
            }
            long minutes = medianOnset.toMinutes();
            if (minutes < 60) {
                return minutes + " min";
            }
            return minutes % 60 == 0 ? minutes / 60 + " h" : minutes / 60 + " h " + minutes % 60 + " min";
        }
    }

    private final MealRepository meals;
    private final EpisodeRepository episodes;
    private final SettingsRepository settings;
    private final Clock clock;

    public SummaryService(MealRepository meals, EpisodeRepository episodes, SettingsRepository settings, Clock clock) {
        this.meals = meals;
        this.episodes = episodes;
        this.settings = settings;
        this.clock = clock;
    }

    /** Every meal and episode ever logged. */
    @Transactional(readOnly = true)
    public Summary summary() {
        List<Episode> allEpisodes = episodes.findAll();
        return summarize(meals.findAll(), allEpisodes, allEpisodes.size());
    }

    /**
     * Meals eaten and episodes started from {@code from} to {@code to}, both days included.
     * An episode just after the period still counts as following a meal eaten at its end.
     */
    @Transactional(readOnly = true)
    public Summary summary(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        int hours = settings.current().getLookbackHours();
        long episodesInPeriod = episodes.findStartedBetween(start, end).size();
        return summarize(meals.findEatenBetween(start, end), episodes.findStartedBetween(start, end.plusHours(hours)),
                episodesInPeriod);
    }

    private Summary summarize(List<Meal> mealsToCount, List<Episode> candidateEpisodes, long episodeCount) {
        Settings s = settings.current();
        BigDecimal threshold = s.getFatThresholdGrams();
        int hours = s.getLookbackHours();
        LocalDateTime now = LocalDateTime.now(clock);

        List<Outcome> outcomes = outcomes(mealsToCount, candidateEpisodes, hours, now);

        int overFollowed = 0, overNotFollowed = 0, withinFollowed = 0, withinNotFollowed = 0;
        List<Duration> delays = new ArrayList<>();
        for (Outcome outcome : outcomes) {
            Meal meal = outcome.meal();
            boolean over = threshold != null && meal.fatTotal().compareTo(threshold) > 0;
            if (outcome.followed()) {
                delays.add(Duration.between(meal.getEatenAt(), outcome.onset()));
                if (over) overFollowed++; else withinFollowed++;
            } else {
                if (over) overNotFollowed++; else withinNotFollowed++;
            }
        }
        return new Summary(threshold, hours, overFollowed, overNotFollowed, withinFollowed, withinNotFollowed,
                episodeCount, mealsToCount.size() - outcomes.size(), median(delays));
    }

    /** A meal whose outcome is known. {@code onset} is the first episode start within the window, or null if none came. */
    record Outcome(Meal meal, LocalDateTime onset) {
        boolean followed() {
            return onset != null;
        }
    }

    /**
     * The outcome of each meal, in the same order. A meal is followed when an episode starts at or after it and at most
     * {@code lookbackHours} later. Meals whose window is still open with no episode yet are left out, because an
     * episode could still come.
     */
    static List<Outcome> outcomes(List<Meal> meals, List<Episode> episodes, int lookbackHours, LocalDateTime now) {
        TreeSet<LocalDateTime> onsets = new TreeSet<>();
        episodes.forEach(e -> onsets.add(e.getStartedAt()));
        List<Outcome> outcomes = new ArrayList<>();
        for (Meal meal : meals) {
            LocalDateTime windowEnd = meal.getEatenAt().plusHours(lookbackHours);
            LocalDateTime onset = onsets.ceiling(meal.getEatenAt());
            boolean followed = onset != null && !onset.isAfter(windowEnd);
            if (followed || !windowEnd.isAfter(now)) {
                outcomes.add(new Outcome(meal, followed ? onset : null));
            }
        }
        return outcomes;
    }

    static Duration median(List<Duration> values) {
        if (values.isEmpty()) {
            return null;
        }
        List<Duration> sorted = values.stream().sorted().toList();
        int mid = sorted.size() / 2;
        return sorted.size() % 2 == 1 ? sorted.get(mid) : sorted.get(mid - 1).plus(sorted.get(mid)).dividedBy(2);
    }
}
