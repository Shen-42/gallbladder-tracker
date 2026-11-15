package dev.kevin.gallbladder;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Every meal and episode as CSV, for backup and analysis elsewhere. Deleted entries are left out. */
@Service
public class CsvExportService {

    private final MealRepository meals;
    private final EpisodeRepository episodes;

    public CsvExportService(MealRepository meals, EpisodeRepository episodes) {
        this.meals = meals;
        this.episodes = episodes;
    }

    /** One row per food eaten, so a meal with three foods has three rows sharing its meal_id. */
    @Transactional(readOnly = true)
    public String mealsCsv() {
        StringBuilder csv = new StringBuilder();
        row(csv, "meal_id", "eaten_at", "food", "grams", "fat_per_100g", "fat_g", "meal_fat_total_g", "note");
        for (Meal meal : meals.findAll(Sort.by("eatenAt", "id"))) {
            String total = plain(meal.fatTotal());
            for (MealItem item : meal.getItems()) {
                row(csv, meal.getId().toString(), meal.getEatenAt().toString(), item.getFood().getName(),
                        plain(item.getGrams()), plain(item.getFood().getFatPer100g()), plain(item.fatGrams()),
                        total, meal.getNote());
            }
        }
        return csv.toString();
    }

    @Transactional(readOnly = true)
    public String episodesCsv() {
        StringBuilder csv = new StringBuilder();
        row(csv, "episode_id", "started_at", "ended_at", "severity", "location", "note", "incomplete");
        for (Episode e : episodes.findAll(Sort.by("startedAt", "id"))) {
            row(csv, e.getId().toString(), e.getStartedAt().toString(),
                    e.getEndedAt() == null ? null : e.getEndedAt().toString(),
                    String.valueOf(e.getSeverity()), e.getLocation(), e.getNote(), String.valueOf(e.isIncomplete()));
        }
        return csv.toString();
    }

    /** A zip holding meals.csv and episodes.csv. */
    public byte[] zip() {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            add(zip, "meals.csv", mealsCsv());
            add(zip, "episodes.csv", episodesCsv());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static void add(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private static void row(StringBuilder csv, String... fields) {
        csv.append(Arrays.stream(fields).map(CsvExportService::escape).collect(Collectors.joining(","))).append("\r\n");
    }

    /** RFC 4180: quote a field holding a comma, quote or line break, and double its quotes. Null becomes empty. */
    static String escape(String field) {
        if (field == null) {
            return "";
        }
        if (field.contains(",") || field.contains("\"") || field.contains("\n") || field.contains("\r")) {
            return "\"" + field.replace("\"", "\"\"") + "\"";
        }
        return field;
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }
}
