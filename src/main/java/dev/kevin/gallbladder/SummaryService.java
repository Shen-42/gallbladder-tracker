package dev.kevin.gallbladder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
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

    @Transactional(readOnly = true)
    public Summary summary() {
        Settings s = settings.current();
        BigDecimal threshold = s.getFatThresholdGrams();
        int hours = s.getLookbackHours();
        LocalDateTime now = LocalDateTime.now(clock);

        TreeSet<LocalDateTime> onsets = new TreeSet<>();
        List<Episode> allEpisodes = episodes.findAll();
        allEpisodes.forEach(e -> onsets.add(e.getStartedAt()));

        int overFollowed = 0, overNotFollowed = 0, withinFollowed = 0, withinNotFollowed = 0, pending = 0;
        List<Duration> delays = new ArrayList<>();
        for (Meal meal : meals.findAll()) {
            LocalDateTime eaten = meal.getEatenAt();
            LocalDateTime windowEnd = eaten.plusHours(hours);
            LocalDateTime onset = onsets.ceiling(eaten);
            boolean followed = onset != null && !onset.isAfter(windowEnd);
            if (!followed && windowEnd.isAfter(now)) {
                pending++; // an episode could still come, so it can't count as "not followed" yet
                continue;
            }
            boolean over = threshold != null && meal.fatTotal().compareTo(threshold) > 0;
            if (followed) {
                delays.add(Duration.between(eaten, onset));
                if (over) overFollowed++; else withinFollowed++;
            } else {
                if (over) overNotFollowed++; else withinNotFollowed++;
            }
        }
        return new Summary(threshold, hours, overFollowed, overNotFollowed, withinFollowed, withinNotFollowed,
                allEpisodes.size(), pending, median(delays));
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
