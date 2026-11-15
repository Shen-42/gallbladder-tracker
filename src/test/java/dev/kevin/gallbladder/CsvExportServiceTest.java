package dev.kevin.gallbladder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class CsvExportServiceTest {

    private static final LocalDateTime T = LocalDateTime.of(2026, 11, 15, 8, 30);

    @Autowired MealRepository meals;
    @Autowired EpisodeRepository episodes;
    @Autowired FoodRepository foods;
    CsvExportService service;

    @BeforeEach
    void setUp() {
        service = new CsvExportService(meals, episodes);
    }

    @Test
    void mealsHaveOneRowPerFood() {
        Food oats = foods.save(new Food("Oats", new BigDecimal("7.00"), FoodSource.CUSTOM));
        Food milk = foods.save(new Food("Milk, whole", new BigDecimal("3.5"), FoodSource.CUSTOM));
        Meal meal = new Meal(T, "said \"no sugar\"");
        meal.addItem(oats, new BigDecimal("50"));
        meal.addItem(milk, new BigDecimal("200.0"));
        long id = meals.save(meal).getId();

        assertThat(service.mealsCsv()).isEqualTo(
                "meal_id,eaten_at,food,grams,fat_per_100g,fat_g,meal_fat_total_g,note\r\n"
                + id + ",2026-11-15T08:30,Oats,50,7,3.5,10.5,\"said \"\"no sugar\"\"\"\r\n"
                + id + ",2026-11-15T08:30,\"Milk, whole\",200,3.5,7,10.5,\"said \"\"no sugar\"\"\"\r\n");
    }

    @Test
    void episodesLeaveMissingFieldsEmpty() {
        long id = episodes.save(Episode.quick(T, 6)).getId();

        assertThat(service.episodesCsv()).isEqualTo(
                "episode_id,started_at,ended_at,severity,location,note,incomplete\r\n"
                + id + ",2026-11-15T08:30,,6,,,true\r\n");
    }

    @Test
    void deletedEntriesAreLeftOut() {
        Episode episode = episodes.save(new Episode(T, null, 3, null, null));
        episode.markDeleted(T);
        episodes.saveAndFlush(episode);

        assertThat(service.episodesCsv().lines()).hasSize(1);
    }

    @Test
    void zipHoldsBothFiles() throws IOException {
        episodes.save(new Episode(T, null, 3, null, null));

        Map<String, String> files = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(service.zip()))) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                files.put(e.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }

        assertThat(files).containsOnlyKeys("meals.csv", "episodes.csv");
        assertThat(files.get("episodes.csv")).isEqualTo(service.episodesCsv());
    }

    @Test
    void escapesOnlyWhenNeeded() {
        assertThat(CsvExportService.escape("plain")).isEqualTo("plain");
        assertThat(CsvExportService.escape("a\nb")).isEqualTo("\"a\nb\"");
        assertThat(CsvExportService.escape(null)).isEmpty();
    }
}
