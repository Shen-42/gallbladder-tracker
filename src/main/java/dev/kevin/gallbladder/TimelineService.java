package dev.kevin.gallbladder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class TimelineService {

    /** One line on the timeline: exactly one of {@code meal} and {@code episode} is set. */
    public record Entry(LocalDateTime at, Meal meal, Episode episode, boolean overThreshold) {
    }

    private final MealRepository meals;
    private final EpisodeRepository episodes;
    private final SettingsRepository settings;

    public TimelineService(MealRepository meals, EpisodeRepository episodes, SettingsRepository settings) {
        this.meals = meals;
        this.episodes = episodes;
        this.settings = settings;
    }

    /** Meals and episodes that started on {@code date}, in time order. A meal and an episode at the same minute list the meal first. */
    @Transactional(readOnly = true)
    public List<Entry> day(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        BigDecimal threshold = settings.findById(Settings.ID).map(Settings::getFatThresholdGrams).orElse(null);

        List<Entry> entries = new ArrayList<>();
        for (Meal meal : meals.findEatenBetween(from, to)) {
            boolean over = threshold != null && meal.fatTotal().compareTo(threshold) > 0;
            entries.add(new Entry(meal.getEatenAt(), meal, null, over));
        }
        for (Episode episode : episodes.findStartedBetween(from, to)) {
            entries.add(new Entry(episode.getStartedAt(), null, episode, false));
        }
        entries.sort(Comparator.comparing(Entry::at)); // stable, so meals stay ahead of episodes on ties
        return entries;
    }
}
