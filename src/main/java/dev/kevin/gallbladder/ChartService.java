package dev.kevin.gallbladder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChartService {

    static final int DAYS = 30;

    /** One point on the chart: a meal's fat total in grams, or an episode's severity. */
    public record Point(LocalDateTime at, BigDecimal value, long id) {
    }

    /** {@code threshold} is null when no fat limit is set. Covers {@code from} to {@code to}, both included. */
    public record ChartData(LocalDate from, LocalDate to, List<Point> meals, List<Point> episodes, BigDecimal threshold) {
    }

    private final MealRepository meals;
    private final EpisodeRepository episodes;
    private final SettingsRepository settings;
    private final Clock clock;

    public ChartService(MealRepository meals, EpisodeRepository episodes, SettingsRepository settings, Clock clock) {
        this.meals = meals;
        this.episodes = episodes;
        this.settings = settings;
        this.clock = clock;
    }

    /** The last {@value #DAYS} days, today included. */
    @Transactional(readOnly = true)
    public ChartData lastDays() {
        LocalDate today = LocalDate.now(clock);
        return between(today.minusDays(DAYS - 1), today);
    }

    @Transactional(readOnly = true)
    public ChartData between(LocalDate from, LocalDate to) {
        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();
        List<Point> mealPoints = meals.findEatenBetween(start, end).stream()
                .map(m -> new Point(m.getEatenAt(), m.fatTotal(), m.getId())).toList();
        List<Point> episodePoints = episodes.findStartedBetween(start, end).stream()
                .map(e -> new Point(e.getStartedAt(), BigDecimal.valueOf(e.getSeverity()), e.getId())).toList();
        return new ChartData(from, to, mealPoints, episodePoints, settings.current().getFatThresholdGrams());
    }
}
